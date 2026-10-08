package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.mojang.serialization.Codec;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.RoadCenter;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
    public static final int FORMAT_VERSION = 12;

    /**
     * Road positions are indexed into every chunk within this many blocks of them.
     * Must cover both the positions that can affect placement in a chunk and the range of road proximity checks. The
     * lookup reach covers both, since it's at least the widest road's half-width plus a block.
     */
    public static final int INDEX_PADDING = AbstractRoadGenerator.PLACEMENT_LOOKUP_REACH;

    private static final String VERSION_KEY = "version";
    private static final String ENDPOINT_CHUNKS_KEY = "endpoint_chunks";
    private static final String ROADS_KEY = "roads";

    private final StructureRegionPos pos;
    private final List<Long> roadEndpointChunks;
    private final List<Road> roads;

    /** Road center positions, keyed by every chunk within {@link #INDEX_PADDING} blocks of them. */
    private final Long2ObjectMap<List<RoadCenter>> roadCentersByChunk = new Long2ObjectOpenHashMap<>();

    /** The road center positions that are part of a land bridge, as {@link BlockPos#asLong}. */
    private final LongSet landBridgePositions = new LongOpenHashSet();

    /** The road center positions that are part of a tunnel, as {@link BlockPos#asLong}. */
    private final LongSet tunnelPositions = new LongOpenHashSet();

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
     * Loads a region from NBT. Its roads get the settings of their road types from the given road types.
     *
     * @throws IllegalStateException if the data is from a different format version or can't be decoded.
     */
    public static StructureRegion fromNbt(long regionKey, CompoundTag compoundTag, RoadTypes roadTypes) {
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
        Codec<Road> roadCodec = Road.codec(roadTypes);
        CompoundTag roadsNbt = compoundTag.getCompound(ROADS_KEY);
        for (String key : roadsNbt.getAllKeys()) {
            roads.add(roadCodec.parse(NbtOps.INSTANCE, roadsNbt.get(key)).getOrThrow(IllegalStateException::new));
        }

        return new StructureRegion(regionKey, endpointChunks, roads);
    }

    /**
     * A copy of the region whose roads have the current settings of their road types, for after the types are edited
     * in ways that don't change routes. See {@link Road#withSettings}.
     */
    public StructureRegion withRoadSettings(RoadTypes roadTypes) {
        List<Road> roads = this.roads.stream().map(road -> road.withSettings(roadTypes.settings(road.roadType, road.variant))).toList();
        return new StructureRegion(this.pos.asLong(), this.roadEndpointChunks, new ArrayList<>(roads));
    }

    public CompoundTag toNbt() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putInt(VERSION_KEY, FORMAT_VERSION);

        // Endpoints
        compoundTag.put(ENDPOINT_CHUNKS_KEY, new LongArrayTag(roadEndpointChunks));

        // Roads. Keyed by both endpoints, since a structure may start more than one road.
        CompoundTag roadsNbt = new CompoundTag();
        Codec<Road> roadCodec = Road.codec(null);
        for (Road road : this.roads) {
            roadCodec.encodeStart(NbtOps.INSTANCE, road).resultOrPartial(error ->
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
                RoadCenter center = new RoadCenter(roadPos, road);
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        this.roadCentersByChunk.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), k -> new ArrayList<>()).add(center);
                    }
                }
            }
            for (Road.Span landBridge : road.landBridges) {
                for (int i = landBridge.first(); i <= landBridge.last(); i++) {
                    this.landBridgePositions.add(road.positions.get(i).asLong());
                }
            }
            for (Road.Span tunnel : road.tunnels) {
                for (int i = tunnel.first(); i <= tunnel.last(); i++) {
                    this.tunnelPositions.add(road.positions.get(i).asLong());
                }
            }
        }
    }

    /**
     * Whether the road center position, including its y, is part of one of this region's land bridges.
     */
    public boolean isLandBridge(BlockPos roadPos) {
        return this.landBridgePositions.contains(roadPos.asLong());
    }

    /**
     * Whether the road center position, including its y, is part of one of this region's tunnels.
     */
    public boolean isTunnel(BlockPos roadPos) {
        return this.tunnelPositions.contains(roadPos.asLong());
    }

    /**
     * Returns the center positions of this region's roads that are within {@link #INDEX_PADDING} blocks of the chunk.
     */
    public List<RoadCenter> getRoadCentersNearChunk(long chunkKey) {
        return this.roadCentersByChunk.getOrDefault(chunkKey, List.of());
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
