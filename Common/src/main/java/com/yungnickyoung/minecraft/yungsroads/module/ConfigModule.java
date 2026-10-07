package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
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

        public Debug copy() {
            Debug copy = new Debug();
            copy.enableExtraDebugF3Info = this.enableExtraDebugF3Info;
            copy.placeRoads = this.placeRoads;
            return copy;
        }
    }

    /**
     * Road settings shared by every road type. See {@link GlobalSetting} for their valid ranges. Each road type's own
     * settings are in its file, in the {@code yungsroads/road_type} datapack registry.
     */
    public static class Advanced {
        public int nodeStepDistance = 8;
        public double heuristicWeight = 1.2;

        public Advanced copy() {
            Advanced copy = new Advanced();
            for (GlobalSetting setting : GlobalSetting.values()) {
                setting.set(copy, setting.get(this));
            }
            return copy;
        }

        /** Whether every setting has the same value as in the other instance. */
        public boolean sameAs(Advanced other) {
            for (GlobalSetting setting : GlobalSetting.values()) {
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
    public enum GlobalSetting implements ITunableSetting {
        NODE_STEP_DISTANCE("node_step_distance", 2, 32, true, 1,
                advanced -> advanced.nodeStepDistance, (advanced, value) -> advanced.nodeStepDistance = (int) value),
        HEURISTIC_WEIGHT("heuristic_weight", 1, 10, false, 2,
                advanced -> advanced.heuristicWeight, (advanced, value) -> advanced.heuristicWeight = value);

        private final String key;
        private final double min;
        private final double max;
        private final boolean isInteger;
        private final double sliderExponent;
        private final ToDoubleFunction<Advanced> getter;
        private final ObjDoubleConsumer<Advanced> setter;

        GlobalSetting(String key, double min, double max, boolean isInteger, double sliderExponent,
                      ToDoubleFunction<Advanced> getter, ObjDoubleConsumer<Advanced> setter) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.isInteger = isInteger;
            this.sliderExponent = sliderExponent;
            this.getter = getter;
            this.setter = setter;
        }

        public double get(Advanced advanced) {
            return this.getter.applyAsDouble(advanced);
        }

        public void set(Advanced advanced, double value) {
            this.setter.accept(advanced, value);
        }

        @Override
        public Group group() {
            return Group.ROUTING;
        }

        @Override
        public String key() {
            return this.key;
        }

        @Override
        public double min() {
            return this.min;
        }

        @Override
        public double max() {
            return this.max;
        }

        @Override
        public boolean isInteger() {
            return this.isInteger;
        }

        @Override
        public boolean isToggle() {
            return false;
        }

        @Override
        public double sliderExponent() {
            return this.sliderExponent;
        }

        @Override
        public double defaultValue() {
            return get(new Advanced());
        }
    }
}
