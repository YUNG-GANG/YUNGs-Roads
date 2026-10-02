package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.RoadBlockWriter;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Routes roads with A* over a world-aligned lattice of terrain samples.
 * <p>
 * The cost of each step is its horizontal length scaled by how steep it is and whether it crosses water, summed
 * along the path. Steps steeper than the configured max grade are not allowed at all. Oceans are impassable.
 */
public class AStarRoadGenerator extends AbstractRoadGenerator {
    /** Failsafe so an unreachable target can't stall worldgen indefinitely. */
    private static final int MAX_EXPANSIONS = 100_000;

    /** The search area is the endpoints' bounding box, expanded by this fraction of the road's length... */
    private static final double SEARCH_MARGIN_FRACTION = 0.25;

    /** ...but by at least this many blocks. */
    private static final int MIN_SEARCH_MARGIN = 64;

    /**
     * Lattice moves: the 8 neighbors plus the 8 knight moves.
     * Knight moves allow headings between the 45-degree increments, giving less zigzagged roads.
     */
    private static final int[][] MOVES = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };

    /** The length of each move in {@link #MOVES}, in lattice steps. */
    private static final double[] MOVE_LENGTHS = new double[MOVES.length];

    static {
        for (int m = 0; m < MOVES.length; m++) {
            MOVE_LENGTHS[m] = Math.sqrt(MOVES[m][0] * MOVES[m][0] + MOVES[m][1] * MOVES[m][1]);
        }
    }

    /**
     * Returns how far outside the endpoints' bounding box a road of the given length may stray, in blocks.
     */
    public static int maxSearchMargin(double roadLength, int step) {
        return (int) Math.ceil(Math.max(MIN_SEARCH_MARGIN, roadLength * SEARCH_MARGIN_FRACTION)) + step;
    }

    @Override
    public Optional<Road> generateRoad(BlockPos pos1, BlockPos pos2, TerrainCache terrain) {
        Road road = new Road(pos1, pos2);
        List<Road.DebugNode> path = findPath(road.getStartPos(), road.getEndPos(), terrain);
        if (path.isEmpty()) {
            return Optional.empty();
        }

        road.nodes.addAll(path);

        // Apply random jitter to all nodes to make path less straight and more natural.
        // The noise is created per road since its seed is mutable state and roads may be generated concurrently.
        FastNoise jitter = createJitterNoise(road.getStartPos());
        for (int j = 0; j < road.nodes.size(); j++) {
            Road.DebugNode debugNode = road.nodes.get(j);
            debugNode.jitteredPos = jitteredPos(jitter, debugNode.rawPos, road, j);
        }

        // Store ALL jittered block positions for iteration later when placing
        for (int i = 0; i < road.nodes.size(); i++) {
            Road.DebugNode debugNode = road.nodes.get(i);
            BlockPos nodePos = debugNode.jitteredPos;

            road.positions.add(nodePos);

            if (i == road.nodes.size() - 1) continue;

            // Start linear interpolation between the two nodes' jittered positions
            BlockPos nextNodePos = road.nodes.get(i + 1).jitteredPos;
            int xDistanceToNextNode = nextNodePos.getX() - nodePos.getX();
            int zDistanceToNextNode = nextNodePos.getZ() - nodePos.getZ();
            double nodePathSlope = xDistanceToNextNode == 0 ? Integer.MAX_VALUE : zDistanceToNextNode / (double) xDistanceToNextNode;
            int xStepDir = xDistanceToNextNode >= 0 ? 1 : -1;
            int zStepDir = zDistanceToNextNode >= 0 ? 1 : -1;

            // Counter used to determine when to move in the z direction vs the x direction
            double slopeCounter = Math.abs(nodePathSlope);

            // Create path from the current node to the next node
            BlockPos.MutableBlockPos mutable = nodePos.mutable();
            while (!isWithinDistance(mutable, nextNodePos, 2)) {
                // Move in z direction
                while (slopeCounter >= 1 && !isWithinDistance(mutable, nextNodePos, 2)) {
                    road.positions.add(jitteredPos(jitter, mutable.immutable(), nodePos, nextNodePos));
                    mutable.move(0, 0, zStepDir);
                    slopeCounter--;
                }

                // Move in x direction
                while (slopeCounter < 1 && !isWithinDistance(mutable, nextNodePos, 2)) {
                    road.positions.add(jitteredPos(jitter, mutable.immutable(), nodePos, nextNodePos));
                    mutable.move(xStepDir, 0, 0);
                    slopeCounter += Math.abs(nodePathSlope);
                }

                // Place path at current position
                if (!mutable.equals(nodePos) && !mutable.equals(nextNodePos)) {
                    road.positions.add(mutable.immutable());
                }
            }
        }

        return Optional.of(road);
    }

    /**
     * Finds the cheapest path between two positions with A*.
     *
     * @return The path's nodes from start to end, including the exact start and end positions,
     * or an empty list if no path was found.
     */
    private List<Road.DebugNode> findPath(BlockPos startPos, BlockPos endPos, TerrainCache terrain) {
        ConfigModule.Advanced settings = YungsRoadsCommon.CONFIG.advanced;
        int step = terrain.step();
        int seaLevel = terrain.seaLevel();

        int startI = Math.floorDiv(startPos.getX() + step / 2, step);
        int startJ = Math.floorDiv(startPos.getZ() + step / 2, step);
        int goalI = Math.floorDiv(endPos.getX() + step / 2, step);
        int goalJ = Math.floorDiv(endPos.getZ() + step / 2, step);

        // Confine the search to the endpoints' bounding box plus a margin
        int margin = Math.ceilDiv(maxSearchMargin(Math.sqrt(startPos.distSqr(endPos)), step), step);
        int minI = Math.min(startI, goalI) - margin;
        int maxI = Math.max(startI, goalI) + margin;
        int minJ = Math.min(startJ, goalJ) - margin;
        int maxJ = Math.max(startJ, goalJ) + margin;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Long2DoubleOpenHashMap bestG = new Long2DoubleOpenHashMap();
        bestG.defaultReturnValue(Double.POSITIVE_INFINITY);
        Long2LongOpenHashMap parents = new Long2LongOpenHashMap();
        LongOpenHashSet closed = new LongOpenHashSet();

        long startKey = ChunkPos.asLong(startI, startJ);
        bestG.put(startKey, 0);
        open.add(new Node(startI, startJ, 0, heuristic(startI, startJ, goalI, goalJ, step, settings.heuristicWeight)));

        Node finalNode = null;
        int expansions = 0;

        while (!open.isEmpty()) {
            Node node = open.poll();
            long nodeKey = ChunkPos.asLong(node.i, node.j);

            // The queue may hold stale duplicates of a node that was later reached more cheaply
            if (!closed.add(nodeKey)) {
                continue;
            }

            // Close enough to the goal. The exact end position is appended below.
            if (Math.abs(node.i - goalI) <= 1 && Math.abs(node.j - goalJ) <= 1) {
                finalNode = node;
                break;
            }

            if (++expansions > MAX_EXPANSIONS) {
                YungsRoadsCommon.LOGGER.warn("Road search from {} to {} exceeded {} expansions. Skipping road.", startPos, endPos, MAX_EXPANSIONS);
                return List.of();
            }

            double nodeHeight = roadHeight(terrain.heightAt(node.i, node.j), seaLevel);

            for (int m = 0; m < MOVES.length; m++) {
                int ni = node.i + MOVES[m][0];
                int nj = node.j + MOVES[m][1];
                if (ni < minI || ni > maxI || nj < minJ || nj > maxJ) {
                    continue;
                }

                long neighborKey = ChunkPos.asLong(ni, nj);
                if (closed.contains(neighborKey)) {
                    continue;
                }

                double neighborSurface = terrain.heightAt(ni, nj);
                if (Double.isNaN(neighborSurface)) {
                    continue; // Impassable
                }

                double run = MOVE_LENGTHS[m] * step;
                double grade = Math.abs(roadHeight(neighborSurface, seaLevel) - nodeHeight) / run;
                if (grade > settings.maxGrade) {
                    continue;
                }

                double costFactor = 1 + settings.slopeWeight * grade * grade;
                if (neighborSurface < seaLevel) {
                    costFactor += settings.waterWeight;
                }

                double g = node.g + run * costFactor;
                if (g < bestG.get(neighborKey)) {
                    bestG.put(neighborKey, g);
                    parents.put(neighborKey, nodeKey);
                    open.add(new Node(ni, nj, g, g + heuristic(ni, nj, goalI, goalJ, step, settings.heuristicWeight)));
                }
            }
        }

        if (finalNode == null) {
            return List.of();
        }

        // Walk back from the final node to the start
        List<Road.DebugNode> path = new ArrayList<>();
        path.add(new Road.DebugNode(endPos, finalNode.g, 0));
        long key = ChunkPos.asLong(finalNode.i, finalNode.j);
        while (true) {
            int i = ChunkPos.getX(key);
            int j = ChunkPos.getZ(key);
            BlockPos latticePos = new BlockPos(i * step, (int) Math.round(roadHeight(terrain.heightAt(i, j), seaLevel)), j * step);
            path.add(new Road.DebugNode(latticePos, bestG.get(key), heuristic(i, j, goalI, goalJ, step, settings.heuristicWeight)));
            if (key == startKey) {
                break;
            }
            key = parents.get(key);
        }
        path.add(new Road.DebugNode(startPos, 0, heuristic(startI, startJ, goalI, goalJ, step, settings.heuristicWeight)));

        return path.reversed();
    }

    /**
     * The height a road would sit at above the given surface height.
     * Over water, roads sit at sea level as bridges rather than following the riverbed or lake floor.
     */
    private static double roadHeight(double surfaceHeight, int seaLevel) {
        return Double.isNaN(surfaceHeight) ? seaLevel : Math.max(surfaceHeight, seaLevel);
    }

    /**
     * Euclidean distance to the goal, in blocks. Every step costs at least its length, so with a weight of 1 this
     * never overestimates and A* finds the cheapest path. Higher weights trade path quality for search speed.
     */
    private static double heuristic(int i, int j, int goalI, int goalJ, int step, double weight) {
        double di = i - goalI;
        double dj = j - goalJ;
        return Math.sqrt(di * di + dj * dj) * step * weight;
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

    /**
     * A lattice point reached during the search.
     *
     * @param g The accumulated cost of the cheapest known path from the start to this point.
     * @param f g plus the heuristic estimate of the remaining cost to the goal.
     */
    private record Node(int i, int j, double g, double f) {
    }
}
