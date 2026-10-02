package com.yungnickyoung.minecraft.yungsroads.module;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.structure.Structure;

public class ConfigModule {
    public General general = new General();
    public Advanced advanced = new Advanced();
    public Debug debug = new Debug();

    public static class General {
        public String structuresString = "[#minecraft:village]";
        public HolderSet<Structure> structures; // Evaluated at runtime, after registries are loaded
    }

    public static class Debug {
        public boolean enableDebugMap = false;
        public boolean enableExtraDebugF3Info = false;
        public boolean placeUnjitteredPosDebugMarkers = false;
        public boolean placeJitteredPosDebugMarkers = false;
        public boolean placeRoadEndpointDebugMarkers = false;
        public boolean placeStraightDebugLine = false;
        public boolean placeDebugPaths = false;
    }

    public static class Advanced {
        public int nodeStepDistance = 8; // Distance between pathfinding nodes, in blocks
        public double jitterAmount = 4; // Max noise-based sideways offset applied to the road's shape, in blocks
        public double heuristicWeight = 1.2; // Values above 1 speed up pathfinding at the cost of slightly less optimal roads
        public double slopeWeight = 25; // Extra cost per unit of grade squared. Higher values make roads avoid hills more.
        public double maxGrade = 1.0; // Steepest allowed rise over run between two nodes. 1.0 is 45 degrees.
        public double waterWeight = 8; // Extra cost multiplier for crossing water. Higher values make roads avoid rivers and lakes more.
    }
}
