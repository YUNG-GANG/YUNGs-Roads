package com.yungnickyoung.minecraft.yungsroads.debug;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.services.Services;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Applies road settings to a running world for tuning: regenerates the roads near the player with the new settings,
 * then re-places them in loaded chunks. The previous settings and roads are kept so a change can be reverted.
 * Only used in debug mode, in singleplayer.
 * <p>
 * Methods that change state must be called on the server thread. The status and previous roads may be read from any
 * thread.
 */
public final class RoadTuning {
    /**
     * Roads are regenerated if they could reach within this many blocks of the player.
     * Roads further away keep their old shape until they're regenerated from a closer position.
     */
    public static final int REGENERATE_RADIUS = 1024;

    /**
     * The road settings that can be tuned: the global settings in the config file, which apply to every road type, and
     * each road type's settings.
     */
    public record Settings(ConfigModule.Advanced advanced, SortedMap<ResourceLocation, RoadType> roadTypes, ConfigModule.Debug debug) {
        /** A copy of the settings in use in the level, which can be changed without affecting it. */
        public static Settings current(ServerLevel level) {
            return new Settings(YungsRoadsCommon.CONFIG.advanced.copy(), copy(roadTypesOf(level).current()), YungsRoadsCommon.CONFIG.debug.copy());
        }

        void applyTo(ServerLevel level) {
            YungsRoadsCommon.CONFIG.advanced = this.advanced.copy();
            roadTypesOf(level).set(this.roadTypes);
            YungsRoadsCommon.CONFIG.debug = this.debug.copy();
        }

        /** Whether roads would be generated the same with the other settings, which may differ in whether roads are placed. */
        boolean generatesSameAs(Settings other) {
            if (!this.advanced.sameAs(other.advanced) || !this.roadTypes.keySet().equals(other.roadTypes.keySet())) {
                return false;
            }
            for (Map.Entry<ResourceLocation, RoadType> entry : this.roadTypes.entrySet()) {
                if (!entry.getValue().sameAs(other.roadTypes.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        private static SortedMap<ResourceLocation, RoadType> copy(Map<ResourceLocation, RoadType> roadTypes) {
            SortedMap<ResourceLocation, RoadType> copy = new TreeMap<>();
            roadTypes.forEach((id, type) -> copy.put(id, type.copy()));
            return copy;
        }
    }

    /**
     * The state before the most recent change.
     *
     * @param regions The regions that change replaced, by region key. A null value means the region wasn't loaded.
     */
    private record Snapshot(ServerLevel level, Settings settings, Long2ObjectMap<StructureRegion> regions) {
    }

    @Nullable
    private static volatile Snapshot previous;
    private static volatile boolean busy = false;
    private static volatile Component status = Component.empty();

    private RoadTuning() {
    }

    /** Whether a change is being applied. No other change can be made until it finishes. */
    public static boolean isBusy() {
        return busy;
    }

    /** A short description of the most recent change, or of the one in progress. */
    public static Component status() {
        return status;
    }

    /** Whether there is a change in the given level that {@link #revert} can undo. */
    public static boolean canRevert(ServerLevel level) {
        Snapshot snapshot = previous;
        return snapshot != null && snapshot.level == level && !busy;
    }

    /**
     * The roads replaced by the most recent change in the given level, for comparing against the current roads.
     */
    public static Collection<StructureRegion> previousRegions(ServerLevel level) {
        Snapshot snapshot = previous;
        if (snapshot == null || snapshot.level != level) {
            return List.of();
        }
        return snapshot.regions.values().stream().filter(Objects::nonNull).toList();
    }

    /** The road types used in the level. */
    public static RoadTypes roadTypesOf(ServerLevel level) {
        return cacheOf(level).getStructureRegionGenerator().getRoadTypes();
    }

    /**
     * Applies the settings, regenerating the roads near the given position unless only whether roads are placed changed.
     * Regeneration runs on worker threads, and the results are swapped in on the server thread when all are done.
     */
    public static void apply(ServerLevel level, Settings settings, BlockPos center) {
        if (busy) {
            return;
        }
        Settings oldSettings = Settings.current(level);
        boolean onlyDebugChanged = settings.generatesSameAs(oldSettings) && !sameDebug(settings.debug, oldSettings.debug);
        settings.applyTo(level);

        StructureRegionCache cache = cacheOf(level);
        LongList regionKeys = StructureRegionCache.regionKeysNearArea(
                center.getX() - REGENERATE_RADIUS, center.getZ() - REGENERATE_RADIUS,
                center.getX() + REGENERATE_RADIUS, center.getZ() + REGENERATE_RADIUS);
        Long2ObjectMap<StructureRegion> oldRegions = new Long2ObjectOpenHashMap<>();
        regionKeys.forEach(regionKey -> oldRegions.put(regionKey, cache.getRegionIfLoaded(regionKey)));

        if (onlyDebugChanged) {
            previous = new Snapshot(level, oldSettings, oldRegions);
            refreshPlacedRoads(level);
            status = Component.translatable("yungsroads.status.replaced");
            return;
        }

        busy = true;
        status = Component.translatable("yungsroads.status.regenerating", regionKeys.size());
        long startTime = System.nanoTime();
        List<CompletableFuture<StructureRegion>> futures = new ArrayList<>();
        for (long regionKey : regionKeys) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> cache.getStructureRegionGenerator().generateRegion(regionKey),
                    Util.backgroundExecutor()));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> level.getServer().execute(() -> {
            busy = false;
            if (error != null) {
                // Leave the world as it was
                oldSettings.applyTo(level);
                status = Component.translatable("yungsroads.status.failed", String.valueOf(error.getMessage()));
                YungsRoadsCommon.LOGGER.error("Unable to regenerate roads", error);
                return;
            }

            int roadCount = 0;
            for (CompletableFuture<StructureRegion> future : futures) {
                StructureRegion region = future.join();
                cache.replaceRegion(region);
                roadCount += region.getRoads().size();
            }
            previous = new Snapshot(level, oldSettings, oldRegions);
            refreshPlacedRoads(level);
            status = Component.translatable("yungsroads.status.regenerated",
                    regionKeys.size(), roadCount, String.format("%.1f", (System.nanoTime() - startTime) / 1e9));
        }));
    }

    /**
     * Restores the settings and roads from before the most recent change. Reverting again redoes the change.
     */
    public static void revert(ServerLevel level) {
        Snapshot snapshot = previous;
        if (busy || snapshot == null || snapshot.level != level) {
            return;
        }

        StructureRegionCache cache = cacheOf(level);
        Long2ObjectMap<StructureRegion> currentRegions = new Long2ObjectOpenHashMap<>();
        snapshot.regions.keySet().forEach(regionKey -> currentRegions.put(regionKey, cache.getRegionIfLoaded(regionKey)));
        Settings currentSettings = Settings.current(level);

        snapshot.settings.applyTo(level);
        for (Long2ObjectMap.Entry<StructureRegion> entry : snapshot.regions.long2ObjectEntrySet()) {
            if (entry.getValue() != null) {
                cache.replaceRegion(entry.getValue());
            } else {
                // It wasn't loaded before, so let it generate again with the restored settings when needed
                cache.invalidateRegion(entry.getLongKey());
            }
        }

        previous = new Snapshot(level, currentSettings, currentRegions);
        refreshPlacedRoads(level);
        status = Component.translatable("yungsroads.status.reverted");
    }

    /** Saves the applied global settings, and whether roads are placed, to the config file. */
    public static void saveGlobal() {
        try {
            Services.PLATFORM.saveRoadSettings();
            status = Component.translatable("yungsroads.status.saved_config");
        } catch (RuntimeException e) {
            YungsRoadsCommon.LOGGER.error("Unable to save road settings", e);
            status = Component.translatable("yungsroads.status.save_config_failed", String.valueOf(e.getMessage()));
        }
    }

    /**
     * Saves a road type, as applied, to the world's tuned road type datapack. See {@link RoadTypeExport#writeWorldDatapack}.
     */
    public static void saveRoadType(ServerLevel level, ResourceLocation typeId) {
        RoadType type = roadTypesOf(level).current().get(typeId);
        if (type == null) {
            return;
        }
        try {
            RoadTypeExport.writeWorldDatapack(level.getServer(), typeId, type);
            status = Component.translatable("yungsroads.status.saved_road_type", typeId.toString(), RoadTypeExport.DATAPACK_NAME);
        } catch (IOException | RuntimeException e) {
            YungsRoadsCommon.LOGGER.error("Unable to save road type {}", typeId, e);
            status = Component.translatable("yungsroads.status.save_road_type_failed", String.valueOf(e.getMessage()));
        }
    }

    private static void refreshPlacedRoads(ServerLevel level) {
        LiveRoadPlacer liveRoadPlacer = ((IStructureRegionCacheProvider) level).getLiveRoadPlacer();
        if (liveRoadPlacer != null) {
            liveRoadPlacer.refreshAll();
        }
    }

    private static StructureRegionCache cacheOf(ServerLevel level) {
        return ((IStructureRegionCacheProvider) level).getStructureRegionCache();
    }

    /** Whether the options affecting placed blocks are the same. */
    private static boolean sameDebug(ConfigModule.Debug a, ConfigModule.Debug b) {
        return a.placeRoads == b.placeRoads;
    }
}
