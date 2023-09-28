package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class ConfiguredRoadDecoration {
//    public static final Codec<ConfiguredRoadDecoration> CODEC = RecordCodecBuilder.create((instance) -> instance
//            .group(
//                    RoadDecorations.SINGLE_CODEC.fieldOf("decoration").forGetter(configuredDecoration -> configuredDecoration.decoration),
//                    Codec.FLOAT.fieldOf("chance").forGetter(configuredDecoration -> configuredDecoration.chance),
//                    Codec.INT.fieldOf("normalOffset").forGetter(configuredDecoration -> configuredDecoration.normalOffset))
//            .apply(instance, ConfiguredRoadDecoration::new));
    protected static <E extends ConfiguredRoadDecoration> RecordCodecBuilder<E, Float> chanceCodec() {
        return Codec.FLOAT.fieldOf("chance").forGetter(configuredDecoration -> configuredDecoration.chance);
    }

    protected static <E extends ConfiguredRoadDecoration> RecordCodecBuilder<E, Integer> normalOffsetCodec() {
        return Codec.INT.fieldOf("normalOffset").forGetter(configuredDecoration -> configuredDecoration.normalOffset);
    }

    protected RoadDecoration decoration;
    protected float chance = 0;
    protected int normalOffset = 0;

    public ConfiguredRoadDecoration withChance(float chance) {
        this.chance = chance;
        return this;
    }

    public ConfiguredRoadDecoration withNormalOffset(int normalOffset) {
        this.normalOffset = normalOffset;
        return this;
    }

    public ConfiguredRoadDecoration(RoadDecoration decoration) {
        this.decoration = decoration;
    }

    public ConfiguredRoadDecoration(RoadDecoration decoration, float chance, int normalOffset) {
        this.decoration = decoration;
        this.chance = chance;
        this.normalOffset = normalOffset;
    }

    public boolean place(WorldGenLevel level, Random random, BlockPos blockPos, Vec3 normal, Vec3 tangent) {
        if (random.nextFloat() < this.chance) {
            BlockPos adjustedPos = blockPos.offset((int) (normalOffset * Math.round(normal.x)), 0, (int) (normalOffset * Math.round(normal.z)));
            return this.decoration.place(level, random, adjustedPos, normal, tangent);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("ConfiguredRoadDecoration[%s][chance=%f][normalOffset=%d]", this.decoration, this.chance, this.normalOffset);
    }
}
