package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.config.YRConfigNeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

public class ConfigModuleNeoForge {
    public static void init(IEventBus eventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, YRConfigNeoForge.SPEC, "YungsRoads-neoforge-1_21.toml");
        NeoForge.EVENT_BUS.addListener(ConfigModuleNeoForge::onWorldLoad);
        eventBus.addListener(ConfigModuleNeoForge::onConfigChange);
    }

    private static void onWorldLoad(LevelEvent.Load event) {
        // Client levels load again on every dimension change, which would undo settings applied from the debug screen
        if (event.getLevel().isClientSide()) {
            return;
        }
        bakeConfig();
    }

    private static void onConfigChange(ModConfigEvent event) {
        if (event.getConfig().getSpec() != YRConfigNeoForge.SPEC) {
            return;
        }
        if (event instanceof ModConfigEvent.Reloading) {
            // Settings that need a world restart keep returning the values they were loaded with until the world
            // restarts, even after the file changes, so baking them here would undo settings applied from the debug
            // screen. That includes the reload fired when the debug screen saves.
            bakeDebugConfig();
        } else {
            bakeConfig();
        }
    }

    private static void bakeConfig() {
        bakeWorldConfig();
        bakeDebugConfig();
    }

    /** Bakes the settings that need a world restart to change. */
    private static void bakeWorldConfig() {
        YungsRoadsCommon.CONFIG.advanced.nodeStepDistance = YRConfigNeoForge.advanced.nodeStepDistance.get();
        YungsRoadsCommon.CONFIG.advanced.heuristicWeight = YRConfigNeoForge.advanced.heuristicWeight.get();
    }

    private static void bakeDebugConfig() {
        YungsRoadsCommon.CONFIG.debug.placeRoads = YRConfigNeoForge.debug.placeRoads.get();
    }

    /**
     * Writes the current advanced and debug settings to the config file.
     */
    public static void saveRoadSettings() {
        ConfigModule.Advanced advanced = YungsRoadsCommon.CONFIG.advanced;
        YRConfigNeoForge.advanced.nodeStepDistance.set(advanced.nodeStepDistance);
        YRConfigNeoForge.advanced.heuristicWeight.set(advanced.heuristicWeight);

        ConfigModule.Debug debug = YungsRoadsCommon.CONFIG.debug;
        YRConfigNeoForge.debug.placeRoads.set(debug.placeRoads);

        YRConfigNeoForge.SPEC.save();
    }
}
