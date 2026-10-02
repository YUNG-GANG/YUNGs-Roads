package com.yungnickyoung.minecraft.yungsroads.world.road.placement;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records every block that road placement changed in each chunk, so roads can be removed and placed again with
 * different settings. Only used in debug mode.
 * <p>
 * Each chunk's entry is stamped with the epoch it was placed in. Changing the road settings bumps the epoch, which
 * marks every chunk placed before then as stale until it's refreshed by {@link LiveRoadPlacer}.
 * <p>
 * Saved alongside the level, since the recorded blocks are part of the saved chunks. Thread-safe.
 */
public class RoadBlockLog {
    private static final int FORMAT_VERSION = 1;

    private final Path file;
    private final HolderGetter<Block> blockLookup;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger epoch = new AtomicInteger();
    private volatile boolean dirty = false;

    /**
     * The blocks changed in one chunk.
     *
     * @param epoch     The epoch the blocks were placed in.
     * @param positions Each changed position, as {@link BlockPos#asLong}.
     * @param originals The state each position had before roads were placed.
     * @param placed    The state roads left at each position.
     */
    public record Entry(int epoch, long[] positions, BlockState[] originals, BlockState[] placed) {
        /**
         * Restores each position's original state. Positions that changed since roads were placed are left alone,
         * so edits made by players aren't overwritten.
         */
        void revert(ServerLevel level) {
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int i = 0; i < this.positions.length; i++) {
                mutable.set(this.positions[i]);
                if (level.getBlockState(mutable) == this.placed[i]) {
                    level.setBlock(mutable, this.originals[i], RoadBlockWriter.FLAGS);
                }
            }
        }
    }

    public RoadBlockLog(Path file, HolderGetter<Block> blockLookup) {
        this.file = file;
        this.blockLookup = blockLookup;
        load();
    }

    public int epoch() {
        return this.epoch.get();
    }

    /** Marks every chunk placed so far as stale. */
    public void bumpEpoch() {
        this.epoch.incrementAndGet();
        this.dirty = true;
    }

    /**
     * Records the blocks a writer changed in a chunk, replacing the chunk's previous entry.
     *
     * @param epoch The epoch read before the road data used for placement was fetched.
     */
    public void record(long chunkKey, int epoch, RoadBlockWriter writer) {
        int size = writer.originals.size();
        long[] positions = new long[size];
        BlockState[] originals = new BlockState[size];
        BlockState[] placed = new BlockState[size];
        int i = 0;
        for (var mapEntry : writer.originals.long2ObjectEntrySet()) {
            positions[i] = mapEntry.getLongKey();
            originals[i] = mapEntry.getValue();
            placed[i] = writer.placed.get(mapEntry.getLongKey());
            i++;
        }
        this.entries.put(chunkKey, new Entry(epoch, positions, originals, placed));
        this.dirty = true;
    }

    @Nullable
    public Entry get(long chunkKey) {
        return this.entries.get(chunkKey);
    }

    /**
     * Whether the chunk's roads were placed before the current epoch.
     * Chunks with no entry were generated before the log existed, so they're only stale once settings have changed.
     */
    public boolean isStale(long chunkKey) {
        Entry entry = this.entries.get(chunkKey);
        return entry == null ? this.epoch.get() > 0 : entry.epoch < this.epoch.get();
    }

    public Set<Long> chunkKeys() {
        return this.entries.keySet();
    }

    public void saveIfDirty() {
        if (!this.dirty) {
            return;
        }
        this.dirty = false;

        // A palette keeps the file small, since nearly all entries share a handful of states
        Object2IntMap<BlockState> paletteIds = new Object2IntOpenHashMap<>();
        ListTag palette = new ListTag();
        ListTag chunks = new ListTag();
        for (Map.Entry<Long, Entry> mapEntry : this.entries.entrySet()) {
            Entry entry = mapEntry.getValue();
            int[] originalIds = new int[entry.positions.length];
            int[] placedIds = new int[entry.positions.length];
            for (int i = 0; i < entry.positions.length; i++) {
                originalIds[i] = paletteId(entry.originals[i], paletteIds, palette);
                placedIds[i] = paletteId(entry.placed[i], paletteIds, palette);
            }
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putLong("chunk", mapEntry.getKey());
            chunkTag.putInt("epoch", entry.epoch);
            chunkTag.put("positions", new LongArrayTag(entry.positions));
            chunkTag.put("originals", new IntArrayTag(originalIds));
            chunkTag.put("placed", new IntArrayTag(placedIds));
            chunks.add(chunkTag);
        }

        CompoundTag tag = new CompoundTag();
        tag.putInt("version", FORMAT_VERSION);
        tag.putInt("epoch", this.epoch.get());
        tag.put("palette", palette);
        tag.put("chunks", chunks);

        try {
            // Write to a temporary file first, so a crash mid-write can't corrupt the existing log
            Path tempFile = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            NbtIo.writeCompressed(tag, tempFile);
            Files.move(tempFile, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            this.dirty = true;
            YungsRoadsCommon.LOGGER.error("Unable to save road block log {}", this.file, e);
        }
    }

    private static int paletteId(BlockState state, Object2IntMap<BlockState> paletteIds, ListTag palette) {
        return paletteIds.computeIfAbsent(state, s -> {
            palette.add(NbtUtils.writeBlockState(state));
            return palette.size() - 1;
        });
    }

    private void load() {
        if (!Files.exists(this.file)) {
            return;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(this.file, NbtAccounter.unlimitedHeap());
            if (tag.getInt("version") != FORMAT_VERSION) {
                YungsRoadsCommon.LOGGER.warn("Ignoring road block log {} with unsupported version", this.file);
                return;
            }

            List<BlockState> palette = new ArrayList<>();
            for (Tag stateTag : tag.getList("palette", Tag.TAG_COMPOUND)) {
                palette.add(NbtUtils.readBlockState(this.blockLookup, (CompoundTag) stateTag));
            }

            for (Tag chunkTag : tag.getList("chunks", Tag.TAG_COMPOUND)) {
                CompoundTag chunk = (CompoundTag) chunkTag;
                long[] positions = chunk.getLongArray("positions");
                int[] originalIds = chunk.getIntArray("originals");
                int[] placedIds = chunk.getIntArray("placed");
                BlockState[] originals = new BlockState[positions.length];
                BlockState[] placed = new BlockState[positions.length];
                for (int i = 0; i < positions.length; i++) {
                    originals[i] = palette.get(originalIds[i]);
                    placed[i] = palette.get(placedIds[i]);
                }
                this.entries.put(chunk.getLong("chunk"), new Entry(chunk.getInt("epoch"), positions, originals, placed));
            }
            this.epoch.set(tag.getInt("epoch"));
        } catch (IOException | RuntimeException e) {
            YungsRoadsCommon.LOGGER.error("Unable to load road block log {}. Roads placed so far can't be removed.", this.file, e);
        }
    }
}
