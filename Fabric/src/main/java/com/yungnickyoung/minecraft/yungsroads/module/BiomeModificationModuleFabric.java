package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class BiomeModificationModuleFabric {
    public static void init() {
        addFeaturesToBiomes();
    }

    private static void addFeaturesToBiomes() {
        ResourceKey<PlacedFeature> roadFeatureKey = ResourceKey.create(Registries.PLACED_FEATURE, YungsRoadsCommon.id("road"));
        BiomeModifications.create(YungsRoadsCommon.id("road_addition"))
                .add(ModificationPhase.ADDITIONS,
                        context -> context.hasTag(BiomeTags.IS_OVERWORLD), // Add to all biomes in Overworld
                        context -> context.getGenerationSettings().addFeature(
                                GenerationStep.Decoration.LOCAL_MODIFICATIONS,
                                roadFeatureKey));
    }
}
