package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.config.YRConfigFabric;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.world.InteractionResult;

public class ConfigModuleFabric {
    public static void init() {
        AutoConfig.register(YRConfigFabric.class, Toml4jConfigSerializer::new);
        AutoConfig.getConfigHolder(YRConfigFabric.class).registerSaveListener(ConfigModuleFabric::bakeConfig);
        AutoConfig.getConfigHolder(YRConfigFabric.class).registerLoadListener(ConfigModuleFabric::bakeConfig);
        bakeConfig(AutoConfig.getConfigHolder(YRConfigFabric.class).get());
    }

    private static InteractionResult bakeConfig(ConfigHolder<YRConfigFabric> configHolder, YRConfigFabric configFabric) {
        bakeConfig(configFabric);
        return InteractionResult.SUCCESS;
    }

    private static void bakeConfig(YRConfigFabric configFabric) {
        // AutoConfig doesn't enforce ranges on decimal values, so clamp them to the valid ranges here
        ConfigModule.Advanced advanced = YungsRoadsCommon.CONFIG.advanced;
        advanced.nodeStepDistance = (int) GlobalSetting.NODE_STEP_DISTANCE.clamp(configFabric.advanced.nodeStepDistance);
        advanced.heuristicWeight = GlobalSetting.HEURISTIC_WEIGHT.clamp(configFabric.advanced.heuristicWeight);

        ConfigModule.Debug debug = YungsRoadsCommon.CONFIG.debug;
        debug.placeRoads = configFabric.debug.placeRoads;
    }

    /**
     * Writes the current advanced and debug settings to the config file.
     */
    public static void saveRoadSettings() {
        ConfigHolder<YRConfigFabric> holder = AutoConfig.getConfigHolder(YRConfigFabric.class);
        YRConfigFabric configFabric = holder.getConfig();

        ConfigModule.Advanced advanced = YungsRoadsCommon.CONFIG.advanced;
        configFabric.advanced.nodeStepDistance = advanced.nodeStepDistance;
        configFabric.advanced.heuristicWeight = advanced.heuristicWeight;

        ConfigModule.Debug debug = YungsRoadsCommon.CONFIG.debug;
        configFabric.debug.placeRoads = debug.placeRoads;

        holder.save();
    }
}
