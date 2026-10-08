package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The settings a road of one road type, or one of its variants, is generated with: how it's routed, how it's shaped to
 * the terrain, and what it's built from. See {@link RoadSetting} for the numeric settings' valid ranges.
 * <p>
 * Mutable so the tuning screen can edit copies. Settings in use by generation are never changed.
 */
public final class RoadSettings {
    /**
     * In a road type file, the numeric settings are grouped as on the tuning screen's tabs. A group or setting that's
     * left out takes its default value.
     */
    public static final Codec<RoadSettings> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    groupField(ITunableSetting.Group.ROUTING, "routing").forGetter(settings -> settings.values(ITunableSetting.Group.ROUTING)),
                    groupField(ITunableSetting.Group.SHAPING, "shaping").forGetter(settings -> settings.values(ITunableSetting.Group.SHAPING)),
                    RoadBlocks.CODEC.fieldOf("blocks").forGetter(settings -> settings.blocks))
            .apply(instance, RoadSettings::new));

    // Routing
    public double jitterAmount = 4;
    public double slopeWeight = 25;
    public double freeGrade = 0.15;
    public boolean straightenRoutes = true;
    public double maxGrade = 1.0;
    public double waterWeight = 8;
    public int maxBridgeLength = 32;

    // Shaping. The width defaults match the roads placed before width was a setting, when it was set per surface.
    public double roadWidth = 3.2;
    public double widthVariation = 1.4;
    public int smoothingRadius = 6;
    public int maxCutDepth = 4;
    public int maxFillDepth = 2;
    public int maxLandBridgeLength = 24;
    public double landBridgeEdgeRoughness = 0;
    public double landBridgeDecay = 0;
    public double landBridgeSag = 0.1;
    public int landBridgeRailingDrop = 4;
    public int landBridgeRailingChance = 100;

    public final RoadBlocks blocks;

    /** Settings with every numeric setting at its default value. */
    public RoadSettings(RoadBlocks blocks) {
        this.blocks = blocks;
    }

    private RoadSettings(Map<RoadSetting, Double> routing, Map<RoadSetting, Double> shaping, RoadBlocks blocks) {
        this(blocks);
        routing.forEach((setting, value) -> setting.set(this, value));
        shaping.forEach((setting, value) -> setting.set(this, value));
    }

    public RoadSettings copy() {
        RoadSettings copy = new RoadSettings(this.blocks);
        for (RoadSetting setting : RoadSetting.values()) {
            setting.set(copy, setting.get(this));
        }
        return copy;
    }

    /** Whether every setting has the same value as in the other instance. */
    public boolean sameAs(RoadSettings other) {
        for (RoadSetting setting : RoadSetting.values()) {
            if (setting.get(this) != setting.get(other)) {
                return false;
            }
        }
        return this.blocks.equals(other.blocks);
    }

    /**
     * The distance from the road's center line to its edge, exclusive, at a point along it.
     *
     * @param widthNoise From 0 to 1, how far the road is widened there by its width variation.
     */
    public double halfWidth(double widthNoise) {
        return (this.roadWidth + this.widthVariation * widthNoise) / 2;
    }

    /** The distance from the road's center line to its edge, exclusive, where the road is widest. */
    public double maxHalfWidth() {
        return halfWidth(1);
    }

    private Map<RoadSetting, Double> values(ITunableSetting.Group group) {
        Map<RoadSetting, Double> values = new EnumMap<>(RoadSetting.class);
        for (RoadSetting setting : RoadSetting.values()) {
            if (setting.group() == group) {
                values.put(setting, setting.get(this));
            }
        }
        return values;
    }

    /**
     * A group of numeric settings, which is always written out in full so a road type file shows every setting it
     * could change.
     */
    private static MapCodec<Map<RoadSetting, Double>> groupField(ITunableSetting.Group group, String name) {
        return Codec.optionalField(name, new GroupCodec(group).codec(), false)
                .xmap(values -> values.orElseGet(() -> new GroupCodec(group).defaults()), Optional::of);
    }

    /**
     * The numeric settings in one group, each written as a number, or as true or false for a toggle. Unknown names are
     * errors, so a misspelled setting doesn't silently keep its default.
     */
    private static final class GroupCodec extends MapCodec<Map<RoadSetting, Double>> {
        private final List<RoadSetting> settings = new ArrayList<>();

        GroupCodec(ITunableSetting.Group group) {
            for (RoadSetting setting : RoadSetting.values()) {
                if (setting.group() == group) {
                    this.settings.add(setting);
                }
            }
        }

        Map<RoadSetting, Double> defaults() {
            Map<RoadSetting, Double> values = new EnumMap<>(RoadSetting.class);
            this.settings.forEach(setting -> values.put(setting, setting.defaultValue()));
            return values;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return this.settings.stream().map(setting -> ops.createString(setting.key()));
        }

        @Override
        public <T> DataResult<Map<RoadSetting, Double>> decode(DynamicOps<T> ops, MapLike<T> input) {
            List<String> unknown = input.entries()
                    .map(entry -> ops.getStringValue(entry.getFirst()).result().orElse("?"))
                    .filter(key -> this.settings.stream().noneMatch(setting -> setting.key().equals(key)))
                    .toList();
            if (!unknown.isEmpty()) {
                return DataResult.error(() -> "Unknown settings " + unknown + ". Valid settings are "
                        + this.settings.stream().map(RoadSetting::key).toList());
            }

            Map<RoadSetting, Double> values = defaults();
            for (RoadSetting setting : this.settings) {
                T element = input.get(setting.key());
                if (element == null) {
                    continue;
                }
                DataResult<Double> parsed = setting.isToggle()
                        ? Codec.BOOL.parse(ops, element).map(on -> on ? 1.0 : 0.0)
                        : Codec.DOUBLE.parse(ops, element);
                if (parsed.error().isPresent()) {
                    return DataResult.error(() -> setting.key() + ": " + parsed.error().get().message());
                }
                double value = parsed.getOrThrow();
                if (!setting.isValid(value)) {
                    return DataResult.error(() -> setting.key() + " must be " + (setting.isInteger() ? "a whole number " : "")
                            + "from " + setting.format(setting.min()) + " to " + setting.format(setting.max()) + ", but was " + value);
                }
                values.put(setting, value);
            }
            return DataResult.success(values);
        }

        @Override
        public <T> RecordBuilder<T> encode(Map<RoadSetting, Double> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (RoadSetting setting : this.settings) {
                double value = input.getOrDefault(setting, setting.defaultValue());
                T element = setting.isToggle() ? ops.createBoolean(value != 0)
                        : setting.isInteger() ? ops.createInt((int) value)
                        : ops.createDouble(value);
                prefix.add(setting.key(), element);
            }
            return prefix;
        }
    }
}
