package com.yungnickyoung.minecraft.yungsroads.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigDebugForge {
    public final ModConfigSpec.ConfigValue<Boolean> enableExtraDebugF3Info;
    public final ModConfigSpec.ConfigValue<Boolean> placeRoads;

    public ConfigDebugForge(final ModConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Debug settings.
                                ##########################################################################################################""")
                .push("Debug");

        enableExtraDebugF3Info = BUILDER
                .comment("""
                            Whether to enable extra debug info on the F3 overlay.
                            This will render extra info on the F3 overlay, such as info for the Road node at the current player pos.
                            Default: false""".indent(1))
                .define("enableExtraDebugF3Info", false);

        placeRoads = BUILDER
                .comment("""
                            Whether to place road blocks. Disabling this is useful for viewing routes with the
                            in-game road debug screen's overlay, without changing the terrain.
                            Default: true""".indent(1))
                .define("placeRoads", true);

        BUILDER.pop();
    }
}

