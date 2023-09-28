package com.yungnickyoung.minecraft.yungsroads.world.road.decoration;

import com.mojang.serialization.Codec;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RoadDecorations {
    private static final Map<String, ManualRoadDecoration> MANUAL_BY_NAME = new HashMap<>();

    public static final RoadDecoration BUSH1            = register("bush1", BushRoadDecoration::new);
    public static final RoadDecoration SMALL_WOOD_BENCH = register("small_wood_bench", SmallWoodBenchRoadDecoration::new);
    public static final RoadDecoration STONE_LAMP_POST  = register("stone_lamp_post", StoneLampPostRoadDecoration::new);

    public static ManualRoadDecoration register(String name, Function<String, ManualRoadDecoration> decorationSupplier) {
        ManualRoadDecoration decoration = decorationSupplier.apply(name);
        MANUAL_BY_NAME.put(name, decoration);
        return decoration;
    }

    @Nullable
    public static ManualRoadDecoration manual(String name) {
        ManualRoadDecoration decoration = MANUAL_BY_NAME.get(name);
        if (decoration == null) {
            YungsRoadsCommon.LOGGER.error("Invalid road decoration name {}", name);
        }
        return MANUAL_BY_NAME.get(name);
    }

    public static FeatureRoadDecoration feature(String location) {
        return new FeatureRoadDecoration(BuiltinRegistries.PLACED_FEATURE.getHolderOrThrow(ResourceKey.create(
                Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation(location)
        )));
    }
}
