package com.yungnickyoung.minecraft.yungsroads.module;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;

public class ConfigModule {
    public General general = new General();
    public Advanced advanced = new Advanced();

    public static class General {
        public String structuresString = "[#minecraft:village]";
        public HolderSet<ConfiguredStructureFeature<?, ?>> structures; // Evaluated at runtime, after registries are loaded
    }

    public static class Advanced {

        public final Path path = new Path();
        public final Segment segment = new Segment();

        public static class Path {
            public int nodeStepDistance = 2;
            public double jitterAmount = 4;
            public double hScalar = 10;
            public double pathScalar = 3; // Increasing this value will make the paths straighter and more direct.
            public double slopeFactorThreshold = 0.0; // The PV threshold between low and high slope factors
            public double highSlopeFactorScalar = 10; // Increasing this value will make the paths flatter
            public double lowSlopeFactorScalar = 2; // Increasing this value will make the paths flatter
            public double altitudePunishment = 10; // Cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.
        }

        public static class Segment {
            public double nodeStepDistanceProportion = 0.1;
            public double hScalar = 10;
            public double pathScalar = 10; // Increasing this value will make the paths straighter and more direct.
            public double slopeFactorThreshold = 0.0; // The PV threshold between low and high slope factors
            public double highSlopeFactorScalar = 10; // Increasing this value will make the paths flatter
            public double lowSlopeFactorScalar = 2; // Increasing this value will make the paths flatter
            public double altitudePunishment = 10; // Cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.

        }
    }
}
