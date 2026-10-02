package com.yungnickyoung.minecraft.yungsroads.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigAdvancedForge {
    public final ModConfigSpec.ConfigValue<Integer> nodeStepDistance;
    public final ModConfigSpec.ConfigValue<Double> jitterAmount;
    public final ModConfigSpec.ConfigValue<Double> heuristicWeight;
    public final ModConfigSpec.ConfigValue<Double> slopeWeight;
    public final ModConfigSpec.ConfigValue<Double> maxGrade;
    public final ModConfigSpec.ConfigValue<Double> waterWeight;

    public ConfigAdvancedForge(final ModConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Advanced settings.
                                ##########################################################################################################""")
                .push("Advanced");

        nodeStepDistance = BUILDER
                .comment(
                        """
                                The distance between adjacent pathfinding nodes, in blocks.
                                Lower values follow the terrain more closely, but are slower to generate.
                                Default: 8""".indent(1))
                .worldRestart()
                .defineInRange("Node Step Distance", 8, 2, 32);

        jitterAmount = BUILDER
                .comment(
                        """
                                The maximum noise-based sideways offset applied to the road's shape, in blocks.
                                Default: 4.0""".indent(1))
                .worldRestart()
                .defineInRange("Jitter Amount", 4.0, 0.0, 16.0);

        heuristicWeight = BUILDER
                .comment(
                        """
                                How strongly pathfinding is pulled toward the destination.
                                1.0 always finds the cheapest road. Higher values generate faster but give slightly less optimal roads.
                                Default: 1.2""".indent(1))
                .worldRestart()
                .defineInRange("Heuristic Weight", 1.2, 1.0, 10.0);

        slopeWeight = BUILDER
                .comment(
                        """
                                The extra cost of steep terrain. Each step's cost is multiplied by (1 + Slope Weight * grade^2),
                                where grade is rise over run. Higher values make roads avoid hills and mountains more.
                                Default: 25.0""".indent(1))
                .worldRestart()
                .defineInRange("Slope Weight", 25.0, 0.0, 1000.0);

        maxGrade = BUILDER
                .comment(
                        """
                                The steepest rise over run allowed between two nodes. Steeper terrain is never crossed.
                                1.0 is a 45 degree slope.
                                Default: 1.0""".indent(1))
                .worldRestart()
                .defineInRange("Max Grade", 1.0, 0.05, 10.0);

        waterWeight = BUILDER
                .comment(
                        """
                                The extra cost of crossing water. Each water step's cost multiplier is increased by this amount.
                                Higher values make roads avoid rivers and lakes more. Oceans are never crossed.
                                Default: 8.0""".indent(1))
                .worldRestart()
                .defineInRange("Water Weight", 8.0, 0.0, 1000.0);

        BUILDER.pop();
    }
}
