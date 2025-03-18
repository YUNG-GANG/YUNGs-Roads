package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsapi.api.autoregister.AutoRegister;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

@AutoRegister(YungsRoadsCommon.MOD_ID)
public class FeatureModule {
    @AutoRegister("road")
    public static Feature<RoadFeatureConfiguration> ROAD = new RoadFeature();
}
