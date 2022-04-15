package com.yungnickyoung.minecraft.yungsroads.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name="YungsRoads-fabric-1_18")
public class YRConfigFabric implements ConfigData {
    @ConfigEntry.Category("YUNG's Roads")
    @ConfigEntry.Gui.TransitiveObject
    public ConfigGeneralFabric general = new ConfigGeneralFabric();
}
