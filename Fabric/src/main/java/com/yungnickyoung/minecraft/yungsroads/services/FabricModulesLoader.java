package com.yungnickyoung.minecraft.yungsroads.services;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleFabric;
import com.yungnickyoung.minecraft.yungsroads.module.FeatureModuleFabric;

public class FabricModulesLoader implements IModulesLoader {
    @Override
    public void loadModules() {
        ConfigModuleFabric.init();
        FeatureModuleFabric.init();
    }
}
