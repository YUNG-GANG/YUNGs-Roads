package com.yungnickyoung.minecraft.yungsroads.util;

/**
 * Packs the x and z coordinates of a point on any 2D integer grid, such as the routing lattice or a grid of tiles,
 * into a single long. Useful as a key for fastutil's long-keyed maps and sets, which avoid allocating an object per
 * key.
 * <p>
 * Uses the same layout as {@link net.minecraft.world.level.ChunkPos#asLong}, x in the low 32 bits and z in the high 32
 * bits, but isn't tied to chunks. Prefer {@code ChunkPos} for actual chunk coordinates.
 */
public final class GridKeys {
    private GridKeys() {
    }

    /** Packs the coordinates of a grid point into a key. */
    public static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    /** The x coordinate of the grid point packed into the key. */
    public static int x(long key) {
        return (int) key;
    }

    /** The z coordinate of the grid point packed into the key. */
    public static int z(long key) {
        return (int) (key >>> 32);
    }
}
