package com.yungnickyoung.minecraft.yungsroads.debug;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.services.Services;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    /** The road settings that can be tuned. */
    public record Settings(ConfigModule.Advanced advanced, ConfigModule.Debug debug) {
        public static Settings current() {
            return new Settings(YungsRoadsCommon.CONFIG.advanced.copy(), YungsRoadsCommon.CONFIG.debug.copy());
        }

        void applyToConfig() {
            YungsRoadsCommon.CONFIG.advanced = this.advanced.copy();
            YungsRoadsCommon.CONFIG.debug = this.debug.copy();
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
    private static volatile String status = "";

    private RoadTuning() {
    }

    /** Whether a change is being applied. No other change can be made until it finishes. */
    public static boolean isBusy() {
        return busy;
    }

    /** A short description of the most recent change, or of the one in progress. */
    public static String status() {
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

    /**
     * Applies the settings, regenerating the roads near the given position unless only debug options changed.
     * Regeneration runs on worker threads, and the results are swapped in on the server thread when all are done.
     */
    public static void apply(ServerLevel level, Settings settings, BlockPos center) {
        if (busy) {
            return;
        }
        Settings oldSettings = Settings.current();
        boolean onlyDebugChanged = settings.advanced.sameAs(oldSettings.advanced) && !sameDebug(settings.debug, oldSettings.debug);
        settings.applyToConfig();

        StructureRegionCache cache = cacheOf(level);
        LongList regionKeys = StructureRegionCache.regionKeysNearArea(
                center.getX() - REGENERATE_RADIUS, center.getZ() - REGENERATE_RADIUS,
                center.getX() + REGENERATE_RADIUS, center.getZ() + REGENERATE_RADIUS);
        Long2ObjectMap<StructureRegion> oldRegions = new Long2ObjectOpenHashMap<>();
        regionKeys.forEach(regionKey -> oldRegions.put(regionKey, cache.getRegionIfLoaded(regionKey)));

        if (onlyDebugChanged) {
            previous = new Snapshot(level, oldSettings, oldRegions);
            refreshPlacedRoads(level);
            status = "Applied debug options";
            return;
        }

        busy = true;
        status = "Regenerating " + regionKeys.size() + " regions...";
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
                oldSettings.applyToConfig();
                status = "Failed: " + error.getMessage();
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
            status = String.format("Regenerated %d regions (%d roads) in %.1f s",
                    regionKeys.size(), roadCount, (System.nanoTime() - startTime) / 1e9);
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
        Settings currentSettings = Settings.current();

        snapshot.settings.applyToConfig();
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
        status = "Reverted to previous settings";
    }

    /** Saves the current settings to the config file. */
    public static void saveToConfig() {
        Services.PLATFORM.saveRoadSettings();
        status = "Saved settings to config";
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

    /** Whether the options affecting placed blocks are the same. The F3 option is applied as soon as it's changed. */
    private static boolean sameDebug(ConfigModule.Debug a, ConfigModule.Debug b) {
        return a.placeRoads == b.placeRoads
                && a.placeDebugPaths == b.placeDebugPaths
                && a.placeUnjitteredPosDebugMarkers == b.placeUnjitteredPosDebugMarkers
                && a.placeJitteredPosDebugMarkers == b.placeJitteredPosDebugMarkers
                && a.placeRoadEndpointDebugMarkers == b.placeRoadEndpointDebugMarkers
                && a.placeStraightDebugLine == b.placeStraightDebugLine;
    }
}
