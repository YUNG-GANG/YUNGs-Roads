package com.yungnickyoung.minecraft.yungsroads.init;

import com.yungnickyoung.minecraft.yungsroads.debug.PathImageCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

public class YRModDebug {
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(YRModDebug::registerCommands);
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        PathImageCommand.register(event.getDispatcher());
    }
}
