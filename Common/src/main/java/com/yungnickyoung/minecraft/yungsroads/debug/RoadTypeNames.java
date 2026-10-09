package com.yungnickyoung.minecraft.yungsroads.debug;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * How road types are named on the debug screen.
 * <p>
 * Names in text are colored, so a road type named with an ordinary word, such as default, reads as a name rather than
 * as part of the sentence.
 */
public final class RoadTypeNames {
    /** The color of road type names in text. */
    public static final int COLOR = 0xC8A8FF;

    private RoadTypeNames() {
    }

    /** The road type's id, without the namespace if it's the mod's own. */
    public static String name(ResourceLocation typeId) {
        return typeId.getNamespace().equals(YungsRoadsCommon.MOD_ID) ? typeId.getPath() : typeId.toString();
    }

    /** The road type's name, colored as a name. */
    public static MutableComponent styledName(ResourceLocation typeId) {
        return Component.literal(name(typeId)).withColor(COLOR);
    }

    /** The road type's full id, colored as a name, for where the namespace matters, such as an id to add to a file. */
    public static MutableComponent styledId(ResourceLocation typeId) {
        return Component.literal(typeId.toString()).withColor(COLOR);
    }

    /**
     * The road type's name, colored as a name, with which of its variants it is if it has more than one. Variants count
     * from 1.
     */
    public static Component name(ResourceLocation typeId, int variant, int variantCount) {
        return variantCount > 1
                ? Component.translatable("yungsroads.screen.road_type.variant", styledName(typeId), variant + 1, variantCount)
                : styledName(typeId);
    }
}
