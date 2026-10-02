package com.yungnickyoung.minecraft.yungsroads.world.structureregion;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AStarRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AbstractRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainSampler;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for generating new StructureRegions.
 * Does not store any generated regions - that is handled by {@link StructureRegionCache}
 */
public class StructureRegionGenerator {
    /** The maximum straight-line distance between two structures connected by a road, in blocks. */
    public static final int MAX_ROAD_LENGTH = 800;

    /** The minimum straight-line distance between two structures connected by a road, in blocks. */
    public static final int MIN_ROAD_LENGTH = 50;

    private final StructureLocator structureLocator;
    private final TerrainSampler terrainSampler;
    private final AbstractRoadGenerator roadGenerator;

    public StructureRegionGenerator(ServerLevel serverLevel) {
        this.terrainSampler = new TerrainSampler(serverLevel);
        this.structureLocator = new StructureLocator(serverLevel, this.terrainSampler, YungsRoadsCommon.CONFIG.general.structures);
        this.roadGenerator = new AStarRoadGenerator();
    }

    /**
     * Generates a new {@link StructureRegion} for the given region key.
     * <p>
     * Structures are connected using a relative neighborhood graph: two structures get a road if no other structure
     * is closer to both of them than they are to each other. This links each structure to its natural neighbors
     * without redundant parallel roads.
     * <p>
     * A road can cross region borders. Each road is generated only by the region containing its owning endpoint
     * (see {@link #isOwner}). Since every structure that could affect whether an edge exists lies within
     * {@link #MAX_ROAD_LENGTH} of the owning endpoint, looking that far outside the region is enough for all regions
     * to agree on the graph.
     */
    public StructureRegion generateRegion(long regionKey) {
        StructureRegionPos regionPos = new StructureRegionPos(regionKey);
        ChunkPos regionMin = regionPos.getMinChunkPosInRegion();
        ChunkPos regionMax = regionPos.getMaxChunkPosInRegion();
        int marginChunks = (MAX_ROAD_LENGTH >> 4) + 1;

        long locateStartTime = System.nanoTime();
        List<ChunkPos> structures = this.structureLocator.locate(
                new ChunkPos(regionMin.x - marginChunks, regionMin.z - marginChunks),
                new ChunkPos(regionMax.x + marginChunks, regionMax.z + marginChunks));
        List<ChunkPos> ownStructures = structures.stream().filter(regionPos::isChunkInRegion).toList();
        long locateTimeMs = (System.nanoTime() - locateStartTime) / 1_000_000;

        long routeStartTime = System.nanoTime();
        TerrainCache terrain = new TerrainCache(this.terrainSampler, YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
        List<Road> roads = new ArrayList<>();
        int edgeCount = 0;
        for (ChunkPos start : ownStructures) {
            for (ChunkPos end : structures) {
                if (isOwner(start, end) && isGraphEdge(start, end, structures)) {
                    edgeCount++;
                    this.roadGenerator.generateRoad(start.getWorldPosition(), end.getWorldPosition(), terrain).ifPresent(roads::add);
                }
            }
        }
        YungsRoadsCommon.LOGGER.debug("Region {}: located {} structures ({} own) in {} ms, routed {}/{} roads in {} ms with {} terrain samples",
                regionPos, structures.size(), ownStructures.size(), locateTimeMs, roads.size(), edgeCount,
                (System.nanoTime() - routeStartTime) / 1_000_000, terrain.sampleCount());

        // Mirrors the debug registration done when loading a region from disk in StructureRegion
        ownStructures.forEach(DebugRenderer.getInstance()::addEndpointPos);

        List<Long> ownStructureLongs = ownStructures.stream().map(ChunkPos::toLong).toList();
        return new StructureRegion(regionKey, new ArrayList<>(ownStructureLongs), roads);
    }

    /**
     * Whether a road between the two structures is owned by {@code a}. Exactly one of the two endpoints owns each
     * road, so exactly one region generates it.
     */
    private static boolean isOwner(ChunkPos a, ChunkPos b) {
        return a.x < b.x || (a.x == b.x && a.z < b.z);
    }

    /**
     * Whether the two structures should be connected by a road: their distance must be within the allowed road length,
     * and no other structure may be closer to both of them than they are to each other.
     */
    private static boolean isGraphEdge(ChunkPos a, ChunkPos b, List<ChunkPos> structures) {
        long abDistSq = distSq(a, b);
        if (abDistSq > blocksToChunksSq(MAX_ROAD_LENGTH) || abDistSq < blocksToChunksSq(MIN_ROAD_LENGTH)) {
            return false;
        }

        for (ChunkPos c : structures) {
            if (c.equals(a) || c.equals(b)) {
                continue;
            }
            if (distSq(a, c) < abDistSq && distSq(b, c) < abDistSq) {
                return false;
            }
        }
        return true;
    }

    /** Squared distance between two chunk positions, in chunks. */
    private static long distSq(ChunkPos a, ChunkPos b) {
        long dx = a.x - b.x;
        long dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static long blocksToChunksSq(int blocks) {
        double chunks = blocks / 16.0;
        return (long) (chunks * chunks);
    }

    public AbstractRoadGenerator getRoadGenerator() {
        return this.roadGenerator;
    }

    public void setEndpointStructures(HolderSet<Structure> endpointStructures) {
        this.structureLocator.setEndpointStructures(endpointStructures);
    }
}
