package com.yungnickyoung.minecraft.yungsroads.world;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.world.road.IRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Random;

@ParametersAreNonnullByDefault
public class RoadFeature extends Feature<NoFeatureConfig> {
    public RoadFeature() {
        super(NoFeatureConfig.field_236558_a_);
    }

    @Override
    public boolean generate(ISeedReader world, ChunkGenerator chunkGenerator, Random rand, BlockPos blockPos, NoFeatureConfig config) {
        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) world.getWorld()).getStructureRegionCache();
        BlockPos nearestVillage = YungsRoads.DEBUG_MODE ? structureRegionCache.getNearestVillage(blockPos) : null;

        // Place roads
        IRoadGenerator roadGenerator = structureRegionCache.getStructureRegionGenerator().getRoadGenerator();
        List<Road> roads = structureRegionCache.getRegion(new StructureRegionPos(blockPos)).getRoads();
        roads.forEach(road -> roadGenerator.placeRoad(road, world, rand, blockPos, nearestVillage));

        return true;
    }
}
