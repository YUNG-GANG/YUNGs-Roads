package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class SmallWoodBenchRoadDecoration extends ManualRoadDecoration {
    public SmallWoodBenchRoadDecoration(String name) {
        super(name);
    }

    @Override
    public boolean place(WorldGenLevel level, Random random, BlockPos blockPos, Vec3 normal, Vec3 tangent) {
        BlockPos.MutableBlockPos mutable = blockPos.mutable();

        Direction towardPath = Direction.fromNormal((int) Math.round(normal.reverse().x), 0, (int) Math.round(normal.reverse().z));
        if (towardPath == null) return false;

        Direction awayFromPath = towardPath.getOpposite();
        Direction right = towardPath.getCounterClockWise();
        Direction left = towardPath.getClockWise();

        // Make sure the ground is flat
        if (!level.getBlockState(mutable.below()).canOcclude() || !level.getBlockState(mutable.below().relative(left)).canOcclude()) {
            return false;
        }

        // Make sure there's air above the ground
        if (!level.getBlockState(mutable).isAir() || !level.getBlockState(mutable.relative(left)).isAir()) {
            return false;
        }

        // Place feature
        level.setBlock(mutable, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, awayFromPath), 2);
        mutable.move(right);
        level.setBlock(mutable, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, right),
                2);
        mutable.move(left, 2);
        level.setBlock(mutable, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, awayFromPath), 2);
        mutable.move(left);
        level.setBlock(mutable, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                        .setValue(TrapDoorBlock.OPEN, true)
                        .setValue(TrapDoorBlock.FACING, left),
                2);

        return true;
    }
}
