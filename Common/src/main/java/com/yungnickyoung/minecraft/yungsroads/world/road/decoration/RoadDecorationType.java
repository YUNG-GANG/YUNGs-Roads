package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.MapCodec;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public interface RoadDecorationType<T extends RoadDecoration> {
    Map<ResourceLocation, RoadDecorationType<? extends RoadDecoration>> NAME_TO_TYPE = new HashMap<>();
    Map<RoadDecorationType<? extends RoadDecoration>, ResourceLocation> TYPE_TO_NAME = new HashMap<>();
    BiMap<Integer, RoadDecorationType<? extends RoadDecoration>> IDS = HashBiMap.create();

    RoadDecorationType<FeatureRoadDecoration> FEATURE_DECORATION = register("feature", 0, FeatureRoadDecoration.CODEC);
    RoadDecorationType<ManualRoadDecoration> MANUAL_DECORATION = register("manual", 1, ManualRoadDecoration.CODEC);

    MapCodec<T> codec();

    static <U extends RoadDecoration> RoadDecorationType<U> register(String name, int id, MapCodec<U> codec) {
        ResourceLocation location = YungsRoadsCommon.id(name);
        RoadDecorationType<U> roadDecorationType = create(codec);
        NAME_TO_TYPE.put(location, roadDecorationType);
        TYPE_TO_NAME.put(roadDecorationType, location);
        IDS.put(id, roadDecorationType);
        return () -> codec;
    }

    static <U extends RoadDecoration> RoadDecorationType<U> create(MapCodec<U> codec) {
        return () -> codec;
    }
}
