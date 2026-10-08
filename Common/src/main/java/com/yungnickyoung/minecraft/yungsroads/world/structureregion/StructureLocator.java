package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.util.GridKeys;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainSampler;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Locates the structures roads may connect.
 * <p>
 * Results are cached per cell of {@code 2^CELL_SIZE_SHIFT} x {@code 2^CELL_SIZE_SHIFT} chunks, so overlapping requests
 * from neighboring regions only evaluate each potential structure start once. Thread-safe.
 */
public class StructureLocator {
    private static final int CELL_SIZE_SHIFT = 5; // 32 x 32 chunks

    private final ServerLevel serverLevel;
    private final TerrainSampler terrainSampler;
    private final ConcurrentHashMap<Long, CompletableFuture<List<ChunkPos>>> cells = new ConcurrentHashMap<>();

    private final HolderSet<Structure> endpointStructures;

    public StructureLocator(ServerLevel serverLevel, TerrainSampler terrainSampler, HolderSet<Structure> endpointStructures) {
        this.serverLevel = serverLevel;
        this.terrainSampler = terrainSampler;
        this.endpointStructures = endpointStructures;
    }

    /**
     * Returns the chunk positions of all endpoint structures between the given chunk positions, inclusive.
     * The order is deterministic.
     */
    public List<ChunkPos> locate(ChunkPos minChunkPos, ChunkPos maxChunkPos) {
        List<ChunkPos> structures = new ArrayList<>();
        for (int cellX = minChunkPos.x >> CELL_SIZE_SHIFT; cellX <= maxChunkPos.x >> CELL_SIZE_SHIFT; cellX++) {
            for (int cellZ = minChunkPos.z >> CELL_SIZE_SHIFT; cellZ <= maxChunkPos.z >> CELL_SIZE_SHIFT; cellZ++) {
                for (ChunkPos structurePos : getCell(cellX, cellZ)) {
                    if (structurePos.x >= minChunkPos.x && structurePos.x <= maxChunkPos.x
                            && structurePos.z >= minChunkPos.z && structurePos.z <= maxChunkPos.z) {
                        structures.add(structurePos);
                    }
                }
            }
        }
        return structures;
    }

    private List<ChunkPos> getCell(int cellX, int cellZ) {
        long key = GridKeys.pack(cellX, cellZ);
        CompletableFuture<List<ChunkPos>> future = new CompletableFuture<>();
        CompletableFuture<List<ChunkPos>> existing = this.cells.putIfAbsent(key, future);
        if (existing != null) {
            return existing.join();
        }

        // This thread claimed the cell, so it computes it. Other threads requesting it wait on the future.
        try {
            List<ChunkPos> structures = locateInRange(
                    new ChunkPos(cellX << CELL_SIZE_SHIFT, cellZ << CELL_SIZE_SHIFT),
                    new ChunkPos(((cellX + 1) << CELL_SIZE_SHIFT) - 1, ((cellZ + 1) << CELL_SIZE_SHIFT) - 1));
            future.complete(structures);
            return structures;
        } catch (Throwable t) {
            this.cells.remove(key, future);
            future.completeExceptionally(t);
            throw t;
        }
    }

    /**
     * Reconstructs the location grid of each structure set holding an endpoint structure, then checks which of the
     * set's structures generates at each candidate, the same way the chunk generator picks one.
     */
    private List<ChunkPos> locateInRange(ChunkPos minChunkPos, ChunkPos maxChunkPos) {
        HolderSet<Structure> structures = this.endpointStructures;
        Set<Holder<Biome>> targetBiomes = structures.stream()
                .flatMap(holder -> holder.value().biomes().stream())
                .collect(Collectors.toSet());

        // Quit if no biomes in this dimension match the target biomes
        Set<Holder<Biome>> allBiomesInDimension = this.serverLevel.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        if (targetBiomes.isEmpty() || Collections.disjoint(allBiomesInDimension, targetBiomes)) {
            return List.of();
        }

        ChunkGeneratorStructureState generatorState = this.serverLevel.getChunkSource().getGeneratorState();
        List<StructureSet> structureSets = generatorState.possibleStructureSets().stream()
                .map(Holder::value)
                .filter(set -> set.structures().stream().anyMatch(entry -> structures.contains(entry.structure())
                        && allBiomesInDimension.stream().anyMatch(entry.structure().value().biomes()::contains)))
                .toList();

        List<ChunkPos> structureChunkPositions = new ArrayList<>();
        for (int chunkX = minChunkPos.x; chunkX <= maxChunkPos.x; chunkX++) {
            for (int chunkZ = minChunkPos.z; chunkZ <= maxChunkPos.z; chunkZ++) {
                for (StructureSet set : structureSets) {
                    // TODO: support concentric rings + modded spreads?
                    if (!(set.placement() instanceof RandomSpreadStructurePlacement structurePlacement)
                            || !structurePlacement.isStructureChunk(generatorState, chunkX, chunkZ)) {
                        continue;
                    }

                    ChunkPos structureChunkPos = new ChunkPos(chunkX, chunkZ);
                    Biome biome = this.terrainSampler.biomeAt(structureChunkPos.getMinBlockX(), structureChunkPos.getMinBlockZ()).value();
                    if (targetBiomes.stream().noneMatch(targetBiome -> targetBiome.value() == biome)) {
                        continue;
                    }

                    Holder<Structure> generated = generatedStructure(set, structureChunkPos, generatorState.getLevelSeed());
                    if (generated != null && structures.contains(generated) && !structureChunkPositions.contains(structureChunkPos)) {
                        structureChunkPositions.add(structureChunkPos);
                    }
                }
            }
        }
        return structureChunkPositions;
    }

    /**
     * Which of a structure set's structures generates at a chunk its placement selects, if any. Like
     * {@link net.minecraft.world.level.chunk.ChunkGenerator#createStructures}, picks the set's structures in a weighted
     * random order seeded by the chunk, until one can generate there.
     */
    @Nullable
    private Holder<Structure> generatedStructure(StructureSet set, ChunkPos chunkPos, long levelSeed) {
        List<StructureSet.StructureSelectionEntry> entries = new ArrayList<>(set.structures());
        if (entries.size() == 1) {
            return canGenerate(entries.get(0).structure().value(), chunkPos) ? entries.get(0).structure() : null;
        }

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(levelSeed, chunkPos.x, chunkPos.z);
        int totalWeight = entries.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while (!entries.isEmpty()) {
            int roll = random.nextInt(totalWeight);
            int index = 0;
            for (StructureSet.StructureSelectionEntry entry : entries) {
                roll -= entry.weight();
                if (roll < 0) {
                    break;
                }
                index++;
            }

            StructureSet.StructureSelectionEntry entry = entries.get(index);
            if (canGenerate(entry.structure().value(), chunkPos)) {
                return entry.structure();
            }
            entries.remove(index);
            totalWeight -= entry.weight();
        }
        return null;
    }

    /**
     * Whether a structure can generate at a chunk. Unlike Structure#generate, this doesn't assemble the structure's
     * pieces, which is very expensive for jigsaw structures like villages.
     */
    private boolean canGenerate(Structure structure, ChunkPos chunkPos) {
        Structure.GenerationContext context = new Structure.GenerationContext(
                this.serverLevel.registryAccess(),
                this.serverLevel.getChunkSource().getGenerator(),
                this.serverLevel.getChunkSource().getGenerator().getBiomeSource(),
                this.serverLevel.getChunkSource().randomState(),
                this.serverLevel.getStructureManager(),
                this.serverLevel.getSeed(),
                chunkPos,
                this.serverLevel,
                structure.biomes()::contains);
        return structure.findValidGenerationPoint(context).isPresent();
    }
}
