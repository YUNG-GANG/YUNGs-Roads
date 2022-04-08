package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.RoadFeature;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;

public class FeatureModuleFabric {
    public static void init() {
        registerFeatures();
        ConfiguredFeatureModule.registerConfiguredFeatures();
        ConfiguredFeatureModule.registerPlacedFeatures();
        addFeaturesToBiomes();
    }

    private static void registerFeatures() {
        FeatureModule.ROAD = Registry.register(Registry.FEATURE, new ResourceLocation(YungsRoadsCommon.MOD_ID, "road"), new RoadFeature());
    }

    private static void addFeaturesToBiomes() {
        BiomeModifications.create(new ResourceLocation(YungsRoadsCommon.MOD_ID, "road_addition"))
                .add(ModificationPhase.ADDITIONS,
                        context -> true,
                        context -> context.getGenerationSettings().addBuiltInFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, ConfiguredFeatureModule.ROAD_PLACED));
    }
}
