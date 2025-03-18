package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class BushRoadDecoration extends ManualRoadDecoration {
    private static final BushRoadDecoration INSTANCE = new BushRoadDecoration("bush");

    public static final MapCodec<BushRoadDecoration> CODEC = MapCodec.unit(() -> INSTANCE);

    public BushRoadDecoration(String name) {
        super(name);
    }

    @Override
    public boolean place(WorldGenLevel level, RandomSource random, BlockPos blockPos, Vec3 normal, Vec3 tangent) {
        BlockPos.MutableBlockPos mutable = blockPos.mutable();

//        Direction towardPath = Direction.fromNormal((int) Math.round(normal.reverse().x), 0, (int) Math.round(normal.reverse().z));
//        if (towardPath == null) return false;

        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        mutable.move((int) Math.round(tangent.x), 0, (int) Math.round(tangent.z));
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);
        mutable.move(Direction.UP);
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        mutable.move((int) Math.round(-normal.x), -1, (int) Math.round(-normal.z));
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        mutable.move((int) Math.round(tangent.x), 0, (int) Math.round(tangent.z));
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        mutable.move((int) Math.round(normal.x), 0, (int) Math.round(normal.z));
        level.setBlock(mutable, Blocks.OAK_LEAVES.defaultBlockState(), 2);

        return true;
    }
}
