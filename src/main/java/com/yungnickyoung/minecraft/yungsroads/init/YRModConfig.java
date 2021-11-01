package com.yungnickyoung.minecraft.yungsroads.init;

import com.yungnickyoung.minecraft.yungsroads.config.YRConfig;
import net.minecraftforge.fml.ModLoadingContext;

public class YRModConfig {
    public static void init() {
        // Register mod config with Forge
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, YRConfig.SPEC, "yungsroads-forge-1_16.toml");
    }
}
