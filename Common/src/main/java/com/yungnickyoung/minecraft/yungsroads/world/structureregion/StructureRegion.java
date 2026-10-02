package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class StructureRegion {
    /**
     * Version of the saved region format. Bump this whenever the stored data or the road generation algorithm changes,
     * so that existing region files are regenerated instead of loaded.
     */
    public static final int FORMAT_VERSION = 2;

    /**
     * Road positions are indexed into every chunk within this many blocks of them.
     * Must cover both block placement around each position and the range of road proximity checks.
     */
    public static final int INDEX_PADDING = Math.max(AbstractRoadGenerator.PLACEMENT_REACH, 3);

    private static final String VERSION_KEY = "version";
    private static final String ENDPOINT_CHUNKS_KEY = "endpoint_chunks";
    private static final String ROADS_KEY = "roads";

    private final StructureRegionPos pos;
    private final List<Long> roadEndpointChunks;
    private final List<Road> roads;

    /** Road center positions, keyed by every chunk within {@link #INDEX_PADDING} blocks of them. */
    private final Long2ObjectMap<List<BlockPos>> roadPositionsByChunk = new Long2ObjectOpenHashMap<>();

    public StructureRegion(long regionKey) {
        this(regionKey, new ArrayList<>(), new ArrayList<>());
    }

    public StructureRegion(long regionKey, List<Long> endpointChunks, List<Road> roads) {
        this.pos = new StructureRegionPos(regionKey);
        this.roadEndpointChunks = endpointChunks;
        this.roads = roads;
        buildChunkIndex();
    }

    /**
     * Loads a region from NBT.
     *
     * @throws IllegalStateException if the data is from a different format version or can't be decoded.
     */
    public static StructureRegion fromNbt(long regionKey, CompoundTag compoundTag) {
        int version = compoundTag.getInt(VERSION_KEY);
        if (version != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported region format version " + version + ", expected " + FORMAT_VERSION);
        }

        // Endpoints
        List<Long> endpointChunks = new ArrayList<>();
        for (long endpointPosLong : compoundTag.getLongArray(ENDPOINT_CHUNKS_KEY)) {
            endpointChunks.add(endpointPosLong);
        }

        // Roads
        List<Road> roads = new ArrayList<>();
        CompoundTag roadsNbt = compoundTag.getCompound(ROADS_KEY);
        for (String key : roadsNbt.getAllKeys()) {
            roads.add(Road.CODEC.parse(NbtOps.INSTANCE, roadsNbt.get(key)).getOrThrow(IllegalStateException::new));
        }

        return new StructureRegion(regionKey, endpointChunks, roads);
    }

    public CompoundTag toNbt() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putInt(VERSION_KEY, FORMAT_VERSION);

        // Endpoints
        compoundTag.put(ENDPOINT_CHUNKS_KEY, new LongArrayTag(roadEndpointChunks));

        // Roads. Keyed by both endpoints, since a structure may start more than one road.
        CompoundTag roadsNbt = new CompoundTag();
        for (Road road : this.roads) {
            Road.CODEC.encodeStart(NbtOps.INSTANCE, road).resultOrPartial(error ->
                    YungsRoadsCommon.LOGGER.error("Unable to save road {}: {}", road, error)
            ).ifPresent(roadNbt -> roadsNbt.put(road.getStartPos().toShortString() + " - " + road.getEndPos().toShortString(), roadNbt));
        }
        compoundTag.put(ROADS_KEY, roadsNbt);

        return compoundTag;
    }

    private void buildChunkIndex() {
        for (Road road : this.roads) {
            for (BlockPos roadPos : road.positions) {
                int minChunkX = (roadPos.getX() - INDEX_PADDING) >> 4;
                int maxChunkX = (roadPos.getX() + INDEX_PADDING) >> 4;
                int minChunkZ = (roadPos.getZ() - INDEX_PADDING) >> 4;
                int maxChunkZ = (roadPos.getZ() + INDEX_PADDING) >> 4;
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        this.roadPositionsByChunk.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), k -> new ArrayList<>()).add(roadPos);
                    }
                }
            }
        }
    }

    /**
     * Returns the center positions of this region's roads that are within {@link #INDEX_PADDING} blocks of the chunk.
     */
    public List<BlockPos> getRoadPositionsNearChunk(long chunkKey) {
        return this.roadPositionsByChunk.getOrDefault(chunkKey, List.of());
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
