package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;

public class ConfiguredFeatureModule {
    public static final ConfiguredFeature<?, ?> ROAD_CONFIGURED = new ConfiguredFeature<>(FeatureModule.ROAD, FeatureConfiguration.NONE);
    public static final PlacedFeature ROAD_PLACED = new PlacedFeature(
            Holder.direct(ROAD_CONFIGURED),
            List.of());

    public static void registerConfiguredFeatures() {
        Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, "road"), ROAD_CONFIGURED);
    }

    public static void registerPlacedFeatures() {
        Registry.register(BuiltinRegistries.PLACED_FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, "road"), ROAD_PLACED);
    }
}
