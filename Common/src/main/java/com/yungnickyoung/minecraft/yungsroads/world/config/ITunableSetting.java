package com.yungnickyoung.minecraft.yungsroads.world.config;

/**
 * A numeric or on/off road setting, with its valid range. Shared by the config files, the road type codecs, the
 * in-game tuning screen, and the generated docs, so each setting's range is defined in one place.
 * <p>
 * Each setting's display name and description are lang entries, keyed by {@link #nameKey} and {@link #descriptionKey}.
 */
public interface ITunableSetting {
    /** Which part of road generation a setting tunes. */
    enum Group {
        /** Where roads go. */
        ROUTING,
        /** How roads fit the terrain along their route. */
        SHAPING
    }

    Group group();

    /** The setting's name in road type files, in snake case. Also identifies its lang entries. */
    String key();

    double min();

    double max();

    boolean isInteger();

    /** Whether the setting is either on or off, stored as 1 or 0, rather than a number. */
    boolean isToggle();

    /**
     * Shapes the in-game slider, which maps its position t in [0, 1] to min + (max - min) * t^sliderExponent.
     * Values above 1 give more of the slider to the low end, for wide ranges whose useful values are small.
     */
    double sliderExponent();

    double defaultValue();

    default String nameKey() {
        return "yungsroads.setting." + key();
    }

    default String descriptionKey() {
        return nameKey() + ".description";
    }

    default double clamp(double value) {
        return Math.max(min(), Math.min(max(), value));
    }

    default boolean isValid(double value) {
        return value >= min() && value <= max() && (!isInteger() || value == Math.rint(value));
    }

    /** Formats a value for display, without a trailing ".0" for whole numbers. */
    default String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
