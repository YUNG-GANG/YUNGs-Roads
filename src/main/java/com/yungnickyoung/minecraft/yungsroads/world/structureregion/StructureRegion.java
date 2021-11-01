package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.LongArrayNBT;

public class StructureRegion {
    private StructureRegionPos pos;
    private LongOpenHashSet villageChunks;

    public StructureRegion(long regionKey) {
        this(regionKey, new LongOpenHashSet());
    }

    public StructureRegion(long regionKey, LongOpenHashSet villageChunks) {
        this.pos = new StructureRegionPos(regionKey);
        this.villageChunks = villageChunks;
    }

    public StructureRegion(long regionKey, CompoundNBT compoundNbt) {
        this.pos = new StructureRegionPos(regionKey);
        long[] villagePositions = compoundNbt.getLongArray("villageChunks");
        this.villageChunks = new LongOpenHashSet(villagePositions);
    }

    public CompoundNBT toNbt() {
        CompoundNBT compoundNbt = new CompoundNBT();
        LongArrayNBT villagePosNbt = new LongArrayNBT(villageChunks);
        compoundNbt.put("villageChunks", villagePosNbt);
        return compoundNbt;
    }

    public String getFileName() {
        return this.pos.getFileName();
    }

    public LongOpenHashSet getVillageChunks() {
        return this.villageChunks;
    }

    public StructureRegionPos getPos() {
        return this.pos;
    }
}
