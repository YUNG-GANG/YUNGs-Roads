package com.yungnickyoung.minecraft.yungsroads.world.road.placement;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Writes road blocks into a level, recording each changed position's original and placed state so the change can be
 * reverted later (see {@link RoadBlockLog}).
 * <p>
 * Works both during worldgen and in a live world. The two differ in how the ground is found: worldgen heightmaps
 * only exist while a chunk generates, and a live world already has trees and plants on top of the ground.
 */
public class RoadBlockWriter {
    /** Send changes to clients, but skip neighbor and shape updates, the same as worldgen placement. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** The most blocks of vegetation cleared above a road block, enough for double-tall plants. */
    private static final int MAX_VEGETATION_HEIGHT = 2;

    private final WorldGenLevel level;
    private final boolean live;
    final Long2ObjectMap<BlockState> originals = new Long2ObjectOpenHashMap<>();
    final Long2ObjectMap<BlockState> placed = new Long2ObjectOpenHashMap<>();

    private RoadBlockWriter(WorldGenLevel level, boolean live) {
        this.level = level;
        this.live = live;
    }

    /** Creates a writer for placing roads while a chunk generates. */
    public static RoadBlockWriter forWorldgen(WorldGenLevel level) {
        return new RoadBlockWriter(level, false);
    }

    /** Creates a writer for placing roads into chunks that have finished generating. */
    public static RoadBlockWriter forLiveWorld(ServerLevel level) {
        return new RoadBlockWriter(level, true);
    }

    public WorldGenLevel level() {
        return this.level;
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    public void setBlock(BlockPos pos, BlockState state) {
        long key = pos.asLong();
        if (!this.originals.containsKey(key)) {
            this.originals.put(key, this.level.getBlockState(pos));
        }
        this.placed.put(key, state);
        this.level.setBlock(pos, state, FLAGS);
    }

    /**
     * Removes plants standing on the given block, which can't survive on a road.
     * During worldgen, roads are placed before vegetation, so there is normally nothing to remove.
     */
    public void clearVegetationAbove(BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        for (int i = 0; i < MAX_VEGETATION_HEIGHT; i++) {
            mutable.move(Direction.UP);
            BlockState state = this.level.getBlockState(mutable);
            if (state.isAir() || !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return;
            }
            setBlock(mutable, Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Returns the y-coordinate of the ground block in the given column, ignoring trees and plants.
     * Fluids count as ground, so roads over water become bridges.
     */
    public int surfaceHeight(int x, int z) {
        // Worldgen heightmaps only exist while a chunk generates
        Heightmap.Types heightmap = this.live ? Heightmap.Types.MOTION_BLOCKING_NO_LEAVES : Heightmap.Types.WORLD_SURFACE_WG;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, this.level.getHeight(heightmap, x, z) - 1, z);
        while (mutable.getY() > this.level.getMinBuildHeight() && isAboveGround(this.level.getBlockState(mutable))) {
            mutable.move(Direction.DOWN);
        }
        return mutable.getY();
    }

    /**
     * Whether the block stands on the ground rather than being part of it: air, trees, and plants.
     * During worldgen, neighboring chunks' trees may already reach into a chunk when its roads are placed.
     */
    public static boolean isAboveGround(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }
}
