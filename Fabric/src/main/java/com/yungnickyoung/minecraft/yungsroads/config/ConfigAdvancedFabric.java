package com.yungnickyoung.minecraft.yungsroads.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ConfigAdvancedFabric {
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int nodeStepDistance = 8;

    @ConfigEntry.Gui.Tooltip
    public double jitterAmount = 4.0;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public double heuristicWeight = 1.2;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public double slopeWeight = 25.0;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public double maxGrade = 1.0;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public double waterWeight = 8.0;

    @ConfigEntry.Gui.Tooltip(count = 3)
    public int maxBridgeLength = 32;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public int smoothingRadius = 6;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public int maxCutDepth = 4;

    @ConfigEntry.Gui.Tooltip(count = 2)
    public int maxFillDepth = 2;

    @ConfigEntry.Gui.Tooltip(count = 3)
    public int maxLandBridgeLength = 24;
}
