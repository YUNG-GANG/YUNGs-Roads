package com.yungnickyoung.minecraft.yungsroads.config;

import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigAdvancedForge {
    public final ModConfigSpec.ConfigValue<Integer> nodeStepDistance;
    public final ModConfigSpec.ConfigValue<Double> heuristicWeight;

    public ConfigAdvancedForge(final ModConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Advanced settings, shared by every road type.
                                # Each road type's own settings are in its file in the yungsroads/road_type datapack registry.
                                ##########################################################################################################""")
                .push("Advanced");

        nodeStepDistance = BUILDER
                .comment(
                        """
                                The distance between adjacent pathfinding nodes, in blocks.
                                Lower values follow the terrain more closely, but are slower to generate.
                                Default: 8""".indent(1))
                .worldRestart()
                .defineInRange("Node Step Distance", 8, (int) GlobalSetting.NODE_STEP_DISTANCE.min(), (int) GlobalSetting.NODE_STEP_DISTANCE.max());

        heuristicWeight = BUILDER
                .comment(
                        """
                                How strongly pathfinding is pulled toward the destination.
                                1.0 always finds the cheapest road. Higher values generate faster but give slightly less optimal roads.
                                Default: 1.2""".indent(1))
                .worldRestart()
                .defineInRange("Heuristic Weight", 1.2, GlobalSetting.HEURISTIC_WEIGHT.min(), GlobalSetting.HEURISTIC_WEIGHT.max());

        BUILDER.pop();
    }
}
