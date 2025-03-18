package com.yungnickyoung.minecraft.yungsroads;

import com.yungnickyoung.minecraft.yungsroads.module.BiomeModificationModuleFabric;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleFabric;
import net.fabricmc.api.ModInitializer;

public class YungsRoadsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        YungsRoadsCommon.init();

        ConfigModuleFabric.init();
        BiomeModificationModuleFabric.init();
    }
}
