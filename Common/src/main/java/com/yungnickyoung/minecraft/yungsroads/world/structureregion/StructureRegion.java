package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.mojang.serialization.DataResult;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

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

    public StructureRegion(long regionKey, CompoundTag compoundTag) {
        this.pos = new StructureRegionPos(regionKey);

        // Villages
        long[] villagePositions = compoundTag.getLongArray("villageChunks");
        this.villageChunks = new LongOpenHashSet(villagePositions);

        // Roads
        List<Road> roads = new ArrayList<>();
        if (compoundTag.contains("roads", 10)) {
            CompoundTag roadsNbt = compoundTag.getCompound("roads");
            for (String key : roadsNbt.getAllKeys()) {
                Tag roadNbt = roadsNbt.get(key);
                roads.add(Road.CODEC.decode(NbtOps.INSTANCE, roadNbt).result().get().getFirst());
            }
        }
        this.roads = roads;
    }

    public CompoundTag toNbt() {
        CompoundTag compoundTag = new CompoundTag();

        // Villages
        LongArrayTag villagePosNbt = new LongArrayTag(villageChunks);
        compoundTag.put("villageChunks", villagePosNbt);

        // Roads
        CompoundTag roadsNbt = new CompoundTag();
        this.roads.forEach((road) -> {
            DataResult<Tag> dataResult = Road.CODEC.encodeStart(NbtOps.INSTANCE, road);
            Optional<Tag> result = dataResult.result();
            if (result.isPresent()) {
                BlockPos startPos = road.getVillageStart();
                roadsNbt.put(String.format("%d,%d,%d", startPos.getX(), startPos.getY(), startPos.getZ()), result.get());
            } else {
                YungsRoadsCommon.LOGGER.error("Missing data result for road {}", road.toString());
            }
        });
        compoundTag.put("roads", roadsNbt);

        return compoundTag;
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
