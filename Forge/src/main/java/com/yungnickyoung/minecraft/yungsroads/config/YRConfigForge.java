package com.yungnickyoung.minecraft.yungsroads.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class YRConfigForge {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ConfigGeneralForge general;
    public static final ConfigAdvancedForge advanced;
    public static final ConfigDebugForge debug;

    static {
        BUILDER.push("YUNG's Roads");

        general = new ConfigGeneralForge(BUILDER);
        advanced = new ConfigAdvancedForge(BUILDER);
        debug = new ConfigDebugForge(BUILDER);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
