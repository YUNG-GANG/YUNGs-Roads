package com.yungnickyoung.minecraft.yungsroads.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ConfigAdvancedFabric {
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int nodeStepDistance = 8;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public double heuristicWeight = 1.2;
}
