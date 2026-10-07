package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * How road types are named on the debug screen.
 */
final class RoadTypeNames {
    private RoadTypeNames() {
    }

    /** The road type's id, without the namespace if it's the mod's own. */
    static String name(ResourceLocation typeId) {
        return typeId.getNamespace().equals(YungsRoadsCommon.MOD_ID) ? typeId.getPath() : typeId.toString();
    }

    /** The road type's name, with which of its variants it is if it has more than one. Variants count from 1. */
    static Component name(ResourceLocation typeId, int variant, int variantCount) {
        return variantCount > 1
                ? Component.translatable("yungsroads.screen.road_type.variant", name(typeId), variant + 1, variantCount)
                : Component.literal(name(typeId));
    }

    /** Like {@link #name(ResourceLocation, int, int)}, but shorter, for the road type picker. */
    static Component shortName(ResourceLocation typeId, int variant, int variantCount) {
        return variantCount > 1
                ? Component.translatable("yungsroads.screen.road_type.variant_short", name(typeId), variant + 1, variantCount)
                : Component.literal(name(typeId));
    }
}
