package com.yungnickyoung.minecraft.yungsroads.world.feature;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class RoadFeature extends Feature<RoadFeatureConfiguration> {
    public RoadFeature() {
        super(RoadFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<RoadFeatureConfiguration> context) {
        ServerLevel serverLevel;
        if (context.level() instanceof WorldGenRegion worldGenRegion) {
            serverLevel = worldGenRegion.getLevel();
        } else if (context.level() instanceof ServerLevel serverLevel1) {
            serverLevel = serverLevel1;
        } else {
            YungsRoadsCommon.LOGGER.error("Unable to cast worldGenLevel to {}", context.level().getClass().toString());
            return false;
        }

        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) serverLevel).getStructureRegionCache();
        AbstractRoadGenerator roadGenerator = structureRegionCache.getStructureRegionGenerator().getRoadGenerator();
        ChunkPos chunkPos = new ChunkPos(context.origin());

        // Place roads
        roadGenerator.placeRoadInChunk(context.level(), context.random(), chunkPos,
                structureRegionCache.getRoadPositionsNearChunk(chunkPos), context.config());

        // Debug markers aren't indexed by chunk, so check every road that could reach this chunk
        if (anyDebugMarkersEnabled(YungsRoadsCommon.CONFIG.debug)) {
            for (StructureRegion region : structureRegionCache.getRegionsNearChunk(chunkPos)) {
                for (Road road : region.getRoads()) {
                    roadGenerator.placeDebugMarkers(road, context.level(), chunkPos);
                }
            }
        }

        return true;
    }

    private static boolean anyDebugMarkersEnabled(ConfigModule.Debug debug) {
        return debug.placeStraightDebugLine
                || debug.placeRoadEndpointDebugMarkers
                || debug.placeUnjitteredPosDebugMarkers
                || debug.placeJitteredPosDebugMarkers;
    }
}
