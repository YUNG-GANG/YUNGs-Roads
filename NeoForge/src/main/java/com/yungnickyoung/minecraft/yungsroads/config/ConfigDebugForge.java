package com.yungnickyoung.minecraft.yungsroads.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigDebugForge {
    public final ModConfigSpec.ConfigValue<Boolean> placeRoads;

    public ConfigDebugForge(final ModConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Debug settings, for testing and tuning roads. The tuning screen (F8 by default) sets these too.
                                ##########################################################################################################""")
                .push("Debug");

        placeRoads = BUILDER
                .comment("""
                            Whether to place road blocks in the world.
                            Turn off to see routes on the tuning screen's overlay without changing terrain.
                            Default: true""".indent(1))
                .define("Place Roads", true);

        BUILDER.pop();
    }
}

