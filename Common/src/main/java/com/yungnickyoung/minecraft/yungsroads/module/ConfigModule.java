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
     * Road routing settings. See {@link AdvancedSetting} for their descriptions and valid ranges.
     */
    public static class Advanced {
        public int nodeStepDistance = 8;
        public double jitterAmount = 4;
        public double heuristicWeight = 1.2;
        public double slopeWeight = 25;
        public double maxGrade = 1.0;
        public double waterWeight = 8;

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
     * The road routing settings in {@link Advanced}, with their valid ranges.
     * Shared by the loader configs and the in-game tuning screen so the ranges are defined in one place.
     */
    public enum AdvancedSetting {
        NODE_STEP_DISTANCE("Node Step Distance", 2, 32, true, 1,
                "The distance between neighboring nodes, in blocks. Lower values follow the terrain more closely, but are slower to generate.",
                advanced -> advanced.nodeStepDistance, (advanced, value) -> advanced.nodeStepDistance = (int) value),
        JITTER_AMOUNT("Jitter Amount", 0, 16, false, 1,
                "How far jitter may shift each node sideways, in blocks. Higher values make roads wavier.",
                advanced -> advanced.jitterAmount, (advanced, value) -> advanced.jitterAmount = value),
        HEURISTIC_WEIGHT("Heuristic Weight", 1, 10, false, 2,
                "How strongly routing is pulled toward the destination, by weighting the distance left in each node's priority. 1 always finds the cheapest road. Higher values generate faster but can give slightly costlier roads.",
                advanced -> advanced.heuristicWeight, (advanced, value) -> advanced.heuristicWeight = value),
        SLOPE_WEIGHT("Slope Weight", 0, 1000, false, 3,
                "The extra cost of steep terrain. Each step cost is multiplied by (1 + Slope Weight × grade²). Higher values make roads avoid hills and mountains more.",
                advanced -> advanced.slopeWeight, (advanced, value) -> advanced.slopeWeight = value),
        MAX_GRADE("Max Grade", 0.05, 10, false, 2,
                "The steepest grade a step may have. Steeper terrain is never crossed. 1 is a 45 degree slope.",
                advanced -> advanced.maxGrade, (advanced, value) -> advanced.maxGrade = value),
        WATER_WEIGHT("Water Weight", 0, 1000, false, 3,
                "The extra cost of crossing water. Added to the multiplier in each step cost over water. Higher values make roads avoid rivers and lakes more. Oceans are never crossed.",
                advanced -> advanced.waterWeight, (advanced, value) -> advanced.waterWeight = value);

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

        AdvancedSetting(String displayName, double min, double max, boolean isInteger, double sliderExponent, String description,
                        ToDoubleFunction<Advanced> getter, ObjDoubleConsumer<Advanced> setter) {
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
