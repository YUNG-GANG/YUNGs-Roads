package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class RoadDecoration {
//    public static final Codec<RoadDecoration> CODEC = RecordCodecBuilder.create((instance) -> instance
//            .group(typeCodec())
//            .apply(instance, RoadDecoration::new));
//
//    public Codec<? extends RoadDecoration> codec() {
//        return switch (this.type) {
//            case MANUAL -> ManualRoadDecoration.CODEC;
//            case FEATURE -> FeatureRoadDecoration.CODEC;
//        };
//    }

//    public RoadDecoration(Codec<FC> p_65786_) {
//        this.configuredCodec = p_65786_.fieldOf("config").xmap((p_65806_) -> {
//            return new ConfiguredFeature<>(this, p_65806_);
//        }, ConfiguredFeature::config).codec();
//    }
//
//    public Codec<ConfiguredFeature<FC, Feature<FC>>> configuredCodec() {
//        return this.configuredCodec;
//    }

//    protected static <E extends RoadDecoration> RecordCodecBuilder<E, Type> typeCodec() {
//        return Type.CODEC.fieldOf("type").forGetter((decoration) -> decoration.type);
//    }
//    RoadDecorationType type;

//    RoadDecoration(RoadDecorationType type) {
//        this.type = type;
//    }

    public ConfiguredRoadDecoration configured() {
        return new ConfiguredRoadDecoration(this);
    }

//    Codec<? extends RoadDecoration> codec();

//    RoadDecorationType<?> getType();

    abstract boolean place(WorldGenLevel level, Random random, BlockPos blockPos, Vec3 normal, Vec3 tangent);
}
