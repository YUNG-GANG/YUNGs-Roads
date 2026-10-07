package com.yungnickyoung.minecraft.yungsroads;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleNeoForge;
import com.yungnickyoung.minecraft.yungsroads.module.DebugModuleNeoForge;
import com.yungnickyoung.minecraft.yungsroads.module.LiveRoadModuleNeoForge;
import com.yungnickyoung.minecraft.yungsroads.module.RoadTypeModuleNeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(YungsRoadsCommon.MOD_ID)
public class YungsRoadsNeoForge {
    public YungsRoadsNeoForge(IEventBus eventBus, ModContainer container) {
        YungsRoadsCommon.init();

        ConfigModuleNeoForge.init(eventBus, container);
        RoadTypeModuleNeoForge.init(eventBus);
        LiveRoadModuleNeoForge.init();

        // The debug module uses client-only classes, so it must not be loaded on dedicated servers
        if (FMLEnvironment.dist.isClient()) {
            DebugModuleNeoForge.init(eventBus);
        }
    }
}