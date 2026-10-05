package com.yungnickyoung.minecraft.yungsroads.module;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

public class ConfigModule {
    public General general = new General();
    public Advanced advanced = new Advanced();
    public Debug debug = new Debug();

    public static class General {
        public String structuresString = "[#minecraft:village]";
        public HolderSet<Structure> structures; // Evaluated at runtime, after registries are loaded
    }

    public static class Debug {
        public boolean enableExtraDebugF3Info = false;
        public boolean placeRoads = true;
        public boolean placeUnjitteredPosDebugMarkers = false;
        public boolean placeJitteredPosDebugMarkers = false;
        public boolean placeRoadEndpointDebugMarkers = false;
        public boolean placeStraightDebugLine = false;
        public boolean placeDebugPaths = false;

        public Debug copy() {
            Debug copy = new Debug();
            copy.enableExtraDebugF3Info = this.enableExtraDebugF3Info;
            copy.placeRoads = this.placeRoads;
            copy.placeUnjitteredPosDebugMarkers = this.placeUnjitteredPosDebugMarkers;
            copy.placeJitteredPosDebugMarkers = this.placeJitteredPosDebugMarkers;
            copy.placeRoadEndpointDebugMarkers = this.placeRoadEndpointDebugMarkers;
            copy.placeStraightDebugLine = this.placeStraightDebugLine;
            copy.placeDebugPaths = this.placeDebugPaths;
            return copy;
        }
    }

    /**
     * Road routing and shaping settings. See {@link AdvancedSetting} for their descriptions and valid ranges.
     */
    public static class Advanced {
        public int nodeStepDistance = 8;
        public double jitterAmount = 4;
        public double heuristicWeight = 1.2;
        public double slopeWeight = 25;
        public double maxGrade = 1.0;
        public double waterWeight = 8;
        public int maxBridgeLength = 32;
        public int smoothingRadius = 6;
        public int maxCutDepth = 4;
        public int maxFillDepth = 2;
        public int maxLandBridgeLength = 24;
        public double landBridgeEdgeRoughness = 0;
        public double landBridgeDecay = 0;

        public Advanced copy() {
            Advanced copy = new Advanced();
            for (AdvancedSetting setting : AdvancedSetting.values()) {
                setting.set(copy, setting.get(this));
            }
            return copy;
        }

        /** Whether every setting has the same value as in the other instance. */
        public boolean sameAs(Advanced other) {
            for (AdvancedSetting setting : AdvancedSetting.values()) {
                if (setting.get(this) != setting.get(other)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The settings in {@link Advanced}, with their valid ranges.
     * Shared by the loader configs and the in-game tuning screen so the ranges are defined in one place.
     */
    public enum AdvancedSetting {
        NODE_STEP_DISTANCE(Group.ROUTING, "Node Step Distance", 2, 32, true, 1,
                "The distance between neighboring nodes, in blocks. Lower values follow the terrain more closely, but are slower to generate.",
                advanced -> advanced.nodeStepDistance, (advanced, value) -> advanced.nodeStepDistance = (int) value),
        JITTER_AMOUNT(Group.ROUTING, "Jitter Amount", 0, 16, false, 1,
                "How far jitter may shift each node sideways, in blocks. Higher values make roads wavier.",
                advanced -> advanced.jitterAmount, (advanced, value) -> advanced.jitterAmount = value),
        HEURISTIC_WEIGHT(Group.ROUTING, "Heuristic Weight", 1, 10, false, 2,
                "How strongly routing is pulled toward the destination, by weighting the distance left in each node's priority. 1 always finds the cheapest road. Higher values generate faster but can give slightly costlier roads.",
                advanced -> advanced.heuristicWeight, (advanced, value) -> advanced.heuristicWeight = value),
        SLOPE_WEIGHT(Group.ROUTING, "Slope Weight", 0, 1000, false, 3,
                "The extra cost of steep terrain. Each step cost is multiplied by (1 + Slope Weight × grade²). Higher values make roads avoid hills and mountains more.",
                advanced -> advanced.slopeWeight, (advanced, value) -> advanced.slopeWeight = value),
        MAX_GRADE(Group.ROUTING, "Max Grade", 0.05, 10, false, 2,
                "The steepest grade a step may have. Steeper terrain is never crossed. 1 is a 45 degree slope.",
                advanced -> advanced.maxGrade, (advanced, value) -> advanced.maxGrade = value),
        WATER_WEIGHT(Group.ROUTING, "Water Weight", 0, 1000, false, 3,
                "The extra cost of bridging water. Added to the multiplier in each bridge's cost. Higher values make roads detour further to avoid rivers and lakes, or to find a shorter crossing.",
                advanced -> advanced.waterWeight, (advanced, value) -> advanced.waterWeight = value),
        MAX_BRIDGE_LENGTH(Group.ROUTING, "Max Bridge Length", 0, 256, true, 1,
                "The longest bridge a road may build, in blocks. Roads cross rivers and lakes only on straight bridges, so wider water must be routed around. 0 disables bridges. Oceans are never crossed.",
                advanced -> advanced.maxBridgeLength, (advanced, value) -> advanced.maxBridgeLength = (int) value),
        SMOOTHING_RADIUS(Group.SHAPING, "Smoothing Radius", 0, 32, true, 1,
                "How far along the road its height is averaged, in blocks. Higher values give gentler slopes, with more cutting and filling to level the ground. 0 follows the terrain.",
                advanced -> advanced.smoothingRadius, (advanced, value) -> advanced.smoothingRadius = (int) value),
        MAX_CUT_DEPTH(Group.SHAPING, "Max Cut Depth", 0, 16, true, 1,
                "The deepest a road may cut into the ground above it to stay level, in blocks. Where the ground rises higher, the road rises too.",
                advanced -> advanced.maxCutDepth, (advanced, value) -> advanced.maxCutDepth = (int) value),
        MAX_FILL_DEPTH(Group.SHAPING, "Max Fill Depth", 0, 16, true, 1,
                "The deepest gap under a road that's filled with ground, in blocks. Deeper gaps, such as ravines and cave openings, are crossed on a land bridge instead.",
                advanced -> advanced.maxFillDepth, (advanced, value) -> advanced.maxFillDepth = (int) value),
        MAX_LAND_BRIDGE_LENGTH(Group.SHAPING, "Max Land Bridge Length", 0, 64, true, 1,
                "The longest dip in the terrain that a road crosses on a land bridge, in blocks, if it's deeper than Max Fill Depth. Longer dips are followed instead. Ravines and caves made by carvers always get a land bridge, since routing can't see them.",
                advanced -> advanced.maxLandBridgeLength, (advanced, value) -> advanced.maxLandBridgeLength = (int) value),
        LAND_BRIDGE_EDGE_ROUGHNESS(Group.SHAPING, "Land Bridge Edge Roughness", 0, 1, false, 1,
                "How ragged the edges of land bridges are. 0 gives straight edges. Higher values move each edge block up to a block in or out.",
                advanced -> advanced.landBridgeEdgeRoughness, (advanced, value) -> advanced.landBridgeEdgeRoughness = value),
        LAND_BRIDGE_DECAY(Group.SHAPING, "Land Bridge Decay", 0, 1, false, 1,
                "How much of each land bridge's deck is missing, for a ruined look. Blocks are likelier to be missing toward the edges, and the center line is always kept. 0 keeps the whole deck.",
                advanced -> advanced.landBridgeDecay, (advanced, value) -> advanced.landBridgeDecay = value);

        /** Which part of road generation a setting tunes. */
        public enum Group {
            /** Where roads go. */
            ROUTING,
            /** How roads fit the terrain along their route. */
            SHAPING
        }

        public final Group group;
        public final String displayName;
        public final double min;
        public final double max;
        public final boolean isInteger;
        /**
         * Shapes the in-game slider, which maps its position t in [0, 1] to min + (max - min) * t^sliderExponent.
         * Values above 1 give more of the slider to the low end, for wide ranges whose useful values are small.
         */
        public final double sliderExponent;
        public final String description;
        private final ToDoubleFunction<Advanced> getter;
        private final ObjDoubleConsumer<Advanced> setter;

        AdvancedSetting(Group group, String displayName, double min, double max, boolean isInteger, double sliderExponent, String description,
                        ToDoubleFunction<Advanced> getter, ObjDoubleConsumer<Advanced> setter) {
            this.group = group;
            this.displayName = displayName;
            this.min = min;
            this.max = max;
            this.isInteger = isInteger;
            this.sliderExponent = sliderExponent;
            this.description = description;
            this.getter = getter;
            this.setter = setter;
        }

        public double get(Advanced advanced) {
            return this.getter.applyAsDouble(advanced);
        }

        public void set(Advanced advanced, double value) {
            this.setter.accept(advanced, value);
        }

        public double defaultValue() {
            return get(new Advanced());
        }

        public double clamp(double value) {
            return Math.max(this.min, Math.min(this.max, value));
        }

        public boolean isValid(double value) {
            return value >= this.min && value <= this.max && (!this.isInteger || value == Math.rint(value));
        }

        /** Formats a value for display, without a trailing ".0" for whole numbers. */
        public String format(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }
}
