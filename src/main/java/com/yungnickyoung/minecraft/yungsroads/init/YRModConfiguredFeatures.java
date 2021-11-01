package com.yungnickyoung.minecraft.yungsroads.init;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.IFeatureConfig;

public class YRModConfiguredFeatures {
    public static final ConfiguredFeature<?, ?> CONFIGURED_ROAD_FEATURE = YRModFeatures.ROAD_FEATURE.get()
            .withConfiguration(IFeatureConfig.NO_FEATURE_CONFIG);

    public static void registerConfiguredFeatures() {
        Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, new ResourceLocation(YungsRoads.MOD_ID, "road"), CONFIGURED_ROAD_FEATURE);
    }
}
