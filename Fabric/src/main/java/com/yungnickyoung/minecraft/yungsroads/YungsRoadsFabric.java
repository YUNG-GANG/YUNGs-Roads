package com.yungnickyoung.minecraft.yungsroads;

import net.fabricmc.api.ModInitializer;

public class YungsRoadsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        YungsRoadsCommon.init();
    }
}
