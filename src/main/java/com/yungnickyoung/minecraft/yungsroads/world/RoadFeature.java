package com.yungnickyoung.minecraft.yungsroads.world;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.block.Blocks;
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
    public boolean generate(ISeedReader world, ChunkGenerator chunkGenerator, Random rand, BlockPos pos, NoFeatureConfig config) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider)world.getWorld()).getStructureRegionCache();
        List<BlockPos> closestVillages = structureRegionCache.getNearestVillages(pos);

        BlockPos village1 = closestVillages.get(0).getX() <= closestVillages.get(1).getX() ? closestVillages.get(0) : closestVillages.get(1);
        BlockPos village2 = closestVillages.get(0).getX() <= closestVillages.get(1).getX() ? closestVillages.get(1) : closestVillages.get(0);

        int villageXDist = village2.getX() - village1.getX();
        int villageZDist = village2.getZ() - village1.getZ();
        double villageSlope = villageZDist / (double) villageXDist;
        // z = (villageSlope * (x - x1)) + z1

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                mutable.setPos(startX + localX, 110, startZ + localZ);

                // Only draw path between the points
                if (mutable.getX() < village1.getX() || mutable.getX() > village2.getX()) {
                    continue;
                }

                int globalX = localX + startX;
                int globalZ = localZ + startZ;
                int targetGlobalZ = (int) (villageSlope * (globalX - village1.getX())) + village1.getZ();

                // Calculate X/Z slope at this current point
//                double currPointSlope = (mutable.getZ() - village1.getZ()) / ((double) (mutable.getX() - village1.getX()));

                // Draw path if slope at current point is very close to slope between village centers
//                if (currPointSlope < villageSlope + .02 && currPointSlope > villageSlope - .02) {
                if (globalZ == targetGlobalZ) {
                    for (int testX = -2; testX <= 2; testX++) {
                        for (int testZ = -2; testZ <= 2; testZ++) {
                            if (testX * testX + testZ * testZ <= 4) {
                                mutable.setPos(startX + localX + testX, 110, startZ + localZ + testZ);
                                world.setBlockState(mutable, Blocks.GRASS_PATH.getDefaultState(), 2);
                                double distToVillage1 = village1.distanceSq(mutable);
                                double distToVillage2 = village2.distanceSq(mutable);
                                DebugRenderer.getInstance().addPath(new ChunkPos(mutable), new ChunkPos(distToVillage1 <= distToVillage2 ? village1 : village2));
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
