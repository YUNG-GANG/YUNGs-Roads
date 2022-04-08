package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Stores the position of a structure region on the structure region grid.
 * A single structure region is 256 x 256 chunks.
 */
public class StructureRegionPos {
    private final int x, z;

    public StructureRegionPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public StructureRegionPos(BlockPos blockPos) {
        this.x = blockPos.getX() >> 12; // 1 structure region coordinate = 4096 blocks (256 chunks)
        this.z = blockPos.getZ() >> 12;
    }

    public StructureRegionPos(long l) {
        this.x = (int)l;
        this.z = (int)(l >> 32);
    }

    public static long asLong(int x, int z) {
        return (long)x & 0xFFFF_FFFFL | ((long) z & 0xFFFF_FFFFL) << 32;
    }

    public long asLong() {
        return asLong(this.x, this.z);
    }

    @Override
    public String toString() {
        return String.format("(%d, %d) %d", x, z, this.asLong());
    }

    public ChunkPos getMinChunkPosInRegion() {
        return new ChunkPos(this.x << 8, this.z << 8);
    }

    public ChunkPos getMaxChunkPosInRegion() {
        return new ChunkPos(((this.x + 1) << 8) - 1, ((this.z + 1) << 8) - 1);
    }

    public String getFileName() {
        return "r." + x + "." + z + ".roads";
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }
}
