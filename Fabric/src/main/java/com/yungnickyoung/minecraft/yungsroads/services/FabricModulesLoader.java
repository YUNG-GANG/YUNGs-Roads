package com.yungnickyoung.minecraft.yungsroads.services;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleFabric;
import com.yungnickyoung.minecraft.yungsroads.module.FeatureModuleFabric;
import com.yungnickyoung.minecraft.yungsroads.module.PlacementModuleFabric;

public class FabricModulesLoader implements IModulesLoader {
    @Override
    public void loadModules() {
        ConfigModuleFabric.init();
        PlacementModuleFabric.init();
        FeatureModuleFabric.init();
    }
}
