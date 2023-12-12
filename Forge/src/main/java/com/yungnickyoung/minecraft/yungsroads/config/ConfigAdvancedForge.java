package com.yungnickyoung.minecraft.yungsroads.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ConfigAdvancedForge {
    public final Path path;
    public final Segment segment;


    public ConfigAdvancedForge(final ForgeConfigSpec.Builder BUILDER) {
        BUILDER
                .comment(
                        """
                                ##########################################################################################################
                                # Advanced settings.
                                ##########################################################################################################""")
                .push("Advanced");

        path = new Path(BUILDER);
        segment = new Segment(BUILDER);

        BUILDER.pop();
    }

    public static class Path {
        public final ForgeConfigSpec.ConfigValue<Integer> nodeStepDistance;
        public final ForgeConfigSpec.ConfigValue<Double> jitterAmount;
        public final ForgeConfigSpec.ConfigValue<Double> hScalar;
        public final ForgeConfigSpec.ConfigValue<Double> pathScalar;
        public final ForgeConfigSpec.ConfigValue<Double> highSlopeFactorScalar;
        public final ForgeConfigSpec.ConfigValue<Double> lowSlopeFactorScalar;
        public final ForgeConfigSpec.ConfigValue<Double> slopeFactorThreshold;
        public final ForgeConfigSpec.ConfigValue<Double> altitudePunishment;

        public Path(ForgeConfigSpec.Builder BUILDER) {
            BUILDER
                    .comment(
                            """
                                    ##########################################################################################################
                                    # Advanced Path settings.
                                    ##########################################################################################################""")
                    .push("Path");

            nodeStepDistance = BUILDER
                    .comment(
                            """
                                    The distance between adjacent nodes in the path, in blocks.
                                    Default: 2""".indent(1))
                    .worldRestart()
                    .define("Node Step Distance", 2);

            jitterAmount = BUILDER
                    .comment(
                            """
                                    The amount of noise-based jitter to apply to the path's shape.
                                    Default: 4""".indent(1))
                    .worldRestart()
                    .define("Jitter Amount", 4.0);

            hScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the H function (distance) of the path's shape.
                                    Default: 10""".indent(1))
                    .worldRestart()
                    .define("H Scalar", 10.0);

            pathScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the path's length, in terms of nodes.
                                    Increasing this value will make the paths straighter and more direct.
                                    Default: 3.0""".indent(1))
                    .worldRestart()
                    .define("Path Scalar", 3.0);

            highSlopeFactorScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the slope factor of the path's shape.
                                    Increasing this value will make the paths flatter, I think.
                                    Default: 10""".indent(1))
                    .worldRestart()
                    .define("High Slope Factor Scalar", 10.0);

            lowSlopeFactorScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the slope factor of the path's shape.
                                    Increasing this value will make the paths flatter, I think.
                                    Default: 2""".indent(1))
                    .worldRestart()
                    .define("Low Slope Factor Scalar", 2.0);

            slopeFactorThreshold = BUILDER
                    .comment(
                            """
                                    The PV threshold between low and high slope factors.
                                    Default: 0.0""".indent(1))
                    .worldRestart()
                    .define("Slope Factor Threshold", 0.0);

            altitudePunishment = BUILDER
                    .comment(
                            """
                                    The cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.
                                    Default: 10""".indent(1))
                    .worldRestart()
                    .define("Altitude Punishment", 10.0);

            BUILDER.pop();
        }
    }

    public static class Segment {
        public final ForgeConfigSpec.ConfigValue<Double> segmentStepDistanceProportion;
        public final ForgeConfigSpec.ConfigValue<Double> hScalar;
        public final ForgeConfigSpec.ConfigValue<Double> pathScalar;
        public final ForgeConfigSpec.ConfigValue<Double> highSlopeFactorScalar;
        public final ForgeConfigSpec.ConfigValue<Double> lowSlopeFactorScalar;
        public final ForgeConfigSpec.ConfigValue<Double> slopeFactorThreshold;
        public final ForgeConfigSpec.ConfigValue<Double> altitudePunishment;

        public Segment(ForgeConfigSpec.Builder BUILDER) {
            BUILDER
                    .comment(
                            """
                                    ##########################################################################################################
                                    # Advanced Segment settings.
                                    ##########################################################################################################""")
                    .push("Segment");

            segmentStepDistanceProportion = BUILDER
                    .comment(
                            """
                                    The proportion of the straight-line path length to use as the segment step distance.
                                    Default: 0.1""".indent(1))
                    .worldRestart()
                    .define("Segment Step Distance Proportion", 0.1);

            hScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the H function (distance) of the path's shape
                                    when determining segment endpoints.
                                    Default: 10""".indent(1))
                    .worldRestart()
                    .define("H Scalar", 10.0);

            pathScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the path's length, in terms of nodes.
                                    Increasing this value will make the paths straighter and more direct.
                                    Default: 10.0""".indent(1))
                    .worldRestart()
                    .define("Path Scalar", 10.0);

            highSlopeFactorScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the slope factor of the path's shape.
                                    Increasing this value will make the paths flatter, I think.
                                    Default: 10""".indent(1))
                    .worldRestart()
                    .define("High Slope Factor Scalar", 10.0);

            lowSlopeFactorScalar = BUILDER
                    .comment(
                            """
                                    The scalar to apply to the slope factor of the path's shape.
                                    Increasing this value will make the paths flatter, I think.
                                    Default: 2""".indent(1))
                    .worldRestart()
                    .define("Low Slope Factor Scalar", 2.0);

            slopeFactorThreshold = BUILDER
                    .comment(
                            """
                                    The PV threshold between low and high slope factors.
                                    Default: 0.0""".indent(1))
                    .worldRestart()
                    .define("Slope Factor Threshold", 0.0);

            altitudePunishment = BUILDER
                    .comment(
                            """
                                    The cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.
                                    Default: 10""".indent(1))
                    .worldRestart()
                    .define("Altitude Punishment", 10.0);

            BUILDER.pop();
        }
    }
}