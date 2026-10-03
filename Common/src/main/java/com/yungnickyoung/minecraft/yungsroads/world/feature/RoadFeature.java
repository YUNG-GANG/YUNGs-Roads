package com.yungnickyoung.minecraft.yungsroads.world.feature;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.RoadBlockWriter;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

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

        IStructureRegionCacheProvider provider = (IStructureRegionCacheProvider) serverLevel;
        LiveRoadPlacer liveRoadPlacer = provider.getLiveRoadPlacer();
        ChunkPos chunkPos = new ChunkPos(context.origin());

        // Read the epoch before fetching any road data, so a settings change made meanwhile leaves the chunk stale
        int epoch = liveRoadPlacer == null ? 0 : liveRoadPlacer.getBlockLog().epoch();
        RoadBlockWriter writer = RoadBlockWriter.forWorldgen(context.level());
        placeRoadsInChunk(writer, context.random(), chunkPos, provider.getStructureRegionCache(), context.config());

        if (liveRoadPlacer != null) {
            liveRoadPlacer.getBlockLog().record(chunkPos.toLong(), epoch, writer);
        }
        return true;
    }

    /**
     * Places the roads and debug markers that reach the given chunk, as enabled in the config.
     * Only blocks inside the chunk are modified.
     */
    public static void placeRoadsInChunk(RoadBlockWriter writer, RandomSource random, ChunkPos chunkPos,
                                         StructureRegionCache structureRegionCache, RoadFeatureConfiguration config) {
        AbstractRoadGenerator roadGenerator = structureRegionCache.getStructureRegionGenerator().getRoadGenerator();

        if (YungsRoadsCommon.CONFIG.debug.placeRoads) {
            List<StructureRegion> regions = structureRegionCache.getRegionsNearChunk(chunkPos);
            roadGenerator.placeRoadInChunk(writer, random, chunkPos, structureRegionCache.getRoadPositionsNearChunk(chunkPos),
                    roadPos -> regions.stream().anyMatch(region -> region.isLandBridge(roadPos)), config);
        }

        // Debug markers aren't indexed by chunk, so check every road that could reach this chunk
        if (anyDebugMarkersEnabled(YungsRoadsCommon.CONFIG.debug)) {
            for (StructureRegion region : structureRegionCache.getRegionsNearChunk(chunkPos)) {
                for (Road road : region.getRoads()) {
                    roadGenerator.placeDebugMarkers(road, writer, chunkPos);
                }
            }
        }
    }

    private static boolean anyDebugMarkersEnabled(ConfigModule.Debug debug) {
        return debug.placeStraightDebugLine
                || debug.placeRoadEndpointDebugMarkers
                || debug.placeUnjitteredPosDebugMarkers
                || debug.placeJitteredPosDebugMarkers;
    }
}
