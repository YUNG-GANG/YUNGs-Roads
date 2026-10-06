package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.util.GridKeys;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainSampler;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Routes roads with A* pathfinding over a world-aligned lattice of terrain samples.
 * <p>
 * The lattice is a grid of points spaced Node Step Distance blocks apart. Lattice point (x, z) is at
 * block (x * step, z * step). A road is a chain of moves between lattice points.
 * <p>
 * Each move has a cost: its horizontal length scaled by how steep it is. Moves steeper than the configured max grade
 * are not allowed at all, and oceans are impassable. The search finds the route whose moves cost the least in total.
 * Grades up to the configured free grade cost nothing extra, so on flat ground the road doesn't weave around every small
 * bump.
 * <p>
 * How the search works: it keeps a frontier of points it can reach, each with the cost of the cheapest route found to
 * it so far. It repeatedly explores the frontier point with the lowest priority, which is that cost plus an estimate
 * of the cost left to the goal, and adds that point's neighbors to the frontier. Exploring the most promising points
 * first lets it find the cheapest route without exploring the whole area. Once a point is explored, its cheapest
 * route is final.
 * <p>
 * Moves may not enter water. Instead, a point on a bank may bridge straight across the water along any move's heading,
 * landing on the first dry point. Bridges cost extra per block, so the search finds the shortest crossing worth
 * taking over a detour, and the crossing can't zigzag.
 */
public final class LatticePathfinder {
    /** Failsafe so an unreachable goal can't stall worldgen indefinitely. */
    private static final int MAX_POINTS_EXPLORED = 100_000;

    /** The search area is the endpoints' bounding box, expanded by this fraction of the road's length... */
    private static final double SEARCH_MARGIN_FRACTION = 0.25;

    /** ...but by at least this many blocks. */
    private static final int MIN_SEARCH_MARGIN = 64;

    /**
     * The moves from a lattice point to its neighbors, as offsets in lattice coordinates: the 8 surrounding points plus
     * the 8 knight moves. Knight moves allow headings between the 45-degree increments, giving less zigzagged roads.
     */
    private static final int[][] MOVES = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };

    /** The length of each move in {@link #MOVES}, in lattice units. */
    private static final double[] MOVE_LENGTHS = new double[MOVES.length];

    static {
        for (int move = 0; move < MOVES.length; move++) {
            MOVE_LENGTHS[move] = Math.sqrt(MOVES[move][0] * MOVES[move][0] + MOVES[move][1] * MOVES[move][1]);
        }
    }

    private LatticePathfinder() {
    }

    /**
     * Returns how far outside the endpoints' bounding box a road of the given length may stray, in blocks.
     */
    public static int maxSearchMargin(double roadLength, int step) {
        return (int) Math.ceil(Math.max(MIN_SEARCH_MARGIN, roadLength * SEARCH_MARGIN_FRACTION)) + step;
    }

    /**
     * Finds the cheapest route between two positions, then straightens it with {@link RouteStraightener} if enabled.
     *
     * @return The route, whose nodes run from start to end and include the exact start and end positions,
     * or empty if no route was found.
     */
    static Optional<Path> findPath(BlockPos startPos, BlockPos endPos, TerrainCache terrain) {
        ConfigModule.Advanced settings = YungsRoadsCommon.CONFIG.advanced;
        int step = terrain.step();
        int seaLevel = terrain.seaLevel();

        // The lattice points nearest the endpoints
        int startX = Math.floorDiv(startPos.getX() + step / 2, step);
        int startZ = Math.floorDiv(startPos.getZ() + step / 2, step);
        int goalX = Math.floorDiv(endPos.getX() + step / 2, step);
        int goalZ = Math.floorDiv(endPos.getZ() + step / 2, step);

        // Confine the search to the endpoints' bounding box plus a margin
        int margin = Math.ceilDiv(maxSearchMargin(Math.sqrt(startPos.distSqr(endPos)), step), step);
        Search search = new Search(step, settings.heuristicWeight, goalX, goalZ,
                Math.min(startX, goalX) - margin, Math.max(startX, goalX) + margin,
                Math.min(startZ, goalZ) - margin, Math.max(startZ, goalZ) + margin);

        long startKey = GridKeys.pack(startX, startZ);
        search.cheapestCost.put(startKey, 0);
        search.frontier.add(new FrontierEntry(startX, startZ, 0, search.weightedDistanceLeft(startX, startZ)));

        FrontierEntry reachedGoal = null;
        int pointsExplored = 0;

        while (!search.frontier.isEmpty()) {
            FrontierEntry current = search.frontier.poll();
            long currentKey = GridKeys.pack(current.x, current.z);

            // The frontier may still hold outdated entries for a point that was later reached more cheaply. Its
            // cheapest entry always comes out first, so any later one is skipped.
            if (!search.explored.add(currentKey)) {
                continue;
            }

            // Close enough to the goal. The exact end position is appended below.
            if (search.isNearGoal(current.x, current.z)) {
                reachedGoal = current;
                break;
            }

            if (++pointsExplored > MAX_POINTS_EXPLORED) {
                YungsRoadsCommon.LOGGER.warn("Road search from {} to {} explored over {} points. Skipping road.", startPos, endPos, MAX_POINTS_EXPLORED);
                return Optional.empty();
            }

            double currentHeight = roadHeight(terrain.heightAt(current.x, current.z), seaLevel);

            for (int move = 0; move < MOVES.length; move++) {
                int neighborX = current.x + MOVES[move][0];
                int neighborZ = current.z + MOVES[move][1];
                if (!search.isInBounds(neighborX, neighborZ) || search.explored.contains(GridKeys.pack(neighborX, neighborZ))) {
                    continue;
                }

                double neighborSurface = terrain.heightAt(neighborX, neighborZ);
                if (Double.isNaN(neighborSurface)) {
                    continue; // Impassable
                }

                // Water is only crossed by bridging straight over it, even from a start in water. Stepping into water
                // is still allowed next to the goal, which may be in water.
                if (isWater(neighborSurface, seaLevel)) {
                    tryBridge(search, terrain, current, currentKey, currentHeight, move, settings);
                    if (!search.isNearGoal(neighborX, neighborZ)) {
                        continue;
                    }
                }

                double run = MOVE_LENGTHS[move] * step;
                double grade = Math.abs(roadHeight(neighborSurface, seaLevel) - currentHeight) / run;
                if (grade > settings.maxGrade) {
                    continue;
                }

                double moveCost = run * slopeCostFactor(grade, settings);
                search.offerRoute(currentKey, neighborX, neighborZ, current.costSoFar + moveCost, false);
            }
        }

        if (reachedGoal == null) {
            return Optional.empty();
        }

        // Follow each point's previous point back from the goal to the start
        List<Long> keys = new ArrayList<>();
        for (long key = GridKeys.pack(reachedGoal.x, reachedGoal.z); ; key = search.previousPoint.get(key)) {
            keys.add(key);
            if (key == startKey) {
                break;
            }
        }

        List<Road.DebugNode> nodes = new ArrayList<>();
        BitSet bridgeSegments = new BitSet();
        nodes.add(new Road.DebugNode(startPos, 0, search.weightedDistanceLeft(startX, startZ)));
        for (long key : keys.reversed()) {
            int x = GridKeys.x(key);
            int z = GridKeys.z(key);
            if (search.bridgeArrivals.contains(key)) {
                // The bridge spans from the last node added to this one
                bridgeSegments.set(nodes.size() - 1);
            }
            BlockPos latticePos = new BlockPos(x * step, (int) Math.round(roadHeight(terrain.heightAt(x, z), seaLevel)), z * step);
            nodes.add(new Road.DebugNode(latticePos, search.cheapestCost.get(key), search.weightedDistanceLeft(x, z)));
        }
        nodes.add(new Road.DebugNode(endPos, reachedGoal.costSoFar, 0));

        Path path = new Path(nodes, bridgeSegments);
        return Optional.of(settings.straightenRoutes ? RouteStraightener.straighten(path, terrain, settings) : path);
    }

    /**
     * Tries to bridge from a point straight over the water along the given move's heading, landing on the first
     * dry lattice point within the max bridge length. Bridges cost extra per block of their full length, so the search
     * prefers the shortest crossing. Gives up at oceans, at the search area's edge, if the landing is too steep to
     * reach, or if there's no landing in range.
     *
     * @param move The index of the move in {@link #MOVES} whose heading the bridge follows.
     */
    private static void tryBridge(Search search, TerrainCache terrain, FrontierEntry from, long fromKey, double fromHeight,
                                  int move, ConfigModule.Advanced settings) {
        int moveX = MOVES[move][0];
        int moveZ = MOVES[move][1];
        double moveLength = MOVE_LENGTHS[move] * search.step;
        int seaLevel = terrain.seaLevel();

        // Repeat the move until it lands on dry ground
        for (int movesAcross = 1; movesAcross * moveLength <= settings.maxBridgeLength; movesAcross++) {
            int x = from.x + movesAcross * moveX;
            int z = from.z + movesAcross * moveZ;
            if (!search.isInBounds(x, z) || knightMoveCrossesOcean(terrain, x - moveX, z - moveZ, moveX, moveZ)) {
                return;
            }

            double surface = terrain.heightAt(x, z);
            if (Double.isNaN(surface)) {
                return;
            }
            if (isWater(surface, seaLevel)) {
                continue;
            }

            // Reached the far bank
            double run = movesAcross * moveLength;
            double grade = Math.abs(roadHeight(surface, seaLevel) - fromHeight) / run;
            if (grade <= settings.maxGrade && !search.explored.contains(GridKeys.pack(x, z))) {
                double bridgeCost = run * (slopeCostFactor(grade, settings) + settings.waterWeight);
                search.offerRoute(fromKey, x, z, from.costSoFar + bridgeCost, true);
            }
            return;
        }
    }

    /**
     * Whether a knight move from the given lattice point passes over ocean. The move passes between the two lattice
     * points beside its midpoint, which aren't otherwise sampled. Other moves only pass over their endpoints' cells.
     */
    private static boolean knightMoveCrossesOcean(TerrainCache terrain, int x, int z, int moveX, int moveZ) {
        if (Math.abs(moveX) + Math.abs(moveZ) != 3) {
            return false;
        }
        int midX = x + moveX / 2;
        int midZ = z + moveZ / 2;
        return Double.isNaN(terrain.heightAt(midX, midZ)) || Double.isNaN(terrain.heightAt(midX + moveX % 2, midZ + moveZ % 2));
    }

    static boolean isWater(double surfaceHeight, int seaLevel) {
        return TerrainSampler.isUnderwater(surfaceHeight, seaLevel);
    }

    /**
     * The height a road would sit at above the given surface height.
     * Over water, roads sit at sea level as bridges rather than following the riverbed or lake floor.
     */
    static double roadHeight(double surfaceHeight, int seaLevel) {
        return Double.isNaN(surfaceHeight) ? seaLevel : Math.max(surfaceHeight, seaLevel);
    }

    /**
     * A point in the search's frontier, waiting to be explored.
     *
     * @param x The point's lattice x coordinate.
     * @param z The point's lattice z coordinate.
     * @param costSoFar The cost of the cheapest route found from the start to this point, when it was added.
     * @param priority The cost so far plus the estimated cost left to the goal. Lower is explored first.
     */
    private record FrontierEntry(int x, int z, double costSoFar, double priority) {
    }

    /**
     * Whether the straight line between two positions passes over water, checked at every block between them.
     */
    static boolean crossesWater(BlockPos from, BlockPos to, TerrainCache terrain) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        int length = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
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

    /**
     * How much a run of road with the given grade costs per block of length. The extra cost of steepness grows with the
     * square of the grade, less that of the free grade, so it starts at zero there and is nearly unchanged on steep
     * slopes, where it makes roads wind up hillsides in switchbacks.
     */
    public static double slopeCostFactor(double grade, ConfigModule.Advanced settings) {
        double freeGrade = settings.freeGrade;
        return 1 + settings.slopeWeight * Math.max(0, grade * grade - freeGrade * freeGrade);
    }

    /**
     * A found route.
     * @param nodes The route's nodes from start to end, including the exact start and end positions.
     * @param bridgeSegments The segments that bridge water, each set at the index of the node it starts from. Each
     *                       spans two consecutive lattice nodes.
     */
    record Path(List<Road.DebugNode> nodes, BitSet bridgeSegments) {
    }

    /**
     * The state of one search over the lattice, confined to a rectangle of lattice points. Points are keyed by their
     * lattice coordinates packed into a long with {@link GridKeys#pack}.
     */
    private static final class Search {
        final int step;
        final double heuristicWeight;
        final int goalX, goalZ;
        final int minX, maxX, minZ, maxZ;

        /** The points that can be explored next, lowest priority first. */
        final PriorityQueue<FrontierEntry> frontier = new PriorityQueue<>(Comparator.comparingDouble(FrontierEntry::priority));

        /** The cost of the cheapest route found to each point reached so far. */
        final Long2DoubleOpenHashMap cheapestCost = new Long2DoubleOpenHashMap();

        /** The point each point's cheapest route arrives from, for tracing the route back once the goal is reached. */
        final Long2LongOpenHashMap previousPoint = new Long2LongOpenHashMap();

        /** The points already explored, whose cheapest routes are final. */
        final LongOpenHashSet explored = new LongOpenHashSet();

        /** Points whose cheapest route arrives over a bridge from their previous point. */
        final LongOpenHashSet bridgeArrivals = new LongOpenHashSet();

        Search(int step, double heuristicWeight, int goalX, int goalZ, int minX, int maxX, int minZ, int maxZ) {
            this.step = step;
            this.heuristicWeight = heuristicWeight;
            this.goalX = goalX;
            this.goalZ = goalZ;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.cheapestCost.defaultReturnValue(Double.POSITIVE_INFINITY);
        }

        boolean isInBounds(int x, int z) {
            return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
        }

        boolean isNearGoal(int x, int z) {
            return Math.abs(x - this.goalX) <= 1 && Math.abs(z - this.goalZ) <= 1;
        }

        /**
         * The estimated cost left from a point to the goal: the straight-line distance in blocks, times the heuristic
         * weight. Every move costs at least its length, so with a weight of 1 this never overestimates, and the search
         * always finds the cheapest route. Higher weights rush toward the goal, trading route quality for speed.
         */
        double weightedDistanceLeft(int x, int z) {
            double dx = x - this.goalX;
            double dz = z - this.goalZ;
            return Math.sqrt(dx * dx + dz * dz) * this.step * this.heuristicWeight;
        }

        /**
         * Records a route to the given point with the given total cost, if it's cheaper than the cheapest one found so
         * far, and adds the point to the frontier.
         */
        void offerRoute(long fromKey, int x, int z, double cost, boolean isBridge) {
            long key = GridKeys.pack(x, z);
            if (cost >= this.cheapestCost.get(key)) {
                return;
            }
            this.cheapestCost.put(key, cost);
            this.previousPoint.put(key, fromKey);
            if (isBridge) {
                this.bridgeArrivals.add(key);
            } else {
                this.bridgeArrivals.remove(key);
            }
            this.frontier.add(new FrontierEntry(x, z, cost, cost + weightedDistanceLeft(x, z)));
        }
    }
}
