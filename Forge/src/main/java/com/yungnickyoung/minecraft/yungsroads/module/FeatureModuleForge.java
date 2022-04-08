package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.RoadFeature;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.IForgeRegistry;

public class FeatureModuleForge {
    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Feature.class, FeatureModuleForge::registerFeatures);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(FeatureModuleForge::registerConfiguredFeatures);
        MinecraftForge.EVENT_BUS.addListener(FeatureModuleForge::addFeaturesToBiomes);
    }

    private static void registerFeatures(RegistryEvent.Register<Feature<?>> event) {
        FeatureModule.ROAD = register(event.getRegistry(), "road", new RoadFeature());
    }

    private static void registerConfiguredFeatures(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ConfiguredFeatureModule.registerConfiguredFeatures();
            ConfiguredFeatureModule.registerPlacedFeatures();
        });
    }

    private static void addFeaturesToBiomes(BiomeLoadingEvent event) {
        event.getGeneration().getFeatures(GenerationStep.Decoration.LOCAL_MODIFICATIONS).add(Holder.direct(ConfiguredFeatureModule.ROAD_PLACED));
    }

    private static <FC extends FeatureConfiguration> Feature<FC> register(IForgeRegistry<Feature<?>> registry, String name, Feature<FC> feature) {
        feature.setRegistryName(new ResourceLocation(YungsRoadsCommon.MOD_ID, name));
        registry.register(feature);
        return feature;
    }
}
