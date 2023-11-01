package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.ChunkGeneratorAccessor;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AStarRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.LinearRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.SplineRoadGenerator;
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
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
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
    HolderSet<ConfiguredStructureFeature<?, ?>> villageStructures;

    public StructureRegionGenerator(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        this.random = new WorldgenRandom(new LegacyRandomSource(0));
//        this.roadGenerator = new SplineRoadGenerator(serverLevel);
        this.roadGenerator = new AStarRoadGenerator(serverLevel);
//        this.roadGenerator = new LinearRoadGenerator(serverLevel);
        this.villageStructures = YungsRoadsCommon.CONFIG.general.structures;
    }

    /**
     * Generates a new {@link StructureRegion} for the given region key.
     * <p>
     * Uses the structure's spacing & separation settings to reconstruct its structure location grid,
     * then validates each position with a biome check. From there, some of the structure locations
     * are randomly selected as endpoints for roads, and the roads are constructed.
     */
    public StructureRegion generateRegion(long regionKey) {
        Set<Holder<Biome>> targetBiomes = this.villageStructures.stream().flatMap((holder) -> holder.value().biomes().stream()).collect(Collectors.toSet());

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
        List<Long> villageList = new ArrayList<>();
        ChunkPos minChunkPos = regionPos.getMinChunkPosInRegion();
        ChunkPos maxChunkPos = regionPos.getMaxChunkPosInRegion();

        // Create map of placements to matching configured features
        Map<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> placementToFeaturesMap = new Object2ObjectArrayMap<>();

        for (Holder<ConfiguredStructureFeature<?, ?>> holder : this.villageStructures) {
            if (allBiomesInDimension.stream().anyMatch(holder.value().biomes()::contains)) {
                List<StructurePlacement> placementsForStructure = ((ChunkGeneratorAccessor) this.serverLevel.getChunkSource().getGenerator()).callGetPlacementsForFeature(holder);
                for (StructurePlacement placement : placementsForStructure) {
                    placementToFeaturesMap.computeIfAbsent(placement, k -> new ObjectArraySet<>()).add(holder);
                }
            }
        }

        // Filter out any placements that aren't random spread.
        // TODO: support concentric rings + modded spreads?
        List<Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>>> structurePlacementEntries = new ArrayList<>(placementToFeaturesMap.size());

        for (Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> entry : placementToFeaturesMap.entrySet()) {
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
                for (Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> entry : structurePlacementEntries) {
                    RandomSpreadStructurePlacement randomPlacement = (RandomSpreadStructurePlacement) entry.getKey();
                    Set<Holder<ConfiguredStructureFeature<?, ?>>> holderSet = entry.getValue();

                    if (!randomPlacement.isFeatureChunk(serverLevel.getChunkSource().getGenerator(), serverLevel.getSeed(), chunkX, chunkZ)) {
                        continue;
                    }

                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

                    if (regionPos.isChunkInRegion(chunkPos)) {
                        Holder<Biome> biome = serverLevel.getChunkSource().getGenerator().getNoiseBiome(
                                QuartPos.fromSection(chunkPos.x),
                                QuartPos.fromBlock(serverLevel.getSeaLevel()),
                                QuartPos.fromSection(chunkPos.z));

                        // See if any of the configured features for this placement could generate in this chunk
                        if (targetBiomes.stream().anyMatch(biomeHolder -> biomeHolder.value() == biome.value())) {

                            for (Holder<ConfiguredStructureFeature<?, ?>> holder : holderSet) {
                                ConfiguredStructureFeature<?, ?> configuredStructureFeature = holder.value();
                                StructureStart structureStart = configuredStructureFeature.generate(serverLevel.registryAccess(), serverLevel.getChunkSource().getGenerator(), serverLevel.getChunkSource().getGenerator().getBiomeSource(), serverLevel.getStructureManager(), serverLevel.getSeed(), chunkPos, 0, serverLevel, (b) -> configuredStructureFeature.biomes().contains(b));
                                if (structureStart != StructureStart.INVALID_START && !villageList.contains(chunkPos.toLong())) {
                                    villageList.add(chunkPos.toLong());
                                }
                            }
                        }
                    }
                }
            }
        }

        List<Road> roads = new ArrayList<>();
        List<Long> villageListCopy = new ArrayList<>(villageList);
        random.setSeed(regionKey ^ serverLevel.getSeed());

        // TODO put these in config options
        int numRoads = villageList.size();
        int maxRoadLength = 800;
        int minRoadLength = 50;

        // Generate some roads connecting villages
        int i = 0;
        while (i < numRoads && villageListCopy.size() > 1) {
            // Choose first village
            int startIndex = random.nextInt(villageListCopy.size());
            ChunkPos startVillage = new ChunkPos(villageListCopy.get(startIndex));

            // Remove start pos from the list now that it's chosen.
            // We remove the start pos to prevent completely duplicate roads, but keep the end pos
            // to allow for villages with multiple roads
            villageListCopy.remove(startIndex);

            // Choose second village
            ChunkPos endVillage = null;
            for (Long endCandidate : villageListCopy) {
                ChunkPos endCandidateChunkPos = new ChunkPos(endCandidate);

                // End pos must be within 800 blocks of start pos (arbitrary max road length)
                if (startVillage.getWorldPosition().closerThan(endCandidateChunkPos.getWorldPosition(), maxRoadLength)
                        && !startVillage.getWorldPosition().closerThan(endCandidateChunkPos.getWorldPosition(), minRoadLength)
                ) {
                    endVillage = endCandidateChunkPos;
                    break;
                }
            }

            // If we found a second village, attempt to construct a Road connecting the two villages
            if (endVillage != null && !endVillage.equals(startVillage)) {
                Optional<Road> roadOptional = this.roadGenerator.generateRoad(startVillage, endVillage);
                if (roadOptional.isPresent()) {
                    roads.add(roadOptional.get());
                    i++;
                }
            }
        }

        // Remove any leftover village chunks that didn't get used
//        villageSet.removeIf(chunkLong -> {
//            BlockPos blockPos = new ChunkPos(chunkLong).getWorldPosition();
//            return roads.stream().noneMatch(road -> road.getVillageStart().equals(blockPos) || road.getVillageEnd().equals(blockPos));
//        });

        return new StructureRegion(regionKey, villageList, roads);
    }

    public AbstractRoadGenerator getRoadGenerator() {
        return this.roadGenerator;
    }

    public void setVillageStructures(HolderSet<ConfiguredStructureFeature<?, ?>> villageStructures) {
        this.villageStructures = villageStructures;
    }
}
