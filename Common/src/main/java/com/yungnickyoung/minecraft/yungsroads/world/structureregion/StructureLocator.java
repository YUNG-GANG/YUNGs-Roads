package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainSampler;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Nullable
    private volatile HolderSet<Structure> endpointStructures;

    public StructureLocator(ServerLevel serverLevel, TerrainSampler terrainSampler, @Nullable HolderSet<Structure> endpointStructures) {
        this.serverLevel = serverLevel;
        this.terrainSampler = terrainSampler;
        this.endpointStructures = endpointStructures;
    }

    public void setEndpointStructures(HolderSet<Structure> endpointStructures) {
        this.endpointStructures = endpointStructures;
        this.cells.clear();
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
        long key = ChunkPos.asLong(cellX, cellZ);
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
     * Uses each structure's placement settings to reconstruct its structure location grid, then validates each
     * candidate with a biome check and by creating its structure start.
     */
    private List<ChunkPos> locateInRange(ChunkPos minChunkPos, ChunkPos maxChunkPos) {
        HolderSet<Structure> structures = this.endpointStructures;
        if (structures == null) {
            return List.of();
        }

        Set<Holder<Biome>> targetBiomes = structures.stream()
                .flatMap(holder -> holder.value().biomes().stream())
                .collect(Collectors.toSet());

        // Quit if no biomes in this dimension match the target biomes
        Set<Holder<Biome>> allBiomesInDimension = this.serverLevel.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        if (targetBiomes.isEmpty() || Collections.disjoint(allBiomesInDimension, targetBiomes)) {
            return List.of();
        }

        // Create map of placements to matching structures
        ChunkGeneratorStructureState generatorState = this.serverLevel.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> placementToStructuresMap = new Object2ObjectArrayMap<>();
        for (Holder<Structure> holder : structures) {
            if (allBiomesInDimension.stream().anyMatch(holder.value().biomes()::contains)) {
                for (StructurePlacement placement : generatorState.getPlacementsForStructure(holder)) {
                    placementToStructuresMap.computeIfAbsent(placement, k -> new ObjectArraySet<>()).add(holder);
                }
            }
        }

        List<ChunkPos> structureChunkPositions = new ArrayList<>();
        for (int chunkX = minChunkPos.x; chunkX <= maxChunkPos.x; chunkX++) {
            for (int chunkZ = minChunkPos.z; chunkZ <= maxChunkPos.z; chunkZ++) {
                for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : placementToStructuresMap.entrySet()) {
                    // TODO: support concentric rings + modded spreads?
                    if (!(entry.getKey() instanceof RandomSpreadStructurePlacement structurePlacement)
                            || !structurePlacement.isStructureChunk(generatorState, chunkX, chunkZ)) {
                        continue;
                    }

                    ChunkPos structureChunkPos = new ChunkPos(chunkX, chunkZ);
                    Biome biome = this.terrainSampler.biomeAt(structureChunkPos.getMinBlockX(), structureChunkPos.getMinBlockZ()).value();
                    if (targetBiomes.stream().noneMatch(targetBiome -> targetBiome.value() == biome)) {
                        continue;
                    }

                    for (Holder<Structure> holder : entry.getValue()) {
                        // Confirm the structure can generate here. Unlike Structure#generate, this doesn't assemble
                        // the structure's pieces, which is very expensive for jigsaw structures like villages.
                        Structure structure = holder.value();
                        Structure.GenerationContext context = new Structure.GenerationContext(
                                this.serverLevel.registryAccess(),
                                this.serverLevel.getChunkSource().getGenerator(),
                                this.serverLevel.getChunkSource().getGenerator().getBiomeSource(),
                                this.serverLevel.getChunkSource().randomState(),
                                this.serverLevel.getStructureManager(),
                                this.serverLevel.getSeed(),
                                structureChunkPos,
                                this.serverLevel,
                                structure.biomes()::contains);

                        if (structure.findValidGenerationPoint(context).isPresent() && !structureChunkPositions.contains(structureChunkPos)) {
                            structureChunkPositions.add(structureChunkPos);
                        }
                    }
                }
            }
        }
        return structureChunkPositions;
    }
}
