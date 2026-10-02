package com.yungnickyoung.minecraft.yungsroads.services;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleFabric;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public void saveRoadSettings() {
        ConfigModuleFabric.saveRoadSettings();
    }
}
