package com.yungnickyoung.minecraft.yungsroads;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModuleNeoForge;
import com.yungnickyoung.minecraft.yungsroads.module.DebugModuleNeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(YungsRoadsCommon.MOD_ID)
public class YungsRoadsNeoForge {
    public YungsRoadsNeoForge(IEventBus eventBus, ModContainer container) {
        YungsRoadsCommon.init();

        ConfigModuleNeoForge.init(eventBus, container);
        DebugModuleNeoForge.init(eventBus);
    }
}