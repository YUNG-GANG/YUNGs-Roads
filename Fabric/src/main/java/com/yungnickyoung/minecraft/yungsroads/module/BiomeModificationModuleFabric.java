package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class BiomeModificationModuleFabric {
    public static void init() {
        addRoadFeatureToBiomes();
    }

    private static void addRoadFeatureToBiomes() {
        ResourceKey<PlacedFeature> roadFeatureKey = ResourceKey.create(Registries.PLACED_FEATURE, YungsRoadsCommon.id("road"));
        BiomeModifications.create(YungsRoadsCommon.id("road_addition"))
                .add(ModificationPhase.ADDITIONS,
                        // Every biome, so roads can be in any dimension. Dimensions without a road network skip it.
                        BiomeSelectors.all(),
                        context -> context.getGenerationSettings().addFeature(
                                GenerationStep.Decoration.LOCAL_MODIFICATIONS,
                                roadFeatureKey));
    }
}
