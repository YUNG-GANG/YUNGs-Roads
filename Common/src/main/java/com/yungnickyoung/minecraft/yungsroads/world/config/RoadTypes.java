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

import javax.annotation.Nullable;
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
import java.util.stream.Collectors;

/**
 * The road types a level's roads are generated with, and how each road's type is chosen.
 * <p>
 * The level's {@link RoadNetwork} sets which types its roads may use, and which type is the default.
 * <p>
 * The types are loaded from the datapack registry when the level is created. The tuning screen may replace them with
 * edited copies while the level runs. Thread-safe: the types are swapped as a whole, and never changed once in use.
 */
public final class RoadTypes {
    public static final ResourceKey<Registry<RoadType>> REGISTRY_KEY = ResourceKey.createRegistryKey(YungsRoadsCommon.id("road_type"));

    /** The settings of roads whose road type no longer exists, in a level without a road network to say otherwise. */
    private static final RoadSettings FALLBACK_SETTINGS = new RoadSettings(RoadBlocks.FALLBACK);

    /** How far apart the biomes along a road are sampled to choose its type, in blocks. */
    private static final int BIOME_SAMPLE_SPACING = 64;

    /** The types as loaded from the registry, by id. */
    private final SortedMap<ResourceLocation, RoadType> loaded;

    /** The types in use, by id. Either the loaded types or ones applied from the tuning screen. */
    private volatile SortedMap<ResourceLocation, RoadType> current;

    /** The types roads may be chosen from, besides the default type. Empty if the level has no road network. */
    private final Set<ResourceLocation> allowed;

    /** The type chosen wherever no allowed type's selection matches, or null if the level has no road network. */
    @Nullable
    private final ResourceLocation defaultId;

    /** Types roads were saved with that no longer exist, so each is only warned about once. */
    private final Set<ResourceLocation> warnedMissing = ConcurrentHashMap.newKeySet();

    public RoadTypes(RegistryAccess registryAccess, Optional<RoadNetwork> network) {
        SortedMap<ResourceLocation, RoadType> types = new TreeMap<>();
        registryAccess.registry(REGISTRY_KEY).ifPresent(registry ->
                registry.entrySet().forEach(entry -> types.put(entry.getKey().location(), entry.getValue())));
        this.loaded = Collections.unmodifiableSortedMap(types);
        this.current = this.loaded;
        this.allowed = network
                .map(roadNetwork -> roadNetwork.roadTypes().stream().map(RoadTypes::idOf).collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
        this.defaultId = network.map(roadNetwork -> idOf(roadNetwork.defaultRoadType())).orElse(null);
    }

    private static ResourceLocation idOf(Holder<RoadType> holder) {
        return holder.unwrapKey().orElseThrow(() -> new IllegalStateException("Road type without an id: " + holder)).location();
    }

    /** The type chosen wherever no allowed type's selection matches, or null if the level has no road network. */
    @Nullable
    public ResourceLocation defaultId() {
        return this.defaultId;
    }

    /** Whether the level's roads can get the type: whether it's in its road network's types, or its default type. */
    public boolean isUsable(ResourceLocation typeId) {
        return this.allowed.contains(typeId) || typeId.equals(this.defaultId);
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
     * default type's first variant, or built-in settings if the level no longer has a road network.
     */
    public RoadSettings settings(ResourceLocation typeId, int variant) {
        RoadType type = this.current.get(typeId);
        if (type == null || variant < 0 || variant >= type.variants().size()) {
            if (this.warnedMissing.add(typeId)) {
                YungsRoadsCommon.LOGGER.warn("Road type {} variant {} doesn't exist. Roads using it will use the default road type.", typeId, variant);
            }
            RoadType defaultType = this.defaultId == null ? null : this.current.get(this.defaultId);
            return defaultType == null ? FALLBACK_SETTINGS : defaultType.variants().get(0).settings();
        }
        return type.variants().get(variant).settings();
    }

    /**
     * Chooses the road type for a road between two positions, by sampling the biomes along the straight line between
     * them. Each sample counts toward the allowed types whose selection matches its biome with the highest priority, or
     * toward the default type if none match. The type covering the most samples, the largest share of the route, wins,
     * with ties going to the higher priority and then the lower id. If the type has several variants, one is picked at random by weight, seeded by the world seed and
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

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int s = 0; s < samples; s++) {
            double t = s / (double) (samples - 1);
            int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
            int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
            for (ResourceLocation typeId : matchingTypes(types, this.allowed, this.defaultId, biomeAt.apply(x, z))) {
                counts.merge(typeId, 1, Integer::sum);
            }
        }

        List<RouteShare> shares = new ArrayList<>();
        counts.forEach((typeId, count) -> shares.add(new RouteShare(typeId, count)));
        shares.sort(Comparator.comparingInt(RouteShare::samples).reversed()
                .thenComparing(Comparator.comparingInt((RouteShare share) -> types.get(share.typeId).priority()).reversed())
                .thenComparing(RouteShare::typeId));

        ResourceLocation typeId = shares.get(0).typeId;
        RoadType type = types.get(typeId);
        int variant = pickVariant(type, worldSeed, a, b);
        return new Choice(typeId, variant, type.variants().get(variant).settings(), samples, List.copyOf(shares));
    }

    /**
     * The types a biome counts toward: the allowed ones whose selection matches it with the highest priority, or the
     * default type if none match.
     *
     * @param allowed The types a biome may count toward besides the default type.
     */
    private static List<ResourceLocation> matchingTypes(SortedMap<ResourceLocation, RoadType> types, Set<ResourceLocation> allowed,
                                                        ResourceLocation defaultId, Holder<Biome> biome) {
        List<ResourceLocation> matching = new ArrayList<>();
        int bestPriority = Integer.MIN_VALUE;
        for (Map.Entry<ResourceLocation, RoadType> entry : types.entrySet()) {
            if (!allowed.contains(entry.getKey()) && !entry.getKey().equals(defaultId)) {
                continue;
            }
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
        return matching.isEmpty() ? List.of(defaultId) : matching;
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
     * @param samples How many biome samples were taken along the road.
     * @param shares Each type's share of the route, largest first, in the order ties were broken. A sample whose biome
     *               matches several types equally counts toward each, so the shares can add up to more than the samples.
     */
    public record Choice(ResourceLocation typeId, int variant, RoadSettings settings, int samples, List<RouteShare> shares) {
    }

    /** How many of a road's biome samples count toward a road type: its share of the road's route. */
    public record RouteShare(ResourceLocation typeId, int samples) {
    }
}
