package com.yungnickyoung.minecraft.yungsroads.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ConfigDebugForge {
    public final ForgeConfigSpec.ConfigValue<Boolean> enableDebugMap;
    public final ForgeConfigSpec.ConfigValue<Boolean> enableExtraDebugF3Info;
    public final ForgeConfigSpec.ConfigValue<Boolean> placeUnjitteredPosDebugMarkers;
    public final ForgeConfigSpec.ConfigValue<Boolean> placeJitteredPosDebugMarkers;
    public final ForgeConfigSpec.ConfigValue<Boolean> placeRoadEndpointDebugMarkers;
    public final ForgeConfigSpec.ConfigValue<Boolean> placeRoadSegmentEndpointDebugMarkers;
    public final ForgeConfigSpec.ConfigValue<Boolean> placeStraightDebugLine;
    public final ForgeConfigSpec.ConfigValue<Boolean> placeDebugPaths;

    public ConfigDebugForge(final ForgeConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Debug settings.
                                ##########################################################################################################""")
                .push("Debug");

        enableDebugMap = BUILDER
                .comment("""
                            Whether to enable the debug map overlay.
                            This will render a map of all villages and paths in the world on the F3 overlay.
                            If enabled, hold left control and press F3 to toggle map visibility.
                            Default: false""".indent(1))
                .define("enableDebugMap", false);

        enableExtraDebugF3Info = BUILDER
                .comment("""
                            Whether to enable extra debug info on the F3 overlay.
                            This will render extra info on the F3 overlay, such as info for the Road node at the current player pos.
                            Default: false""".indent(1))
                .define("enableExtraDebugF3Info", false);

        placeUnjitteredPosDebugMarkers = BUILDER
                .comment("""
                            Whether to place debug markers at the unjittered positions of Road nodes.
                            Markers will be towers of purple wool.
                            Default: false""".indent(1))
                .define("placeUnjitteredPosDebugMarkers", false);

        placeJitteredPosDebugMarkers = BUILDER
                .comment("""
                            Whether to place debug markers at the jittered (final) positions of Road nodes.
                            Markers will be towers of redstone blocks.
                            Default: false""".indent(1))
                .define("placeJitteredPosDebugMarkers", false);

        placeRoadEndpointDebugMarkers = BUILDER
                .comment("""
                            Whether to place debug markers at the endpoints of Roads.
                            Markers will be towers of emerald blocks.
                            Default: false""".indent(1))
                .define("placeRoadEndpointDebugMarkers", false);

        placeRoadSegmentEndpointDebugMarkers = BUILDER
                .comment("""
                            Whether to place debug markers at the endpoints of Road segments.
                            Markers will be towers of gold blocks.
                            Default: false""".indent(1))
                .define("placeRoadSegmentEndpointDebugMarkers", false);

        placeStraightDebugLine = BUILDER
                .comment("""
                            Whether to place a straight line of gold blocks between the start and end of each Road.
                            Default: false""".indent(1))
                .define("placeStraightDebugLine", false);

        placeDebugPaths = BUILDER
                .comment("""
                            Whether to place debug paths instead of normal paths.
                            Debug paths are only a single block wide and are made of lapis blocks.
                            Default: false""".indent(1))
                .define("placeDebugPaths", false);

        BUILDER.pop();
    }
}

