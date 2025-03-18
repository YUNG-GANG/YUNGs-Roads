package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AStarRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class for generating new StructureRegions.
 * Does not store any generated regions - that is handled by {@link StructureRegionCache}
 */
public class StructureRegionGenerator {
    private final ServerLevel serverLevel;
    private final WorldgenRandom random;
    private final AbstractRoadGenerator roadGenerator;
    HolderSet<Structure> endpointStructures;

    public StructureRegionGenerator(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        this.random = new WorldgenRandom(new LegacyRandomSource(0));
        this.roadGenerator = new AStarRoadGenerator(serverLevel);
//        this.roadGenerator = new SplineRoadGenerator(serverLevel);
//        this.roadGenerator = new LinearRoadGenerator(serverLevel);
        this.endpointStructures = YungsRoadsCommon.CONFIG.general.structures;
    }

    /**
     * Generates a new {@link StructureRegion} for the given region key.
     * <p>
     * Uses the structure's spacing & separation settings to reconstruct its structure location grid,
     * then validates each position with a biome check. From there, some of the structure locations
     * are randomly selected as endpoints for roads, and the roads are constructed.
     */
    public StructureRegion generateRegion(long regionKey) {
        Set<Holder<Biome>> targetBiomes = this.endpointStructures.stream()
                .flatMap(holder -> holder.value().biomes().stream())
                .collect(Collectors.toSet());

        // Quit if there are no target biomes
        if (targetBiomes.isEmpty()) {
            return new StructureRegion(regionKey);
        }

        // Quit if no biomes in this dimension match the target biomes
        Set<Holder<Biome>> allBiomesInDimension = this.serverLevel.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        if (Collections.disjoint(allBiomesInDimension, targetBiomes)) {
            return new StructureRegion(regionKey);
        }

        StructureRegionPos regionPos = new StructureRegionPos(regionKey);
        List<Long> structureChunkPosList = new ArrayList<>();
        ChunkPos minChunkPos = regionPos.getMinChunkPosInRegion();
        ChunkPos maxChunkPos = regionPos.getMaxChunkPosInRegion();

        // Create map of placements to matching structures
        Map<StructurePlacement, Set<Holder<Structure>>> placementToStructuresMap = new Object2ObjectArrayMap<>();
        for (Holder<Structure> holder : this.endpointStructures) {
            if (allBiomesInDimension.stream().anyMatch(holder.value().biomes()::contains)) {
                List<StructurePlacement> placementsForStructure = this.serverLevel.getChunkSource().getGeneratorState().getPlacementsForStructure(holder);
                for (StructurePlacement placement : placementsForStructure) {
                    placementToStructuresMap.computeIfAbsent(placement, k -> new ObjectArraySet<>()).add(holder);
                }
            }
        }

        // Filter out any placements that aren't random spread.
        // TODO: support concentric rings + modded spreads?
        List<Map.Entry<StructurePlacement, Set<Holder<Structure>>>> structurePlacementEntries = new ArrayList<>(placementToStructuresMap.size());

        for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : placementToStructuresMap.entrySet()) {
            StructurePlacement structureplacement = entry.getKey();
            if (structureplacement instanceof ConcentricRingsStructurePlacement) {
                // TODO
            } else if (structureplacement instanceof RandomSpreadStructurePlacement) {
                structurePlacementEntries.add(entry);
            }
        }

        // Locate target structures in this region
        for (int chunkX = minChunkPos.x; chunkX <= maxChunkPos.x; chunkX++) {
            for (int chunkZ = minChunkPos.z; chunkZ <= maxChunkPos.z; chunkZ++) {
                for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : structurePlacementEntries) {
                    RandomSpreadStructurePlacement structurePlacement = (RandomSpreadStructurePlacement) entry.getKey();
                    Set<Holder<Structure>> holderSet = entry.getValue();

                    if (!structurePlacement.isStructureChunk(serverLevel.getChunkSource().getGeneratorState(), chunkX, chunkZ)) {
                        continue;
                    }

                    ChunkPos structureChunkPos = new ChunkPos(chunkX, chunkZ);

                    if (regionPos.isChunkInRegion(structureChunkPos)) {
                        Holder<Biome> biome = serverLevel.getNoiseBiome(
                                QuartPos.fromSection(structureChunkPos.x),
                                QuartPos.fromBlock(serverLevel.getSeaLevel()),
                                QuartPos.fromSection(structureChunkPos.z));

                        // See if any of the structures for this placement could generate in this chunk
                        if (targetBiomes.stream().anyMatch(biomeHolder -> biomeHolder.value() == biome.value())) {
                            for (Holder<Structure> holder : holderSet) {
                                Structure structure = holder.value();

                                // "Generate" the structure to get its StructureStart.
                                // Note that this doesn't actually generate the structure in the world, it just creates the StructureStart object.
                                StructureStart structureStart = structure.generate(
                                        serverLevel.registryAccess(),
                                        serverLevel.getChunkSource().getGenerator(),
                                        serverLevel.getChunkSource().getGenerator().getBiomeSource(),
                                        serverLevel.getChunkSource().randomState(),
                                        serverLevel.getStructureManager(),
                                        serverLevel.getSeed(),
                                        structureChunkPos,
                                        0, //number of references
                                        serverLevel,
                                        structure.biomes()::contains);

                                if (structureStart != StructureStart.INVALID_START && !structureChunkPosList.contains(structureChunkPos.toLong())) {
                                    structureChunkPosList.add(structureChunkPos.toLong());
                                }
                            }
                        }
                    }
                }
            }
        }

        List<Road> roads = new ArrayList<>();
        List<Long> structureChunkPosListCopy = new ArrayList<>(structureChunkPosList);
        random.setSeed(regionKey ^ serverLevel.getSeed());

        // TODO put these in config options
        int maxNumRoads = structureChunkPosList.size();
        int maxRoadLength = 800;
        int minRoadLength = 50;

        // Generate some roads connecting structures
        int numRoadsGenerated = 0;
        while (numRoadsGenerated < maxNumRoads && structureChunkPosListCopy.size() > 1) {
            // Choose first structure endpoint
            int startIndex = random.nextInt(structureChunkPosListCopy.size());
            ChunkPos startStructurePos = new ChunkPos(structureChunkPosListCopy.get(startIndex));

            // Remove start pos from the list now that it's chosen.
            // We remove the start pos to prevent completely duplicate roads, but keep the end pos
            // to allow for structures with multiple roads
            structureChunkPosListCopy.remove(startIndex);

            // Choose second structure endpoint
            ChunkPos endStructurePos = null;
            for (Long endCandidate : structureChunkPosListCopy) {
                ChunkPos endCandidateChunkPos = new ChunkPos(endCandidate);

                // End pos must be within 800 blocks of start pos (arbitrary max road length)
                if (startStructurePos.getWorldPosition().closerThan(endCandidateChunkPos.getWorldPosition(), maxRoadLength)
                        && !startStructurePos.getWorldPosition().closerThan(endCandidateChunkPos.getWorldPosition(), minRoadLength)
                ) {
                    endStructurePos = endCandidateChunkPos;
                    break;
                }
            }

            // If we found a second structure, attempt to construct a Road connecting the two structures
            if (endStructurePos != null && !endStructurePos.equals(startStructurePos)) {
                Optional<Road> roadOptional = this.roadGenerator.generateRoad(startStructurePos, endStructurePos);
                if (roadOptional.isPresent()) {
                    roads.add(roadOptional.get());
                    numRoadsGenerated++;
                }
            }
        }

        // Remove any leftover village chunks that didn't get used
//        villageSet.removeIf(chunkLong -> {
//            BlockPos blockPos = new ChunkPos(chunkLong).getWorldPosition();
//            return roads.stream().noneMatch(road -> road.getVillageStart().equals(blockPos) || road.getVillageEnd().equals(blockPos));
//        });

        return new StructureRegion(regionKey, structureChunkPosList, roads);
    }

    public AbstractRoadGenerator getRoadGenerator() {
        return this.roadGenerator;
    }

    public void setEndpointStructures(HolderSet<Structure> endpointStructures) {
        this.endpointStructures = endpointStructures;
    }
}
