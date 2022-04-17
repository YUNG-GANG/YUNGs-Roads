package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;

public class FeatureRoadDecoration extends AbstractRoadDecoration {
    private final Holder<PlacedFeature> placedFeatureHolder;

    public FeatureRoadDecoration(float chance, Holder<PlacedFeature> placedFeatureHolder) {
        super(placedFeatureHolder.value().toString(), chance);
        this.placedFeatureHolder = placedFeatureHolder;
    }

    @Override
    public boolean place(WorldGenLevel level, Random random, BlockPos blockPos, @Nullable Vec3 normal, @Nullable Vec3 tangent) {
        return this.placedFeatureHolder.value().place(level, level.getLevel().getChunkSource().getGenerator(), random, blockPos);
    }
}
