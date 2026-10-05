package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.RoadBlockWriter;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainSampler;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
 * The cost of each step is its horizontal length scaled by how steep it is, summed along the path. Steps steeper than
 * the configured max grade are not allowed at all. Oceans are impassable.
 * <p>
 * Steps may not enter water. Instead, a bank node may bridge straight across the water along any move's heading,
 * landing on the first dry point. Bridges cost extra per block, so the search finds the shortest crossing worth
 * taking over a detour, and the crossing can't zigzag. Steps that turn out to cross water too narrow for the lattice
 * to see, such as streams, are made bridges after routing.
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
        Optional<Path> path = findPath(road.getStartPos(), road.getEndPos(), terrain);
        if (path.isEmpty()) {
            return Optional.empty();
        }

        road.nodes.addAll(path.get().nodes);
        road.bridges.addAll(path.get().bridges);

        // Routing only checks for water at lattice points, so a step can cross water too narrow for one to land in,
        // such as a stream. Make those steps bridges too, so that every water crossing is straight.
        boolean[] isBridgeSegment = new boolean[road.nodes.size() - 1];
        List<Road.Bridge> bridges = new ArrayList<>();
        for (int n = 0; n < isBridgeSegment.length; n++) {
            BlockPos from = road.nodes.get(n).rawPos;
            BlockPos to = road.nodes.get(n + 1).rawPos;
            isBridgeSegment[n] = road.isBridgeSegment(n) || crossesWater(from, to, terrain);
            if (isBridgeSegment[n]) {
                bridges.add(new Road.Bridge(atDeckHeight(from, terrain), atDeckHeight(to, terrain)));
            }
        }
        road.bridges = bridges;

        // Apply random jitter to all nodes to make path less straight and more natural.
        // Bridge ends aren't jittered, so bridges stay straight and land where routing checked for dry ground.
        // The noise is created per road since its seed is mutable state and roads may be generated concurrently.
        FastNoise jitter = createJitterNoise(road.getStartPos());
        for (int j = 0; j < road.nodes.size(); j++) {
            Road.DebugNode debugNode = road.nodes.get(j);
            boolean isBridgeEnd = (j > 0 && isBridgeSegment[j - 1]) || (j < isBridgeSegment.length && isBridgeSegment[j]);
            debugNode.jitteredPos = isBridgeEnd ? debugNode.rawPos : jitteredPos(jitter, debugNode.rawPos, road, j);
        }

        // Store ALL jittered block positions for iteration later when placing
        for (int i = 0; i < road.nodes.size(); i++) {
            Road.DebugNode debugNode = road.nodes.get(i);
            BlockPos nodePos = debugNode.jitteredPos;

            road.positions.add(nodePos);

            if (i == road.nodes.size() - 1) continue;

            boolean straight = isBridgeSegment[i];

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

        RoadProfile.apply(road, terrain, YungsRoadsCommon.CONFIG.advanced);
        return Optional.of(road);
    }

    /**
     * Finds the cheapest path between two positions with A*.
     *
     * @return The path, whose nodes run from start to end and include the exact start and end positions,
     * or empty if no path was found.
     */
    private static Optional<Path> findPath(BlockPos startPos, BlockPos endPos, TerrainCache terrain) {
        ConfigModule.Advanced settings = YungsRoadsCommon.CONFIG.advanced;
        int step = terrain.step();
        int seaLevel = terrain.seaLevel();

        int startI = Math.floorDiv(startPos.getX() + step / 2, step);
        int startJ = Math.floorDiv(startPos.getZ() + step / 2, step);
        int goalI = Math.floorDiv(endPos.getX() + step / 2, step);
        int goalJ = Math.floorDiv(endPos.getZ() + step / 2, step);

        // Confine the search to the endpoints' bounding box plus a margin
        int margin = Math.ceilDiv(maxSearchMargin(Math.sqrt(startPos.distSqr(endPos)), step), step);
        Search search = new Search(step, settings.heuristicWeight, goalI, goalJ,
                Math.min(startI, goalI) - margin, Math.max(startI, goalI) + margin,
                Math.min(startJ, goalJ) - margin, Math.max(startJ, goalJ) + margin);

        long startKey = ChunkPos.asLong(startI, startJ);
        search.bestG.put(startKey, 0);
        search.open.add(new Node(startI, startJ, 0, search.heuristic(startI, startJ)));

        Node finalNode = null;
        int expansions = 0;

        while (!search.open.isEmpty()) {
            Node node = search.open.poll();
            long nodeKey = ChunkPos.asLong(node.i, node.j);

            // The queue may hold stale duplicates of a node that was later reached more cheaply
            if (!search.closed.add(nodeKey)) {
                continue;
            }

            // Close enough to the goal. The exact end position is appended below.
            if (search.isNearGoal(node.i, node.j)) {
                finalNode = node;
                break;
            }

            if (++expansions > MAX_EXPANSIONS) {
                YungsRoadsCommon.LOGGER.warn("Road search from {} to {} exceeded {} expansions. Skipping road.", startPos, endPos, MAX_EXPANSIONS);
                return Optional.empty();
            }

            double nodeHeight = roadHeight(terrain.heightAt(node.i, node.j), seaLevel);

            for (int m = 0; m < MOVES.length; m++) {
                int ni = node.i + MOVES[m][0];
                int nj = node.j + MOVES[m][1];
                if (!search.isInBounds(ni, nj) || search.closed.contains(ChunkPos.asLong(ni, nj))) {
                    continue;
                }

                double neighborSurface = terrain.heightAt(ni, nj);
                if (Double.isNaN(neighborSurface)) {
                    continue; // Impassable
                }

                // Water is only crossed by bridging straight over it, even from a start in water. Stepping into water
                // is still allowed next to the goal, which may be in water.
                if (isWater(neighborSurface, seaLevel)) {
                    tryBridge(search, terrain, node, nodeKey, nodeHeight, m, settings);
                    if (!search.isNearGoal(ni, nj)) {
                        continue;
                    }
                }

                double run = MOVE_LENGTHS[m] * step;
                double grade = Math.abs(roadHeight(neighborSurface, seaLevel) - nodeHeight) / run;
                if (grade > settings.maxGrade) {
                    continue;
                }

                search.relax(nodeKey, ni, nj, node.g + run * (1 + settings.slopeWeight * grade * grade), false);
            }
        }

        if (finalNode == null) {
            return Optional.empty();
        }

        // Walk back from the final node to the start
        List<Long> keys = new ArrayList<>();
        for (long key = ChunkPos.asLong(finalNode.i, finalNode.j); ; key = search.parents.get(key)) {
            keys.add(key);
            if (key == startKey) {
                break;
            }
        }

        List<Road.DebugNode> nodes = new ArrayList<>();
        List<Road.Bridge> bridges = new ArrayList<>();
        nodes.add(new Road.DebugNode(startPos, 0, search.heuristic(startI, startJ)));
        BlockPos prevLatticePos = null;
        for (long key : keys.reversed()) {
            int i = ChunkPos.getX(key);
            int j = ChunkPos.getZ(key);
            BlockPos latticePos = new BlockPos(i * step, (int) Math.round(roadHeight(terrain.heightAt(i, j), seaLevel)), j * step);
            if (search.bridgeArrivals.contains(key)) {
                bridges.add(new Road.Bridge(prevLatticePos, latticePos));
            }
            nodes.add(new Road.DebugNode(latticePos, search.bestG.get(key), search.heuristic(i, j)));
            prevLatticePos = latticePos;
        }
        nodes.add(new Road.DebugNode(endPos, finalNode.g, 0));

        return Optional.of(new Path(nodes, bridges));
    }

    /**
     * Tries to bridge from a node straight over the water along the given move's heading, landing on the first
     * dry lattice point within the max bridge length. Bridges cost extra per block of their full length, so the search
     * prefers the shortest crossing. Gives up at oceans, at the search area's edge, if the landing is too steep to
     * reach, or if there's no landing in range.
     */
    private static void tryBridge(Search search, TerrainCache terrain, Node node, long nodeKey, double nodeHeight,
                                  int m, ConfigModule.Advanced settings) {
        int di = MOVES[m][0];
        int dj = MOVES[m][1];
        double moveLength = MOVE_LENGTHS[m] * search.step;
        int seaLevel = terrain.seaLevel();

        for (int t = 1; t * moveLength <= settings.maxBridgeLength; t++) {
            int i = node.i + t * di;
            int j = node.j + t * dj;
            if (!search.isInBounds(i, j) || knightMoveCrossesOcean(terrain, i - di, j - dj, di, dj)) {
                return;
            }

            double surface = terrain.heightAt(i, j);
            if (Double.isNaN(surface)) {
                return;
            }
            if (isWater(surface, seaLevel)) {
                continue;
            }

            // Reached the far bank
            double run = t * moveLength;
            double grade = Math.abs(roadHeight(surface, seaLevel) - nodeHeight) / run;
            if (grade <= settings.maxGrade && !search.closed.contains(ChunkPos.asLong(i, j))) {
                search.relax(nodeKey, i, j, node.g + run * (1 + settings.slopeWeight * grade * grade + settings.waterWeight), true);
            }
            return;
        }
    }

    /**
     * Whether a knight move from the given lattice point passes over ocean. The move passes between the two lattice
     * points beside its midpoint, which aren't otherwise sampled. Other moves only pass over their endpoints' cells.
     */
    private static boolean knightMoveCrossesOcean(TerrainCache terrain, int i, int j, int di, int dj) {
        if (Math.abs(di) + Math.abs(dj) != 3) {
            return false;
        }
        int midI = i + di / 2;
        int midJ = j + dj / 2;
        return Double.isNaN(terrain.heightAt(midI, midJ)) || Double.isNaN(terrain.heightAt(midI + di % 2, midJ + dj % 2));
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
            if (isWater(terrain.surfaceHeightAtBlock(x, z), terrain.seaLevel())) {
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
        return pos.atY((int) Math.round(roadHeight(terrain.surfaceHeightAtBlock(pos.getX(), pos.getZ()), terrain.seaLevel())));
    }

    private static boolean isWater(double surfaceHeight, int seaLevel) {
        return TerrainSampler.isUnderwater(surfaceHeight, seaLevel);
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

    /**
     * A found path.
     * @param nodes The path's nodes from start to end, including the exact start and end positions.
     * @param bridges The path's bridges from start to end. Each spans two consecutive lattice nodes.
     */
    private record Path(List<Road.DebugNode> nodes, List<Road.Bridge> bridges) {
    }

    /**
     * The state of one A* search over the lattice, confined to a rectangle of lattice points.
     */
    private static final class Search {
        final int step;
        final double heuristicWeight;
        final int goalI, goalJ;
        final int minI, maxI, minJ, maxJ;

        final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        final Long2DoubleOpenHashMap bestG = new Long2DoubleOpenHashMap();
        final Long2LongOpenHashMap parents = new Long2LongOpenHashMap();
        final LongOpenHashSet closed = new LongOpenHashSet();

        /** Points whose cheapest known route arrives over a bridge from their parent. */
        final LongOpenHashSet bridgeArrivals = new LongOpenHashSet();

        Search(int step, double heuristicWeight, int goalI, int goalJ, int minI, int maxI, int minJ, int maxJ) {
            this.step = step;
            this.heuristicWeight = heuristicWeight;
            this.goalI = goalI;
            this.goalJ = goalJ;
            this.minI = minI;
            this.maxI = maxI;
            this.minJ = minJ;
            this.maxJ = maxJ;
            this.bestG.defaultReturnValue(Double.POSITIVE_INFINITY);
        }

        boolean isInBounds(int i, int j) {
            return i >= this.minI && i <= this.maxI && j >= this.minJ && j <= this.maxJ;
        }

        boolean isNearGoal(int i, int j) {
            return Math.abs(i - this.goalI) <= 1 && Math.abs(j - this.goalJ) <= 1;
        }

        double heuristic(int i, int j) {
            return AStarRoadGenerator.heuristic(i, j, this.goalI, this.goalJ, this.step, this.heuristicWeight);
        }

        /**
         * Records a route to the given point with the given cost, if it's cheaper than the best known one.
         */
        void relax(long fromKey, int i, int j, double g, boolean isBridge) {
            long key = ChunkPos.asLong(i, j);
            if (g >= this.bestG.get(key)) {
                return;
            }
            this.bestG.put(key, g);
            this.parents.put(key, fromKey);
            if (isBridge) {
                this.bridgeArrivals.add(key);
            } else {
                this.bridgeArrivals.remove(key);
            }
            this.open.add(new Node(i, j, g, g + heuristic(i, j)));
        }
    }
}
