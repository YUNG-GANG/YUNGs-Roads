package com.yungnickyoung.minecraft.yungsroads.module;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;

public class ConfigModule {
    public General general = new General();
    public Advanced advanced = new Advanced();
    public Debug debug = new Debug();

    public static class General {
        public String structuresString = "[#minecraft:village]";
        public HolderSet<ConfiguredStructureFeature<?, ?>> structures; // Evaluated at runtime, after registries are loaded
    }

    public static class Debug {
        public boolean enableDebugMap = false;
        public boolean enableExtraDebugF3Info = false;
        public boolean placeUnjitteredPosDebugMarkers = false;
        public boolean placeJitteredPosDebugMarkers = false;
        public boolean placeRoadEndpointDebugMarkers = false;
        public boolean placeRoadSegmentEndpointDebugMarkers = false;
        public boolean placeStraightDebugLine = false;
        public boolean placeDebugPaths = false;
    }

    public static class Advanced {

        public final Path path = new Path();
        public final Segment segment = new Segment();

        public static class Path {
            public int nodeStepDistance = 8;
            public double jitterAmount = 4;
            public double hScalar = 10;
            public double pathScalar = 3; // Increasing this value will make the paths straighter and more direct.
            public double slopeFactorThreshold = -1.0; // The PV threshold between low and high slope factors
            public double highSlopeFactorScalar = 10; // Increasing this value will make the paths flatter
            public double lowSlopeFactorScalar = 2; // Increasing this value will make the paths flatter
            public double altitudePunishmentScalar = 2; // Cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.
        }

        public static class Segment {
            public double nodeStepDistanceProportion = 0.05;
            public double hScalar = 10;
            public double pathScalar = 3; // Increasing this value will make the paths straighter and more direct.
            public double slopeFactorThreshold = -1.0; // The PV threshold between low and high slope factors
            public double highSlopeFactorScalar = 10; // Increasing this value will make the paths flatter
            public double lowSlopeFactorScalar = 2; // Increasing this value will make the paths flatter
            public double altitudePunishmentScalar = 2; // Cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.

        }
    }
}
