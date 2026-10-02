package com.yungnickyoung.minecraft.yungsroads.config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class ConfigDebugFabric {
    @ConfigEntry.Gui.Tooltip
    public boolean enableExtraDebugF3Info = false;

    @ConfigEntry.Gui.Tooltip
    public boolean placeRoads = true;

    @ConfigEntry.Gui.Tooltip
    public boolean placeUnjitteredPosDebugMarkers = false;

    @ConfigEntry.Gui.Tooltip
    public boolean placeJitteredPosDebugMarkers = false;

    @ConfigEntry.Gui.Tooltip
    public boolean placeRoadEndpointDebugMarkers = false;

    @ConfigEntry.Gui.Tooltip
    public boolean placeStraightDebugLine = false;

    @ConfigEntry.Gui.Tooltip
    public boolean placeDebugPaths = false;
}
