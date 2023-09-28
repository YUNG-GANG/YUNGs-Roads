package com.yungnickyoung.minecraft.yungsroads.module;

import com.mojang.serialization.Codec;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.placement.BlockBelowFilter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class PlacementModuleFabric {
    public static void init() {
        PlacementModule.BLOCK_BELOW_FILTER = register("block_below_filter", BlockBelowFilter.CODEC);
    }

    private static <P extends PlacementModifier> PlacementModifierType<P> register(String name, Codec<P> codec) {
        return Registry.register(Registry.PLACEMENT_MODIFIERS, new ResourceLocation(YungsRoadsCommon.MOD_ID, name), () -> codec);
    }
}
