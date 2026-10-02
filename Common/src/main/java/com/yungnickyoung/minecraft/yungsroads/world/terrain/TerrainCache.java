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
    private final TerrainSampler.HeightSampler heightSampler;
    private final int step;
    private final Long2DoubleOpenHashMap heights = new Long2DoubleOpenHashMap();

    public TerrainCache(TerrainSampler sampler, int step) {
        this.sampler = sampler;
        this.heightSampler = sampler.createHeightSampler();
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
            height = this.heightSampler.surfaceHeight(x, z);

            // Ocean surfaces are always below sea level, so only those points need the more expensive biome lookup
            if (height < this.sampler.seaLevel() && this.sampler.isOcean(x, z)) {
                height = IMPASSABLE;
            }
            this.heights.put(key, height);
        }
        return height;
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
