package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;

public class StoneLampPostRoadDecoration extends ManualRoadDecoration {
    public StoneLampPostRoadDecoration(String name) {
        super(name);
    }

    @Override
    public boolean place(WorldGenLevel level, Random random, BlockPos blockPos, @Nullable Vec3 normal, @Nullable Vec3 tangent) {
        BlockPos.MutableBlockPos mutable = blockPos.mutable();
        
        level.setBlock(mutable, Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
        mutable.move(Direction.UP);
        level.setBlock(mutable, Blocks.LANTERN.defaultBlockState(), 2);

        return true;
    }
}
