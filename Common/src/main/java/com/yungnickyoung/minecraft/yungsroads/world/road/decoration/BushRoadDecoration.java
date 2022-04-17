package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;

public class BushRoadDecoration extends AbstractRoadDecoration {
    public BushRoadDecoration(float chance) {
        super("bush", chance);
    }

    @Override
    protected boolean place(WorldGenLevel level, Random random, BlockPos blockPos, @Nullable Vec3 normal, @Nullable Vec3 tangent) {
        BlockPos.MutableBlockPos mutable = blockPos.mutable();

        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);
        mutable.move((int) Math.round(tangent.x), 0, (int) Math.round(tangent.z));
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);
        mutable.move((int) Math.round(tangent.x), 0, (int) Math.round(tangent.z));
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        return true;
    }
}
