package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * The road types a level's roads are generated with, and how each road's type is chosen.
 * <p>
 * The types are loaded from the datapack registry when the level is created. The tuning screen may replace them with
 * edited copies while the level runs. Thread-safe: the types are swapped as a whole, and never changed once in use.
 */
public final class RoadTypes {
    public static final ResourceKey<Registry<RoadType>> REGISTRY_KEY = ResourceKey.createRegistryKey(YungsRoadsCommon.id("road_type"));

    /** The road type chosen wherever no other type's selection matches. Must always exist. */
    public static final ResourceLocation DEFAULT_ID = YungsRoadsCommon.id("default");

    /** How far apart the biomes along a road are sampled to choose its type, in blocks. */
    private static final int BIOME_SAMPLE_SPACING = 64;

    /** The types as loaded from the registry, by id. */
    private final SortedMap<ResourceLocation, RoadType> loaded;

    /** The types in use, by id. Either the loaded types or ones applied from the tuning screen. */
    private volatile SortedMap<ResourceLocation, RoadType> current;

    /** Types roads were saved with that no longer exist, so each is only warned about once. */
    private final Set<ResourceLocation> warnedMissing = ConcurrentHashMap.newKeySet();

    public RoadTypes(RegistryAccess registryAccess) {
        SortedMap<ResourceLocation, RoadType> types = new TreeMap<>();
        registryAccess.registry(REGISTRY_KEY).ifPresent(registry ->
                registry.entrySet().forEach(entry -> types.put(entry.getKey().location(), entry.getValue())));
        if (!types.containsKey(DEFAULT_ID)) {
            YungsRoadsCommon.LOGGER.error("The default road type {} is missing. Roads not matching another road type will use built-in settings.", DEFAULT_ID);
            types.put(DEFAULT_ID, new RoadType(Optional.empty(), List.of(new RoadType.Variant(1, new RoadSettings(RoadBlocks.FALLBACK)))));
        }
        this.loaded = Collections.unmodifiableSortedMap(types);
        this.current = this.loaded;
    }

    /** The types as loaded from the registry, by id. */
    public SortedMap<ResourceLocation, RoadType> loaded() {
        return this.loaded;
    }

    /** The types in use, by id. Mustn't be changed. */
    public SortedMap<ResourceLocation, RoadType> current() {
        return this.current;
    }

    /**
     * Replaces the types in use, such as with ones edited on the tuning screen. The given types are copied, so they can
     * still be changed afterward without affecting generation.
     */
    public void set(Map<ResourceLocation, RoadType> types) {
        SortedMap<ResourceLocation, RoadType> copy = new TreeMap<>();
        types.forEach((id, type) -> copy.put(id, type.copy()));
        this.current = Collections.unmodifiableSortedMap(copy);
    }

    /**
     * The settings of a road type's variant. A road saved with a type or variant that no longer exists uses the
     * default type's first variant.
     */
    public RoadSettings settings(ResourceLocation typeId, int variant) {
        RoadType type = this.current.get(typeId);
        if (type == null || variant < 0 || variant >= type.variants().size()) {
            if (this.warnedMissing.add(typeId)) {
                YungsRoadsCommon.LOGGER.warn("Road type {} variant {} doesn't exist. Roads using it will use the default road type.", typeId, variant);
            }
            return this.current.get(DEFAULT_ID).variants().get(0).settings();
        }
        return type.variants().get(variant).settings();
    }

    /**
     * Chooses the road type for a road between two positions, by sampling the biomes along the straight line between
     * them. Each sample votes for the types whose selection matches its biome with the highest priority, or for the
     * default type if none match. The type with the most votes wins, with ties going to the higher priority and then
     * the lower id. If the type has several variants, one is picked at random by weight, seeded by the world seed and
     * the road's endpoints, so the same road always gets the same variant.
     * <p>
     * The result doesn't depend on which endpoint is given first.
     *
     * @param biomeAt The biome at the surface of a block column, given its x and z.
     */
    public Choice choose(BlockPos a, BlockPos b, long worldSeed, BiFunction<Integer, Integer, Holder<Biome>> biomeAt) {
        SortedMap<ResourceLocation, RoadType> types = this.current;
        double length = Math.sqrt(Math.pow(b.getX() - a.getX(), 2) + Math.pow(b.getZ() - a.getZ(), 2));
        int samples = Math.max(2, (int) Math.ceil(length / BIOME_SAMPLE_SPACING) + 1);

        Map<ResourceLocation, Integer> votes = new HashMap<>();
        for (int s = 0; s < samples; s++) {
            double t = s / (double) (samples - 1);
            int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
            int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
            for (ResourceLocation typeId : matchingTypes(types, biomeAt.apply(x, z))) {
                votes.merge(typeId, 1, Integer::sum);
            }
        }

        List<Vote> ranked = new ArrayList<>();
        votes.forEach((typeId, count) -> ranked.add(new Vote(typeId, count)));
        ranked.sort(Comparator.comparingInt(Vote::votes).reversed()
                .thenComparing(Comparator.comparingInt((Vote vote) -> types.get(vote.typeId).priority()).reversed())
                .thenComparing(Vote::typeId));

        ResourceLocation typeId = ranked.get(0).typeId;
        RoadType type = types.get(typeId);
        int variant = pickVariant(type, worldSeed, a, b);
        return new Choice(typeId, variant, type.variants().get(variant).settings(), List.copyOf(ranked));
    }

    /**
     * The types a biome votes for: those whose selection matches it with the highest priority, or the default type if
     * none match.
     */
    private static List<ResourceLocation> matchingTypes(SortedMap<ResourceLocation, RoadType> types, Holder<Biome> biome) {
        List<ResourceLocation> matching = new ArrayList<>();
        int bestPriority = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, RoadType> entry : types.entrySet()) {
            Optional<RoadType.Selection> selection = entry.getValue().selection();
            if (selection.isEmpty() || !selection.get().biomes().contains(biome)) {
                continue;
            }
            int priority = selection.get().priority();
            if (matching.isEmpty() || priority > bestPriority) {
                matching.clear();
                bestPriority = priority;
            }
            if (priority == bestPriority) {
                matching.add(entry.getKey());
            }
        }
        return matching.isEmpty() ? List.of(DEFAULT_ID) : matching;
    }

    private static int pickVariant(RoadType type, long worldSeed, BlockPos a, BlockPos b) {
        if (type.variants().size() == 1) {
            return 0;
        }
        // Order the endpoints so either order gives the same seed
        long first = Math.min(a.asLong(), b.asLong());
        long second = Math.max(a.asLong(), b.asLong());
        int totalWeight = type.variants().stream().mapToInt(RoadType.Variant::weight).sum();
        int roll = new XoroshiroRandomSource(worldSeed ^ (first * 0x9E3779B97F4A7C15L + second)).nextInt(totalWeight);
        for (int i = 0; i < type.variants().size(); i++) {
            roll -= type.variants().get(i).weight();
            if (roll < 0) {
                return i;
            }
        }
        return type.variants().size() - 1;
    }

    /**
     * The road type chosen for a road.
     *
     * @param votes How many biome samples voted for each type, most first, in the order ties were broken.
     */
    public record Choice(ResourceLocation typeId, int variant, RoadSettings settings, List<Vote> votes) {
    }

    /** How many of a road's biome samples voted for a road type. */
    public record Vote(ResourceLocation typeId, int votes) {
    }
}
