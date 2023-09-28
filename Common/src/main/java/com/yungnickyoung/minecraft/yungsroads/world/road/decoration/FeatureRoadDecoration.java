package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class FeatureRoadDecoration extends RoadDecoration {
    public static final Codec<FeatureRoadDecoration> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    BuiltinRegistries.PLACED_FEATURE.holderByNameCodec().fieldOf("placedFeatureHolder").forGetter(FeatureRoadDecoration::getPlacedFeatureHolder))
            .apply(instance, FeatureRoadDecoration::new));

    private final Holder<PlacedFeature> placedFeatureHolder;

    public FeatureRoadDecoration(Holder<PlacedFeature> placedFeatureHolder) {
//        super(Type.FEATURE);
        this.placedFeatureHolder = placedFeatureHolder;
    }

    @Override
    public boolean place(WorldGenLevel level, Random random, BlockPos blockPos, Vec3 normal, Vec3 tangent) {
        return this.placedFeatureHolder.value().place(level, level.getLevel().getChunkSource().getGenerator(), random, blockPos);
    }

    public Holder<PlacedFeature> getPlacedFeatureHolder() {
        return placedFeatureHolder;
    }

//    @Override
//    public Codec<? extends RoadDecoration> codec() {
//        return CODEC;
//    }
    @Override
    public String toString() {
        return "FeatureRoadDecoration[" + placedFeatureHolder.value() + "]";
    }
}
