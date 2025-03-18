package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.mojang.serialization.DataResult;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureRegion {
    private static final String ENDPOINT_CHUNKS_KEY = "endpoint_chunks";
    private static final String ROADS_KEY = "roads";

    private final StructureRegionPos pos;
    private final List<Long> roadEndpointChunks;
    private final List<Road> roads;

    public StructureRegion(long regionKey) {
        this(regionKey, new ArrayList<>(), new ArrayList<>());
    }

    public StructureRegion(long regionKey, List<Long> endpointChunks, List<Road> roads) {
        this.pos = new StructureRegionPos(regionKey);
        this.roadEndpointChunks = endpointChunks;
        this.roads = roads;
    }

    public StructureRegion(long regionKey, CompoundTag compoundTag) {
        this.pos = new StructureRegionPos(regionKey);

        // Endpoints
        long[] endpointChunkPositions = compoundTag.getLongArray(ENDPOINT_CHUNKS_KEY);
        this.roadEndpointChunks = new ArrayList<>();
        for (long endpointPosLong : endpointChunkPositions) {
            this.roadEndpointChunks.add(endpointPosLong);
        }

        // Roads
        List<Road> roads = new ArrayList<>();
        if (compoundTag.contains(ROADS_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag roadsNbt = compoundTag.getCompound(ROADS_KEY);
            for (String key : roadsNbt.getAllKeys()) {
                Tag roadNbt = roadsNbt.get(key);
                roads.add(Road.CODEC.decode(NbtOps.INSTANCE, roadNbt).result().get().getFirst());
            }
        }
        this.roads = roads;

        this.roadEndpointChunks.forEach(chunkPos -> DebugRenderer.getInstance().addEndpointPos(new ChunkPos(chunkPos)));
        this.roads.forEach(road -> road.nodes.forEach(node -> DebugRenderer.getInstance().addPath(new ChunkPos(node.jitteredPos), null)));
    }

    public CompoundTag toNbt() {
        CompoundTag compoundTag = new CompoundTag();

        // Endpoints
        LongArrayTag endpointsNbt = new LongArrayTag(roadEndpointChunks);
        compoundTag.put(ENDPOINT_CHUNKS_KEY, endpointsNbt);

        // Roads
        CompoundTag roadsNbt = new CompoundTag();
        this.roads.forEach((road) -> {
            DataResult<Tag> dataResult = Road.CODEC.encodeStart(NbtOps.INSTANCE, road);
            Optional<Tag> result = dataResult.result();
            if (result.isPresent()) {
                BlockPos startPos = road.getStartPos();
                roadsNbt.put(String.format("%d,%d,%d", startPos.getX(), startPos.getY(), startPos.getZ()), result.get());
            } else {
                YungsRoadsCommon.LOGGER.error("Missing data result for road {}", road.toString());
            }
        });
        compoundTag.put(ROADS_KEY, roadsNbt);

        return compoundTag;
    }

    public boolean hasRoadAt(BlockPos pos) {
        return this.roads.stream().anyMatch((road) -> road.positions.contains(pos));
    }

    public boolean hasRoadInRange(BlockPos pos, int range) {
        return this.roads.stream().anyMatch((road) -> road.positions.stream().anyMatch(p -> p.distSqr(pos) <= range * range));
    }

    public Optional<Road> getRoadAt(BlockPos pos) {
        return this.roads.stream().filter(road -> road.positions.stream()
                .anyMatch(p -> p.getX() == pos.getX() && p.getZ() == pos.getZ()))
                .findFirst();
    }

    public String getFileName() {
        return this.pos.getFileName();
    }

    public List<Long> getRoadEndpointChunks() {
        return this.roadEndpointChunks;
    }

    public List<Road> getRoads() {
        return this.roads;
    }

    public StructureRegionPos getPos() {
        return this.pos;
    }
}
