package com.yungnickyoung.minecraft.yungsroads.world.config;

import java.util.function.BiConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * The numeric and on/off settings in {@link RoadSettings}, with their valid ranges. Each road type sets its own.
 */
public enum RoadSetting implements ITunableSetting {
    JITTER_AMOUNT(Group.ROUTING, "jitter_amount", 0, 16, false, 1,
            settings -> settings.jitterAmount, (settings, value) -> settings.jitterAmount = value),
    SLOPE_WEIGHT(Group.ROUTING, "slope_weight", 0, 1000, false, 3,
            settings -> settings.slopeWeight, (settings, value) -> settings.slopeWeight = value),
    FREE_GRADE(Group.ROUTING, "free_grade", 0, 1, false, 2,
            settings -> settings.freeGrade, (settings, value) -> settings.freeGrade = value),
    STRAIGHTEN_ROUTES(Group.ROUTING, "straighten_routes",
            settings -> settings.straightenRoutes, (settings, value) -> settings.straightenRoutes = value),
    MAX_GRADE(Group.ROUTING, "max_grade", 0.05, 10, false, 2,
            settings -> settings.maxGrade, (settings, value) -> settings.maxGrade = value),
    WATER_WEIGHT(Group.ROUTING, "water_weight", 0, 1000, false, 3,
            settings -> settings.waterWeight, (settings, value) -> settings.waterWeight = value),
    MAX_BRIDGE_LENGTH(Group.ROUTING, "max_bridge_length", 0, 256, true, 1,
            settings -> settings.maxBridgeLength, (settings, value) -> settings.maxBridgeLength = (int) value),
    // The widest road, at both maximums, must leave placement's reads within one chunk. See AbstractRoadGenerator.
    ROAD_WIDTH(Group.SHAPING, "road_width", 1, 8, false, 1,
            settings -> settings.roadWidth, (settings, value) -> settings.roadWidth = value),
    WIDTH_VARIATION(Group.SHAPING, "width_variation", 0, 4, false, 1,
            settings -> settings.widthVariation, (settings, value) -> settings.widthVariation = value),
    SMOOTHING_RADIUS(Group.SHAPING, "smoothing_radius", 0, 32, true, 1,
            settings -> settings.smoothingRadius, (settings, value) -> settings.smoothingRadius = (int) value),
    MAX_CUT_DEPTH(Group.SHAPING, "max_cut_depth", 0, 16, true, 1,
            settings -> settings.maxCutDepth, (settings, value) -> settings.maxCutDepth = (int) value),
    MAX_FILL_DEPTH(Group.SHAPING, "max_fill_depth", 0, 16, true, 1,
            settings -> settings.maxFillDepth, (settings, value) -> settings.maxFillDepth = (int) value),
    MAX_LAND_BRIDGE_LENGTH(Group.SHAPING, "max_land_bridge_length", 0, 64, true, 1,
            settings -> settings.maxLandBridgeLength, (settings, value) -> settings.maxLandBridgeLength = (int) value),
    LAND_BRIDGE_EDGE_ROUGHNESS(Group.SHAPING, "land_bridge_edge_roughness", 0, 1, false, 1,
            settings -> settings.landBridgeEdgeRoughness, (settings, value) -> settings.landBridgeEdgeRoughness = value),
    LAND_BRIDGE_DECAY(Group.SHAPING, "land_bridge_decay", 0, 1, false, 1,
            settings -> settings.landBridgeDecay, (settings, value) -> settings.landBridgeDecay = value),
    LAND_BRIDGE_SAG(Group.SHAPING, "land_bridge_sag", 0, 0.25, false, 2,
            settings -> settings.landBridgeSag, (settings, value) -> settings.landBridgeSag = value),
    LAND_BRIDGE_RAILING_DROP(Group.SHAPING, "land_bridge_railing_drop", 1, 64, true, 1,
            settings -> settings.landBridgeRailingDrop, (settings, value) -> settings.landBridgeRailingDrop = (int) value),
    LAND_BRIDGE_RAILING_CHANCE(Group.SHAPING, "land_bridge_railing_chance", 0, 100, true, 1,
            settings -> settings.landBridgeRailingChance, (settings, value) -> settings.landBridgeRailingChance = (int) value);

    private final Group group;
    private final String key;
    private final double min;
    private final double max;
    private final boolean isInteger;
    private final boolean isToggle;
    private final double sliderExponent;
    private final ToDoubleFunction<RoadSettings> getter;
    private final ObjDoubleConsumer<RoadSettings> setter;

    RoadSetting(Group group, String key, double min, double max, boolean isInteger, double sliderExponent,
                ToDoubleFunction<RoadSettings> getter, ObjDoubleConsumer<RoadSettings> setter) {
        this(group, key, min, max, isInteger, false, sliderExponent, getter, setter);
    }

    /** A setting that's either on or off. */
    RoadSetting(Group group, String key, Predicate<RoadSettings> getter, BiConsumer<RoadSettings, Boolean> setter) {
        this(group, key, 0, 1, true, true, 1,
                settings -> getter.test(settings) ? 1 : 0, (settings, value) -> setter.accept(settings, value != 0));
    }

    RoadSetting(Group group, String key, double min, double max, boolean isInteger, boolean isToggle, double sliderExponent,
                ToDoubleFunction<RoadSettings> getter, ObjDoubleConsumer<RoadSettings> setter) {
        this.group = group;
        this.key = key;
        this.min = min;
        this.max = max;
        this.isInteger = isInteger;
        this.isToggle = isToggle;
        this.sliderExponent = sliderExponent;
        this.getter = getter;
        this.setter = setter;
    }

    public double get(RoadSettings settings) {
        return this.getter.applyAsDouble(settings);
    }

    public void set(RoadSettings settings, double value) {
        this.setter.accept(settings, value);
    }

    @Override
    public Group group() {
        return this.group;
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
        return this.isToggle;
    }

    @Override
    public double sliderExponent() {
        return this.sliderExponent;
    }

    @Override
    public double defaultValue() {
        // Only the numeric settings are read, so no blocks are needed
        return get(new RoadSettings(null));
    }
}
