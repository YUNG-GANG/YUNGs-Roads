package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.ChunkGeneratorAccessor;
import com.yungnickyoung.minecraft.yungsroads.world.road.IRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.SplineRoadGenerator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for generating new StructureRegions.
 * Does not store any regions generated - this is handled by {@link StructureRegionCache}
 */
public class StructureRegionGenerator {
    private final ServerLevel serverLevel;
    private final WorldgenRandom random;
    private final IRoadGenerator roadGenerator;
    HolderSet<ConfiguredStructureFeature<?, ?>> configuredStructureFeatures;

    public StructureRegionGenerator(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
        this.random = new WorldgenRandom(new LegacyRandomSource(0));
        this.roadGenerator = new SplineRoadGenerator(serverLevel);

        // Extract info into HolderSet of configured features
        List<Holder<ConfiguredStructureFeature<?,?>>> holders = new ArrayList<>();
        holders.addAll(serverLevel.registryAccess().registryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY).getTag(TagKey.create(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY, new ResourceLocation("village"))).get().stream().toList());
        this.configuredStructureFeatures = HolderSet.direct(holders);
    }

    /**
     * Generates a new {@link StructureRegion} for the given region key.
     * <p>
     * Uses the structure's separation settings to reconstruct its structure location grid,
     * then validates each position with a biome check. From there, some of the structure locations
     * are randomly selected as endpoints for roads, and the roads are constructed.
     */
    public StructureRegion generateRegion(long regionKey) {
        Set<Holder<Biome>> targetBiomes = this.configuredStructureFeatures.stream().flatMap((holder) -> holder.value().biomes().stream()).collect(Collectors.toSet());

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
        LongOpenHashSet villageSet = new LongOpenHashSet();
        ChunkPos minChunkPos = regionPos.getMinChunkPosInRegion();
        ChunkPos maxChunkPos = regionPos.getMaxChunkPosInRegion();

        // Create map of placements to matching configured features
        Map<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> placementToFeaturesMap = new Object2ObjectArrayMap<>();
        for (Holder<ConfiguredStructureFeature<?, ?>> holder : this.configuredStructureFeatures) {
            if (allBiomesInDimension.stream().anyMatch(holder.value().biomes()::contains)) {
                for (StructurePlacement structureplacement : ((ChunkGeneratorAccessor) this.serverLevel.getChunkSource().getGenerator()).callGetPlacementsForFeature(holder)) {
                    placementToFeaturesMap.computeIfAbsent(structureplacement, (p_211663_) -> new ObjectArraySet()).add(holder);
                }
            }
        }

        List<Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>>> list = new ArrayList<>(placementToFeaturesMap.size());

        for (Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> entry : placementToFeaturesMap.entrySet()) {
            StructurePlacement structureplacement1 = entry.getKey();
            if (structureplacement1 instanceof ConcentricRingsStructurePlacement) {
                // TODO
            } else if (structureplacement1 instanceof RandomSpreadStructurePlacement) {
                list.add(entry);
            }
        }

        // Locate target structures in this region
        for (int chunkX = minChunkPos.x; chunkX <= maxChunkPos.x; chunkX++) {
            for (int chunkZ = minChunkPos.z; chunkZ <= maxChunkPos.z; chunkZ++) {
                if (!list.isEmpty()) {
                    for (Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> entry1 : list) {
                        RandomSpreadStructurePlacement randomspreadstructureplacement = (RandomSpreadStructurePlacement) entry1.getKey();
                        ChunkPos chunkPos = randomspreadstructureplacement.getPotentialFeatureChunk(serverLevel.getSeed(), chunkX, chunkZ);

                        if (regionPos.isChunkInRegion(chunkPos)) {
                            Holder<Biome> biome = serverLevel.getChunkSource().getGenerator().getNoiseBiome(
                                    QuartPos.fromSection(chunkPos.x),
                                    QuartPos.fromBlock(serverLevel.getSeaLevel()),
                                    QuartPos.fromSection(chunkPos.z));

                            if (targetBiomes.stream().anyMatch(biomeHolder -> biomeHolder.value() == biome.value())) {
                                villageSet.add(chunkPos.toLong());
                            }
                        }
                    }
                }
            }
        }

        // Create set for storing the village chunks
        LongList villageChunks = new LongArrayList(villageSet);

        // Generate some roads connecting villages
        List<Road> roads = new ArrayList<>();
        // int numRoads = random.nextInt(villageChunks.size() / 2);
        int numRoads = villageChunks.size() / 2;
        int i = 0;
        while (i < numRoads && villageChunks.size() > 1) {
            // Choose first village
            int startIndex = random.nextInt(villageChunks.size());
            ChunkPos startVillage = new ChunkPos(villageChunks.getLong(startIndex));

            // Remove start pos from the list now that it's chosen.
            // We remove the start pos to prevent completely duplicate roads, but keep the end pos
            // to allow for villages with multiple roads
            villageChunks.removeLong(startIndex);

            // Choose second village
            ChunkPos endVillage = null;
            for (Long endCandidate : villageChunks) {
                ChunkPos endCandidateChunkPos = new ChunkPos(endCandidate);

                // End pos must be within 800 blocks of start pos (arbitrary max road length)
                if (startVillage.getWorldPosition().closerThan(endCandidateChunkPos.getWorldPosition(), 800)) {
                    endVillage = endCandidateChunkPos;
                    break;
                }
            }

            // If we found a second village, attempt to construct a Road connecting the two villages
            if (endVillage != null) {
                Optional<Road> roadOptional = this.roadGenerator.generateRoad(startVillage, endVillage);
                if (roadOptional.isPresent()) {
                    roads.add(roadOptional.get());
                    i++;
                }
            }
        }

        return new StructureRegion(regionKey, villageSet, roads);
    }

    public IRoadGenerator getRoadGenerator() {
        return this.roadGenerator;
    }

    public void setConfiguredStructureFeatures(HolderSet<ConfiguredStructureFeature<?, ?>> configuredStructureFeatures) {
        this.configuredStructureFeatures = configuredStructureFeatures;
    }
}
