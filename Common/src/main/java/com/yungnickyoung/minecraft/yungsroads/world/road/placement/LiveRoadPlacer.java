package com.yungnickyoung.minecraft.yungsroads.world.road.placement;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Re-places roads in chunks that have already generated, so road settings can be tuned without creating a new world.
 * Only used in debug mode.
 * <p>
 * A chunk is refreshed by reverting the blocks its roads changed, as recorded in the {@link RoadBlockLog}, then placing
 * the current roads again. Stale chunks are refreshed a few at a time each tick, and unloaded chunks are refreshed
 * when they next load.
 * <p>
 * Must only be used on the server thread, except for {@link #getBlockLog}.
 */
public class LiveRoadPlacer {
    /** The most time spent refreshing chunks per tick, to keep the server responsive. */
    private static final long TICK_BUDGET_NANOS = 10_000_000;

    private final ServerLevel level;
    private final StructureRegionCache structureRegionCache;
    private final RoadBlockLog blockLog;
    /** Chunks waiting to be refreshed, in the order they'll be refreshed. Each chunk is queued at most once. */
    private final LongLinkedOpenHashSet pendingChunks = new LongLinkedOpenHashSet();
    private RoadFeatureConfiguration roadConfig;

    public LiveRoadPlacer(ServerLevel level, StructureRegionCache structureRegionCache, Path saveDirectory) {
        this.level = level;
        this.structureRegionCache = structureRegionCache;
        this.blockLog = new RoadBlockLog(saveDirectory.resolve("placed_blocks.dat"), level.holderLookup(Registries.BLOCK));
    }

    public RoadBlockLog getBlockLog() {
        return this.blockLog;
    }

    /** The number of chunks waiting to be refreshed. */
    public int pendingChunkCount() {
        return this.pendingChunks.size();
    }

    /**
     * Marks every chunk as stale and queues the loaded ones for refreshing, nearest to players first.
     * Call after changing road settings or road data.
     */
    public void refreshAll() {
        this.blockLog.bumpEpoch();

        LongSet loadedChunks = new LongOpenHashSet();
        for (long chunkKey : this.blockLog.chunkKeys()) {
            if (isLoaded(chunkKey)) {
                loadedChunks.add(chunkKey);
            }
        }

        // Chunks generated before the log existed have no entry, so also check the area around each player
        int viewDistance = this.level.getServer().getPlayerList().getViewDistance();
        for (ServerPlayer player : this.level.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            for (int x = playerChunk.x - viewDistance; x <= playerChunk.x + viewDistance; x++) {
                for (int z = playerChunk.z - viewDistance; z <= playerChunk.z + viewDistance; z++) {
                    long chunkKey = ChunkPos.asLong(x, z);
                    if (isLoaded(chunkKey)) {
                        loadedChunks.add(chunkKey);
                    }
                }
            }
        }

        LongList sorted = new LongArrayList(loadedChunks);
        sorted.sort(Comparator.comparingDouble(this::distanceSqToNearestPlayer));
        this.pendingChunks.addAll(sorted);
    }

    /** Queues the chunk for refreshing if its roads are stale. Called whenever a chunk loads. */
    public void onChunkLoad(LevelChunk chunk) {
        long chunkKey = chunk.getPos().toLong();
        if (this.blockLog.isStale(chunkKey)) {
            this.pendingChunks.add(chunkKey);
        }
    }

    /** Refreshes queued chunks until this tick's time budget runs out. */
    public void tick() {
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        while (!this.pendingChunks.isEmpty() && System.nanoTime() < deadline) {
            refreshChunk(this.pendingChunks.removeFirstLong());
        }
    }

    /** Saves the block log. Called whenever the level saves. */
    public void save() {
        this.blockLog.saveIfDirty();
    }

    private void refreshChunk(long chunkKey) {
        // Unloaded chunks stay stale, and are queued again when they next load
        if (!isLoaded(chunkKey) || !this.blockLog.isStale(chunkKey)) {
            return;
        }
        RoadFeatureConfiguration config = getRoadConfig();
        if (config == null) {
            return;
        }

        int epoch = this.blockLog.epoch();
        RoadBlockLog.Entry previous = this.blockLog.get(chunkKey);
        if (previous != null) {
            previous.revert(this.level);
        }

        RoadBlockWriter writer = RoadBlockWriter.forLiveWorld(this.level);
        RandomSource random = RandomSource.create(this.level.getSeed() ^ chunkKey);
        RoadFeature.placeRoadsInChunk(writer, random, new ChunkPos(chunkKey), this.structureRegionCache, config);
        this.blockLog.record(chunkKey, epoch, writer);
    }

    private boolean isLoaded(long chunkKey) {
        return this.level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) != null;
    }

    private double distanceSqToNearestPlayer(long chunkKey) {
        double closest = Double.MAX_VALUE;
        for (ServerPlayer player : this.level.players()) {
            double dx = ChunkPos.getX(chunkKey) - player.chunkPosition().x;
            double dz = ChunkPos.getZ(chunkKey) - player.chunkPosition().z;
            closest = Math.min(closest, dx * dx + dz * dz);
        }
        return closest;
    }

    /**
     * The road feature's configuration, which holds the road and bridge block types.
     * Read from the registry, since there's no feature context outside of worldgen.
     */
    @Nullable
    private RoadFeatureConfiguration getRoadConfig() {
        if (this.roadConfig == null) {
            this.roadConfig = this.level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getOptional(YungsRoadsCommon.id("road"))
                    .map(ConfiguredFeature::config)
                    .filter(RoadFeatureConfiguration.class::isInstance)
                    .map(RoadFeatureConfiguration.class::cast)
                    .orElse(null);
            if (this.roadConfig == null) {
                YungsRoadsCommon.LOGGER.error("Unable to find the road feature configuration. Roads can't be refreshed.");
            }
        }
        return this.roadConfig;
    }
}
