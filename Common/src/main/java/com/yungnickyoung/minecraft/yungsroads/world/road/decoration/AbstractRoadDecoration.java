package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;

public abstract class AbstractRoadDecoration {
    private final float chance;
    private final String name;

    public AbstractRoadDecoration(String name, float chance) {
        this.name = name;
        this.chance = chance;
    }

    public boolean chancePlace(WorldGenLevel level, Random random, BlockPos blockPos, @Nullable Vec3 normal, @Nullable Vec3 tangent) {
        return random.nextFloat() < this.chance ? place(level, random, blockPos, normal, tangent) : false;
    }

    public float getChance() {
        return chance;
    }

    protected abstract boolean place(WorldGenLevel level, Random random, BlockPos blockPos, @Nullable Vec3 normal, @Nullable Vec3 tangent);

    @Override
    public String toString() {
        return "RoadDecoration[" + this.name + "]";
    }
}
