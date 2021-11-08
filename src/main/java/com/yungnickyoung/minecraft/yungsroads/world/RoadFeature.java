package com.yungnickyoung.minecraft.yungsroads.world;

import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.util.math.BlockPos;
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
    public boolean generate(ISeedReader world, ChunkGenerator chunkGenerator, Random rand, BlockPos pos, NoFeatureConfig config) {
        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) world.getWorld()).getStructureRegionCache();
        BlockPos nearestVillage = structureRegionCache.getNearestVillage(pos);
        List<Road> roads = structureRegionCache.getRoadsForPosition(pos);
        roads.forEach(road -> road.place(world, rand, pos, nearestVillage));

        return true;
    }
}
