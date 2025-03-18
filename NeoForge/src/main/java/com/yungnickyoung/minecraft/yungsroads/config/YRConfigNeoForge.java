package com.yungnickyoung.minecraft.yungsroads.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class YRConfigNeoForge {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

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
