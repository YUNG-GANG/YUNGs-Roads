package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class LinearRoadGenerator implements IRoadGenerator {
    private final StructureRegionCache structureRegionCache;

    public LinearRoadGenerator(StructureRegionCache structureRegionCache) {
        this.structureRegionCache = structureRegionCache;
    }

    @Override
    public Road generate(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.asBlockPos() : pos2.asBlockPos();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.asBlockPos() : pos1.asBlockPos();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        return new Road(blockPos1, blockPos2)
            .addRoadSegment(
                blockPos1,
                blockPos1.add(xDist / 4, 0, zDist / 4))
            .addRoadSegment(
                blockPos1.add(xDist / 4, 0, zDist / 4),
                blockPos1.add(2 * xDist / 4, 0, 2 * zDist / 4))
            .addRoadSegment(
                blockPos1.add(2 * xDist / 4, 0, 2 * zDist / 4),
                blockPos1.add(3 * xDist / 4, 0, 3 * zDist / 4))
            .addRoadSegment(
                blockPos1.add(3 * xDist / 4, 0, 3 * zDist / 4),
                blockPos2);
    }
}
