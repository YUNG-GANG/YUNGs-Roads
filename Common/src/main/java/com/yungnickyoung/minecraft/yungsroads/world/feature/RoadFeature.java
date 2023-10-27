package com.yungnickyoung.minecraft.yungsroads.world.feature;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
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

        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) serverLevel).getStructureRegionCache();
        StructureRegionPos structureRegionPos = new StructureRegionPos(context.origin());
        BlockPos nearestVillage = YungsRoadsCommon.DEBUG_MODE ? structureRegionCache.getNearestVillage(context.origin()) : null;

        // Place roads
        AbstractRoadGenerator roadGenerator = structureRegionCache.getStructureRegionGenerator().getRoadGenerator();
        List<Road> roads = structureRegionCache.getRegion(structureRegionPos).getRoads();
        roads.forEach(road -> roadGenerator.placeRoad(road, context.level(), context.random(), context.origin(), context.config(), nearestVillage));

        return true;
    }
}
