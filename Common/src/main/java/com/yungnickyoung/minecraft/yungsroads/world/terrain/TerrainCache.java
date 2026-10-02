package com.yungnickyoung.minecraft.yungsroads.world.terrain;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.world.level.ChunkPos;

/**
 * Caches {@link TerrainSampler} results on a world-aligned lattice with the given step size.
 * Lattice point (i, j) corresponds to block (i * step, j * step).
 * <p>
 * Because the lattice is anchored to the world rather than to any one road, all roads routed with the same cache
 * share samples. Not thread-safe: create one per region generation.
 */
public class TerrainCache {
    /** Stored height for lattice points roads may not pass through. */
    public static final double IMPASSABLE = Double.NaN;

    /** Returned by the map for lattice points that haven't been sampled yet. */
    private static final double NOT_SAMPLED = Double.NEGATIVE_INFINITY;

    private final TerrainSampler sampler;
    private final int step;
    private final Long2DoubleOpenHashMap heights = new Long2DoubleOpenHashMap();

    /**
     * Samples lattice points without rechecking water. Routing samples so many points that rechecking would make it
     * much slower, and an occasional wrong water point only costs routing a small detour.
     */
    private final TerrainSampler.HeightSampler latticeSampler;

    /**
     * Samples single blocks, rechecking water. Block samples feed checks where one wrong water sample would mark a
     * whole step as a bridge, and they're few enough that rechecking costs little.
     */
    private final TerrainSampler.HeightSampler blockSampler;

    public TerrainCache(TerrainSampler sampler, int step) {
        this.sampler = sampler;
        this.latticeSampler = sampler.createHeightSampler(false);
        this.blockSampler = sampler.createHeightSampler(true);
        this.step = step;
        this.heights.defaultReturnValue(NOT_SAMPLED);
    }

    /**
     * Returns the approximate surface height at the given lattice point, or {@link #IMPASSABLE} (NaN) if the point
     * lies in an ocean.
     */
    public double heightAt(int i, int j) {
        long key = ChunkPos.asLong(i, j);
        double height = this.heights.get(key);
        if (height == NOT_SAMPLED) {
            int x = i * this.step;
            int z = j * this.step;
            height = this.latticeSampler.surfaceHeight(x, z);

            // Ocean surfaces are always below sea level, so only those points need the more expensive biome lookup
            if (height < this.sampler.seaLevel() && this.sampler.isOcean(x, z)) {
                height = IMPASSABLE;
            }
            this.heights.put(key, height);
        }
        return height;
    }

    /**
     * Samples the approximate surface height of any block column, without caching. For checks finer than the lattice.
     * Unlike lattice points, surfaces found underwater are rechecked, so they're reliable enough to decide bridges.
     */
    public double surfaceHeightAtBlock(int x, int z) {
        return this.blockSampler.surfaceHeight(x, z);
    }

    public int sampleCount() {
        return this.heights.size();
    }

    public int step() {
        return this.step;
    }

    public int seaLevel() {
        return this.sampler.seaLevel();
    }
}
