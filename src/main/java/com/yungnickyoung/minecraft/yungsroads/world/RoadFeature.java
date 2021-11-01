package com.yungnickyoung.minecraft.yungsroads.world;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.debug.PathImageCommand;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Random;
import java.util.Set;

@ParametersAreNonnullByDefault
public class RoadFeature extends Feature<NoFeatureConfig> {
    public RoadFeature() {
        super(NoFeatureConfig.field_236558_a_);
    }

    @Override
    public boolean generate(ISeedReader world, ChunkGenerator chunkGenerator, Random rand, BlockPos pos, NoFeatureConfig config) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider)world.getWorld()).getStructureRegionCache();
        List<BlockPos> closestVillages = structureRegionCache.getNearestVillages(pos);

        YungsRoads.LOGGER.info(closestVillages);

        BlockPos village1 =  closestVillages.get(0).getX() <= closestVillages.get(1).getX() ? closestVillages.get(0) : closestVillages.get(1);
        BlockPos village2 =  closestVillages.get(0).getX() <= closestVillages.get(1).getX() ? closestVillages.get(1) : closestVillages.get(0);

        int villageXDist = village2.getX() - village1.getX();
        int villageZDist = village2.getZ() - village1.getZ();
        double villageSlope = villageZDist / (double) villageXDist;

        int pointXDist = pos.getX() - village1.getX();
        int pointZDist = pos.getZ() - village1.getZ();
        double pointSlope = pointZDist / (double) pointXDist;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                mutable.setPos(startX + localX, 110, startZ + localZ);
                double currPointSlope = (mutable.getZ() - village1.getZ()) / ((double) (mutable.getX() - village1.getX()));
                if (currPointSlope < villageSlope + .01 && currPointSlope > villageSlope - .01) {
                    for (int testX = -2; testX <= 2; testX++) {
                        for (int testZ = -2; testZ <= 2; testZ++) {
                            if (testX * testX + testZ * testZ <= 4) {
                                mutable.setPos(startX + localX + testX, 110, startZ + localZ + testZ);
                                world.setBlockState(mutable, Blocks.GRASS_PATH.getDefaultState(), 2);
                            }
                        }
                    }
                }
            }
        }
//
//        if (pointSlope < villageSlope + .1 && pointSlope > villageSlope - .1) {
//            for (int x = 0; x < 16; x++) {
//                int z = (int) (x * pointSlope);
//                if (z >= 0 && z < 16) {
//                    // Offset z if slope is negative
//                    if (pointSlope < 0) z = 15 - z;
//
//                    // Place square of path
//                    for (int xOffset = -2; xOffset <= 2; xOffset++) {
//                        for (int zOffset = -2; zOffset <= 2; zOffset++) {
//                            mutable.setPos(startX + x + xOffset, 110, startZ + z + zOffset);
////                            mutable.setY(world.getHeight(Heightmap.Type.MOTION_BLOCKING, mutable).getY());
//                            world.setBlockState(mutable, Blocks.GRASS_PATH.getDefaultState(), 2);
//                        }
//                    }
//                }
//            }
//        }


//        BlockPos.Mutable mutable = new BlockPos.Mutable();
//        for (BlockPos villagePos : closestVillages) {
//            mutable.setPos(villagePos);
//            for (int y = 60; y < 100; y++) {
//                mutable.setY(y);
//                world.setBlockState(mutable, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
//            }
//        }

//        for (Long point : closestVillages) {
//            PathImageCommand.getInstance().addPoint(new ChunkPos(point).asBlockPos());
//        }

//        YungsRoads.LOGGER.info(closestVillages);

        return true;
    }
}
