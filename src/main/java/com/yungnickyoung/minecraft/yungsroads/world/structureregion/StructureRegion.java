package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.mojang.serialization.DataResult;
import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.LongArrayNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureRegion {
    private final StructureRegionPos pos;
    private final LongOpenHashSet villageChunks;
    private final List<Road> roads;

    public StructureRegion(long regionKey) {
        this(regionKey, new LongOpenHashSet(), new ArrayList<>());
    }

    public StructureRegion(long regionKey, LongOpenHashSet villageChunks, List<Road> roads) {
        this.pos = new StructureRegionPos(regionKey);
        this.villageChunks = villageChunks;
        this.roads = roads;
    }

    public StructureRegion(long regionKey, CompoundNBT compoundNbt) {
        this.pos = new StructureRegionPos(regionKey);

        // Villages
        long[] villagePositions = compoundNbt.getLongArray("villageChunks");
        this.villageChunks = new LongOpenHashSet(villagePositions);

        // Roads
        List<Road> roads = new ArrayList<>();
        if (compoundNbt.contains("roads", 10)) {
            CompoundNBT roadsNbt = compoundNbt.getCompound("roads");
            for (String key : roadsNbt.keySet()) {
                INBT roadNbt = roadsNbt.get(key);
                roads.add(Road.CODEC.decode(NBTDynamicOps.INSTANCE, roadNbt).result().get().getFirst());
            }
        }
        this.roads = roads;
    }

    public CompoundNBT toNbt() {
        CompoundNBT compoundNbt = new CompoundNBT();

        // Villages
        LongArrayNBT villagePosNbt = new LongArrayNBT(villageChunks);
        compoundNbt.put("villageChunks", villagePosNbt);

        // Roads
        CompoundNBT roadsNbt = new CompoundNBT();
        this.roads.forEach((road) -> {
            DataResult<INBT> dataResult = Road.CODEC.encodeStart(NBTDynamicOps.INSTANCE, road);
            Optional<INBT> result = dataResult.result();
            if (result.isPresent()) {
                BlockPos startPos = road.getVillageStart();
                roadsNbt.put(String.format("%d,%d,%d", startPos.getX(), startPos.getY(), startPos.getZ()), result.get());
            } else {
                YungsRoads.LOGGER.error("Missing data result for road {}", road.toString());
            }
        });
        compoundNbt.put("roads", roadsNbt);

        return compoundNbt;
    }

    public String getFileName() {
        return this.pos.getFileName();
    }

    public LongOpenHashSet getVillageChunks() {
        return this.villageChunks;
    }

    public List<Road> getRoads() {
        return this.roads;
    }

    public StructureRegionPos getPos() {
        return this.pos;
    }
}
