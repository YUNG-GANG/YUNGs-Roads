package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class ManualRoadDecoration extends RoadDecoration {
    public static final Codec<ManualRoadDecoration> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    Codec.STRING.fieldOf("name").forGetter(decoration -> decoration.name))
            .apply(instance, ManualRoadDecoration::new));
    private final String name;

    public ManualRoadDecoration(String name) {
//        super(Type.MANUAL);
        this.name = name;
    }

    @Override
    public boolean place(WorldGenLevel level, Random random, BlockPos blockPos, Vec3 normal, Vec3 tangent) {
        return false;
    }

    public String getName() {
        return name;
    }

//    @Override
//    public Codec<? extends RoadDecoration> codec() {
//        return CODEC;
//    }

    @Override
    public String toString() {
        return "ManualRoadDecoration[" + this.name + "]";
    }
}
