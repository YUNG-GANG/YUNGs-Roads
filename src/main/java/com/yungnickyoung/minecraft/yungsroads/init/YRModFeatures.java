package com.yungnickyoung.minecraft.yungsroads.init;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.world.RoadFeature;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class YRModFeatures {
    /* Registry for deferred registration */
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, YungsRoads.MOD_ID);

    /* Features */
    public static final RegistryObject<Feature<NoFeatureConfig>> ROAD_FEATURE = FEATURES.register("road", RoadFeature::new);

    public static void init () {
        FEATURES.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(YRModFeatures::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(YRModFeatures::onBiomeLoad);
    }

    /**
     * Register configured features.
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(YRModConfiguredFeatures::registerConfiguredFeatures);
    }

    /**
     * Adds configured features to appropriate biomes.
     */
    private static void onBiomeLoad(BiomeLoadingEvent event) {
        event.getGeneration().getFeatures(GenerationStage.Decoration.SURFACE_STRUCTURES).add(() -> YRModConfiguredFeatures.CONFIGURED_ROAD_FEATURE);
    }
}
