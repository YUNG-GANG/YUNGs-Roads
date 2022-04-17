package com.yungnickyoung.minecraft.yungsroads.services;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleForge;
import com.yungnickyoung.minecraft.yungsroads.module.DebugModuleForge;
import com.yungnickyoung.minecraft.yungsroads.module.FeatureModuleForge;
import com.yungnickyoung.minecraft.yungsroads.module.PlacementModuleForge;

public class ForgeModulesLoader implements IModulesLoader {
    @Override
    public void loadModules() {
        ConfigModuleForge.init();
        PlacementModuleForge.init();
        FeatureModuleForge.init();
        if (YungsRoadsCommon.DEBUG_MODE) {
            DebugModuleForge.init();
        }
    }
}
