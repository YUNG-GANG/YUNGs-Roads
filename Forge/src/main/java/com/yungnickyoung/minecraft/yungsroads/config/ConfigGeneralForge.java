package com.yungnickyoung.minecraft.yungsroads.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ConfigGeneralForge {
    public final ForgeConfigSpec.ConfigValue<String> structures;

    public ConfigGeneralForge(final ForgeConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # General settings.
                                ##########################################################################################################""")
                .push("General");

        structures = BUILDER
                .comment(
                        """
                                List of configured structure tags that can act as endpoints for roads.
                                Each entry can be a configured structure feature resource location OR a tag.
                                Must be a comma-separated list, encased in square brackets.
                                Example: "[#minecraft:village,minecraft:shipwreck_beached]"
                                Default: [#minecraft:village]""".indent(1))
                .worldRestart()
                .define("Valid Structures", "[#minecraft:village]");

        BUILDER.pop();
    }
}

