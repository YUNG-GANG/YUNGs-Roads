package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.RoadBlockWriter;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.BitSet;
import java.util.Optional;

/**
 * Generates roads routed by {@link LatticePathfinder}. The route's nodes are jittered so the road looks less straight,
 * then joined into a center line of block positions, whose heights are set by {@link RoadProfile}.
 * <p>
 * Steps that turn out to cross water too narrow for the lattice to see, such as streams, are made bridges after
 * routing, so that every water crossing is straight.
 */
public class AStarRoadGenerator extends AbstractRoadGenerator {
    @Override
    public Optional<Road> generateRoad(BlockPos pos1, BlockPos pos2, TerrainCache terrain) {
        Road road = new Road(pos1, pos2);
        Optional<LatticePathfinder.Path> path = LatticePathfinder.findPath(road.getStartPos(), road.getEndPos(), terrain);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        road.nodes.addAll(path.get().nodes());

        boolean[] isBridgeSegment = addBridges(road, path.get().bridgeSegments(), terrain);

        // The noise is created per road since its seed is mutable state and roads may be generated concurrently
        FastNoise jitter = createJitterNoise(road.getStartPos());
        jitterNodes(road, isBridgeSegment, jitter);
        addCenterLine(road, isBridgeSegment, jitter);

        RoadProfile.apply(road, terrain, YungsRoadsCommon.CONFIG.advanced);
        return Optional.of(road);
    }

    /**
     * Records the road's water crossings in {@link Road#bridges}: the bridges routing found, plus any step that crosses
     * water too narrow for a lattice point to land in, such as a stream.
     *
     * @param routedBridges The segments routing bridged, set at the index of the node each starts from.
     * @return Whether each segment, from node n to node n + 1, is a bridge.
     */
    private static boolean[] addBridges(Road road, BitSet routedBridges, TerrainCache terrain) {
        boolean[] isBridgeSegment = new boolean[road.nodes.size() - 1];
        for (int n = 0; n < isBridgeSegment.length; n++) {
            BlockPos from = road.nodes.get(n).rawPos;
            BlockPos to = road.nodes.get(n + 1).rawPos;
            isBridgeSegment[n] = routedBridges.get(n) || crossesWater(from, to, terrain);
            if (isBridgeSegment[n]) {
                road.bridges.add(new Road.Bridge(atDeckHeight(from, terrain), atDeckHeight(to, terrain)));
            }
        }
        return isBridgeSegment;
    }

    /**
     * Shifts each node sideways by noise, so the road looks less straight and more natural. Bridge ends aren't
     * jittered, so bridges stay straight and land where routing checked for dry ground.
     */
    private static void jitterNodes(Road road, boolean[] isBridgeSegment, FastNoise jitter) {
        for (int j = 0; j < road.nodes.size(); j++) {
            Road.DebugNode debugNode = road.nodes.get(j);
            boolean isBridgeEnd = (j > 0 && isBridgeSegment[j - 1]) || (j < isBridgeSegment.length && isBridgeSegment[j]);
            debugNode.jitteredPos = isBridgeEnd ? debugNode.rawPos : jitteredPos(jitter, debugNode.rawPos, road, j);
        }
    }

    /**
     * Fills in {@link Road#positions} with the block positions along the lines between consecutive jittered nodes.
     * The positions between nodes are jittered too, except along bridges, which stay straight.
     */
    private static void addCenterLine(Road road, boolean[] isBridgeSegment, FastNoise jitter) {
        for (int i = 0; i < road.nodes.size(); i++) {
            BlockPos nodePos = road.nodes.get(i).jitteredPos;
            road.positions.add(nodePos);
            if (i < road.nodes.size() - 1) {
                addSegment(road, nodePos, road.nodes.get(i + 1).jitteredPos, isBridgeSegment[i], jitter);
            }
        }
    }

    /**
     * Adds the block positions along the line from one node toward the next, stopping within a couple of blocks of
     * the next node, which is added separately.
     */
    private static void addSegment(Road road, BlockPos nodePos, BlockPos nextNodePos, boolean straight, FastNoise jitter) {
        int xDistanceToNextNode = nextNodePos.getX() - nodePos.getX();
        int zDistanceToNextNode = nextNodePos.getZ() - nodePos.getZ();
        double nodePathSlope = xDistanceToNextNode == 0 ? Integer.MAX_VALUE : zDistanceToNextNode / (double) xDistanceToNextNode;
        int xStepDir = xDistanceToNextNode >= 0 ? 1 : -1;
        int zStepDir = zDistanceToNextNode >= 0 ? 1 : -1;

        // Counter used to determine when to move in the z direction vs the x direction
        double slopeCounter = Math.abs(nodePathSlope);

        BlockPos.MutableBlockPos mutable = nodePos.mutable();
        while (!isWithinDistance(mutable, nextNodePos, 2)) {
            // Move in z direction
            while (slopeCounter >= 1 && !isWithinDistance(mutable, nextNodePos, 2)) {
                road.positions.add(straight ? mutable.immutable() : jitteredPos(jitter, mutable.immutable(), nodePos, nextNodePos));
                mutable.move(0, 0, zStepDir);
                slopeCounter--;
            }

            // Move in x direction
            while (slopeCounter < 1 && !isWithinDistance(mutable, nextNodePos, 2)) {
                road.positions.add(straight ? mutable.immutable() : jitteredPos(jitter, mutable.immutable(), nodePos, nextNodePos));
                mutable.move(xStepDir, 0, 0);
                slopeCounter += Math.abs(nodePathSlope);
            }

            // Place path at current position. Jittered like the rest of the line, so the positions stay in order
            // along a single center line, which the road's height profile is computed along.
            if (!mutable.equals(nodePos) && !mutable.equals(nextNodePos)) {
                road.positions.add(straight ? mutable.immutable() : jitteredPos(jitter, mutable.immutable(), nodePos, nextNodePos));
            }
        }
    }

    /**
     * Whether the straight line between two positions passes over water, checked at every block between them.
     */
    private static boolean crossesWater(BlockPos from, BlockPos to, TerrainCache terrain) {
        int length = (int) Math.ceil(Math.sqrt(horizontalDistSqr(from, to)));
        for (int s = 1; s < length; s++) {
            double t = s / (double) length;
            int x = (int) Math.round(Mth.lerp(t, from.getX(), to.getX()));
            int z = (int) Math.round(Mth.lerp(t, from.getZ(), to.getZ()));
            if (LatticePathfinder.isWater(terrain.surfaceHeightAtBlock(x, z), terrain.seaLevel())) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * The position at the height a bridge's deck would have there. Road endpoints come from structure locations, which
     * don't have a meaningful height.
     */
    private static BlockPos atDeckHeight(BlockPos pos, TerrainCache terrain) {
        double surfaceHeight = terrain.surfaceHeightAtBlock(pos.getX(), pos.getZ());
        return pos.atY((int) Math.round(LatticePathfinder.roadHeight(surfaceHeight, terrain.seaLevel())));
    }

    private static FastNoise createJitterNoise(BlockPos roadStartPos) {
        FastNoise jitter = new FastNoise();
        jitter.SetNoiseType(FastNoise.NoiseType.Simplex);
        jitter.SetFrequency(.05f);
        jitter.SetFractalOctaves(1);
        jitter.SetSeed(roadStartPos.getX() * 1000 + roadStartPos.getZ());
        return jitter;
    }

    private static BlockPos jitteredPos(FastNoise jitter, BlockPos pos, Road road, int i) {
        BlockPos p1, p2;
        if (i == 0) {
            p1 = pos;
            p2 = road.nodes.get(i + 1).rawPos;
        } else if (i == road.nodes.size() - 1) {
            p1 = road.nodes.get(i - 1).rawPos;
            p2 = pos;
        } else {
            p1 = road.nodes.get(i - 1).rawPos;
            p2 = road.nodes.get(i + 1).rawPos;
        }
        return jitteredPos(jitter, pos, p1, p2);
    }

    private static BlockPos jitteredPos(FastNoise jitter, BlockPos pos, BlockPos prevPos, BlockPos nextPos) {
        Vector3f normal = calculateNormalBetween(prevPos, nextPos);
        float jitterAmount = (float) (jitter.GetNoise(pos.getX(), pos.getZ()) * YungsRoadsCommon.CONFIG.advanced.jitterAmount);
        Vector3f jitterOffset = new Vector3f(normal.x() * jitterAmount, 0, normal.z() * jitterAmount);
        return pos.offset((int) jitterOffset.x(), 0, (int) jitterOffset.z());
    }

    /**
     * Calculates the x-z normal vector between two positions.
     * @param p1 The first position.
     * @param p2 The second position.
     * @return The normalized normal vector.
     */
    private static Vector3f calculateNormalBetween(BlockPos p1, BlockPos p2) {
        BlockPos offset = p2.subtract(p1);
        Vector3f tangent = new Vector3f(offset.getX(), 0, offset.getZ());
        tangent.normalize();
        return new Vector3f(tangent.z(), 0, -tangent.x());
    }

    @Override
    public void placeDebugMarkers(Road road, RoadBlockWriter writer, ChunkPos chunkPos) {
        if (YungsRoadsCommon.CONFIG.debug.placeStraightDebugLine) {
            placeDebugLine(road, writer, chunkPos);
        }
        if (YungsRoadsCommon.CONFIG.debug.placeRoadEndpointDebugMarkers) {
            placeDebugMarker(writer, chunkPos, road.getStartPos(), Blocks.EMERALD_BLOCK.defaultBlockState());
            placeDebugMarker(writer, chunkPos, road.getEndPos(), Blocks.EMERALD_BLOCK.defaultBlockState());
        }
        for (Road.DebugNode debugNode : road.nodes) {
            if (YungsRoadsCommon.CONFIG.debug.placeUnjitteredPosDebugMarkers) {
                placeDebugMarker(writer, chunkPos, debugNode.rawPos, Blocks.PURPLE_WOOL.defaultBlockState());
            }
            if (YungsRoadsCommon.CONFIG.debug.placeJitteredPosDebugMarkers) {
                placeDebugMarker(writer, chunkPos, debugNode.jitteredPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }
    }

    /**
     * Places a straight line of gold blocks between the start and end points of the road.
     */
    private void placeDebugLine(Road road, RoadBlockWriter writer, ChunkPos chunkPos) {
        // Determine total slope of line from starting point to ending point
        int totalXDiff = road.getEndPos().getX() - road.getStartPos().getX();
        int totalZDiff = road.getEndPos().getZ() - road.getStartPos().getZ();
        double totalSlope = totalXDiff == 0 ? Integer.MAX_VALUE : totalZDiff / (double) totalXDiff;
        int xDir = totalXDiff >= 0 ? 1 : -1; // x direction multiplier
        int zDir = totalZDiff >= 0 ? 1 : -1; // z direction multiplier

        double slopeCounter = Math.abs(totalSlope);
        BlockPos.MutableBlockPos mutable = road.getStartPos().mutable();

        while (!isWithinDistance(mutable, road.getEndPos(), 10)) {
            // Move in z direction
            while (slopeCounter >= 1 && !isWithinDistance(mutable, road.getEndPos(), 10)) {
                placeDebugBlock(writer, chunkPos, mutable, Blocks.GOLD_BLOCK.defaultBlockState());
                mutable.move(0, 0, zDir);
                slopeCounter--;
            }

            // Move in x direction
            while (slopeCounter < 1 && !isWithinDistance(mutable, road.getEndPos(), 10)) {
                placeDebugBlock(writer, chunkPos, mutable, Blocks.GOLD_BLOCK.defaultBlockState());
                mutable.move(xDir, 0, 0);
                slopeCounter += Math.abs(totalSlope);
            }

            // Place path at final position
            placeDebugBlock(writer, chunkPos, mutable, Blocks.GOLD_BLOCK.defaultBlockState());
        }
    }

    private static boolean isWithinDistance(BlockPos pos, BlockPos targetPos, int distance) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < distance * distance;
    }
}
