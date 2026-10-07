package com.yungnickyoung.minecraft.yungsroads.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ConfigDebugFabric {
    @ConfigEntry.Gui.Tooltip
    public boolean enableExtraDebugF3Info = false;

    @ConfigEntry.Gui.Tooltip
    public boolean placeRoads = true;
}
