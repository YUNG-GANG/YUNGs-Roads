package com.yungnickyoung.minecraft.yungsroads.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class YRConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("YUNG's Roads");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
