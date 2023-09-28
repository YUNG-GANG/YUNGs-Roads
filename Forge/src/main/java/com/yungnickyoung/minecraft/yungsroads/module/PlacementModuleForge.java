package com.yungnickyoung.minecraft.yungsroads.module;

import com.mojang.serialization.Codec;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.placement.BlockBelowFilter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class PlacementModuleForge {
    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PlacementModuleForge::commonSetup);
    }

    private static void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PlacementModule.BLOCK_BELOW_FILTER = register("block_below_filter", BlockBelowFilter.CODEC);
        });
    }

    private static <T extends PlacementModifier> PlacementModifierType<T> register(String name, Codec<T> codec) {
        return Registry.register(Registry.PLACEMENT_MODIFIERS, new ResourceLocation(YungsRoadsCommon.MOD_ID, name), () -> codec);
    }
}
