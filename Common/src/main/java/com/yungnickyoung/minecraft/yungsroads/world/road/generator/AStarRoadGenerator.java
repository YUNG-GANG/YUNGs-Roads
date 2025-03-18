package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.segment.DefaultRoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public class AStarRoadGenerator extends AbstractRoadGenerator {
    private static final int MAX_ITERATIONS = 1_000_000; // TODO reduce this?

    public final ServerLevel serverLevel;

    private final FastNoise jitter;

    public AStarRoadGenerator(ServerLevel serverLevel) {
        super();
        this.serverLevel = serverLevel;
        this.jitter = new FastNoise();
        this.jitter.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.jitter.SetFrequency(.05f);
        this.jitter.SetFractalOctaves(1);
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.getWorldPosition() : pos2.getWorldPosition();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.getWorldPosition() : pos1.getWorldPosition();

        // Construct road & road segments
        Road road = new Road(blockPos1, blockPos2);
        List<DefaultRoadSegment> roadSegments = generateRoadSegmentsWithAStar(blockPos1, blockPos2);
        if (roadSegments.isEmpty()) {
            // No valid road segment endpoints could be found
            return Optional.empty();
        }
        roadSegments.forEach(road::addRoadSegment);

        // Ensure road does not cross ocean
        for (DefaultRoadSegment roadSegment : road.getRoadSegments()) {
            if (serverLevel.getNoiseBiome(
                            QuartPos.fromBlock(roadSegment.getStartPos().getX()),
                            QuartPos.fromBlock(0),
                            QuartPos.fromBlock(roadSegment.getStartPos().getZ()))
                    .is(BiomeTags.IS_OCEAN)) {
                return Optional.empty();
            }
        }

        Set<BlockPos> nodePositions = new HashSet<>(); // Used to prevent duplicate nodes

        BlockPos startPos = roadSegments.get(0).getStartPos();

        // Generate road
        // We use A* to generate each segment's path
        for (int i = 0; i < roadSegments.size(); i++) {
            DefaultRoadSegment roadSegment = roadSegments.get(i);
            PriorityQueue<Node> closed = new PriorityQueue<>();
            PriorityQueue<Node> open = new PriorityQueue<>();
            BlockPos targetPos = roadSegment.getEndPos();

            // Initialize start node
            Node start = new Node(serverLevel, startPos, YungsRoadsCommon.CONFIG.advanced.path.nodeStepDistance, false);
            start.g = 0;
            start.h = Node.calcH(start, targetPos);
            start.f = start.g + start.h;
            open.add(start);

            Node finalNode = null;

            int iterations = 0;

            // A* algorithm
            // Begin generating segment path
            while (!open.isEmpty()) {
                iterations++;
                if (iterations > MAX_ITERATIONS) {
                    YungsRoadsCommon.LOGGER.info("ITERATIONS TOO LONG FOR SEGMENT AT " + startPos + " TO " + targetPos);
                    break;
                }

                Node n = open.peek();
                if (n.pos.distSqr(targetPos) <= YungsRoadsCommon.CONFIG.advanced.path.nodeStepDistance * YungsRoadsCommon.CONFIG.advanced.path.nodeStepDistance + 1) {
                    finalNode = n;
                    startPos = finalNode.pos;
                    break;
                }

                // Propagate update to neighbors
                for (Node neighbor : n.getNeighbors()) {
                    if (!isInRange(roadSegment.getStartPos(), roadSegment.getEndPos(), neighbor.pos)) {
                        continue;
                    }
                    if (!open.contains(neighbor) && !closed.contains(neighbor)) {
                        neighbor.parent = n;
                        neighbor.g = Node.calcG(serverLevel, n, neighbor);
                        neighbor.h = Node.calcH(neighbor, targetPos);
                        neighbor.f = neighbor.g + neighbor.h;
                        open.add(neighbor);
                    }
                }
                open.remove(n);
                closed.add(n);
            }

            // Store segment path
            if (finalNode != null) {
                List<Road.DebugNode> segmentNodes = new ArrayList<>();

                // Store last node
                if (!nodePositions.contains(finalNode.pos)) {
                    segmentNodes.add(new Road.DebugNode(finalNode));
                    nodePositions.add(finalNode.pos);
                }
                DebugRenderer.getInstance().addPath(new ChunkPos(finalNode.pos), null);

                // Store all other nodes until no nodes remain
                while (finalNode.parent != null) {
                    finalNode = finalNode.parent;
                    if (!nodePositions.contains(finalNode.pos)) {
                        segmentNodes.add(new Road.DebugNode(finalNode));
                        nodePositions.add(finalNode.pos);
                        DebugRenderer.getInstance().addPath(new ChunkPos(finalNode.pos), null);
                    }
                }

                // Reverse list so that it goes from start to end
                segmentNodes = Lists.reverse(segmentNodes);
                road.nodes.addAll(segmentNodes);
            }
//            YungsRoadsCommon.LOGGER.info("SEGMENT " + i + " OPEN: " + open.size() + ", CLOSED: " + closed.size() + ", ITERATIONS: " + iterations);
        }

        // Apply random jitter to all nodes to make path less straight and more natural
        this.jitter.SetSeed(road.getStartPos().getX() * 1000 + road.getStartPos().getZ());
        for (int j = 0; j < road.nodes.size(); j++) {
            Road.DebugNode debugNode = road.nodes.get(j);
            debugNode.jitteredPos = jitteredPos(debugNode.rawPos, road, j);
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
                    BlockPos jitteredPos = jitteredPos(mutable.immutable(), nodePos, nextNodePos);
                    road.positions.add(jitteredPos);
                    mutable.move(0, 0, zStepDir);
                    slopeCounter--;
                }

                // Move in x direction
                while (slopeCounter < 1 && !isWithinDistance(mutable, nextNodePos, 2)) {
                    BlockPos jitteredPos = jitteredPos(mutable.immutable(), nodePos, nextNodePos);
                    road.positions.add(jitteredPos);
                    mutable.move(xStepDir, 0, 0);
                    slopeCounter += Math.abs(nodePathSlope);
//                    slopeCounter++;
                }

                // Place path at current position
                if (!mutable.equals(nodePos) && !mutable.equals(nextNodePos)) {
                    road.positions.add(mutable.immutable());
                }
            }
        }

//        List<BlockPos> jitteredPositions = new ArrayList<>();
//        for (int j = 0; j < road.positions.size(); j++) {
//            BlockPos pos = road.positions.get(j);
//            BlockPos prevPos = j == 0 ? pos : road.positions.get(j - 1);
//            BlockPos nextPos = j == road.positions.size() - 1 ? pos : road.positions.get(j + 1);
//            jitteredPositions.add(jitteredPos(pos, prevPos, nextPos));
//        }
//        road.positions = jitteredPositions;

        return Optional.of(road);
    }

    private BlockPos jitteredPos(BlockPos pos, Road road, int i) {
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
        return jitteredPos(pos, p1, p2);
    }

    private BlockPos jitteredPos(BlockPos pos, BlockPos prevPos, BlockPos nextPos) {
        Vector3f normal = calculateNormalBetween(prevPos, nextPos);
        float jitter = (float) (this.jitter.GetNoise(pos.getX(), pos.getZ()) * YungsRoadsCommon.CONFIG.advanced.path.jitterAmount);
        Vector3f jitterOffset = new Vector3f(normal.x() * jitter, 0, normal.z() * jitter);
        return pos.offset((int) jitterOffset.x(), 0, (int) jitterOffset.z());
    }

    @Override
    public void placeRoad(Road road, WorldGenLevel level, RandomSource rand, BlockPos blockPos, RoadFeatureConfiguration config, @Nullable BlockPos nearestEndpoint) {
        // The position of the chunk we're currently confined to
        ChunkPos chunkPos = new ChunkPos(blockPos);

        // Short-circuit if this chunk isn't between the start/end points of the road
        if (!containsRoad(chunkPos, road)) {
            return;
        }

        // DEBUG line
        if (YungsRoadsCommon.CONFIG.debug.placeStraightDebugLine) {
            placeDebugLine(road, level, chunkPos, nearestEndpoint);
        }
        // Debug markers at road & road segment endpoints
        if (YungsRoadsCommon.CONFIG.debug.placeRoadEndpointDebugMarkers) {
            placeDebugMarker(level, chunkPos, road.getStartPos(), Blocks.EMERALD_BLOCK.defaultBlockState());
            placeDebugMarker(level, chunkPos, road.getEndPos(), Blocks.EMERALD_BLOCK.defaultBlockState());
        }
        if (YungsRoadsCommon.CONFIG.debug.placeRoadSegmentEndpointDebugMarkers) {
            for (DefaultRoadSegment segment : road.getRoadSegments()) {
                placeDebugMarker(level, chunkPos, segment.getStartPos(), Blocks.GOLD_BLOCK.defaultBlockState());
                placeDebugMarker(level, chunkPos, segment.getEndPos(), Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
        // Debug markers at unjittered & jittered node positions
        for (Road.DebugNode debugNode : road.nodes) {
            if (YungsRoadsCommon.CONFIG.debug.placeUnjitteredPosDebugMarkers) {
                placeDebugMarker(level, chunkPos, debugNode.rawPos, Blocks.PURPLE_WOOL.defaultBlockState());
            }
            if (YungsRoadsCommon.CONFIG.debug.placeJitteredPosDebugMarkers) {
                placeDebugMarker(level, chunkPos, debugNode.jitteredPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }

        // Place paths
        road.positions.forEach(pos -> placePath(level, rand, pos, chunkPos, config, null, nearestEndpoint));
    }

    /**
     * Places a straight line of gold blocks between the start and end points of the road.
     */
    private void placeDebugLine(Road road, WorldGenLevel level, ChunkPos chunkPos, @Nullable BlockPos nearestVillage) {
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
                DEBUGplacePath(level, mutable, chunkPos, null, nearestVillage, Blocks.GOLD_BLOCK.defaultBlockState());
                mutable.move(0, 0, zDir);
                slopeCounter--;
            }

            // Move in x direction
            while (slopeCounter < 1 && !isWithinDistance(mutable, road.getEndPos(), 10)) {
                DEBUGplacePath(level, mutable, chunkPos, null, nearestVillage, Blocks.GOLD_BLOCK.defaultBlockState());
                mutable.move(xDir, 0, 0);
                slopeCounter += Math.abs(totalSlope);
            }

            // Place path at final position
            DEBUGplacePath(level, mutable, chunkPos, null, nearestVillage, Blocks.GOLD_BLOCK.defaultBlockState());
        }
    }

    private List<DefaultRoadSegment> generateRoadSegmentsWithAStar(BlockPos startPos, BlockPos endPos) {
        double distance = Math.sqrt(startPos.distSqr(endPos));
        int stepSize = (int) (distance * YungsRoadsCommon.CONFIG.advanced.segment.nodeStepDistanceProportion);

        List<BlockPos> segmentEndpoints = new ArrayList<>();
        segmentEndpoints.add(endPos);

        // A* algorithm
        PriorityQueue<Node> closed = new PriorityQueue<>();
        PriorityQueue<Node> open = new PriorityQueue<>();

        // Initialize start node
        Node start = new Node(serverLevel, startPos, stepSize, true);
        start.g = 0;
        start.h = Segment.calcH(start, endPos);
        start.f = start.g + start.h;
        open.add(start);

        Node finalNode = null;

        int iterations = 0;

        // Begin generating segment path
        while (!open.isEmpty()) {
            iterations++;
            if (iterations > MAX_ITERATIONS) {
                YungsRoadsCommon.LOGGER.info("--ITERATIONS TOO LONG FOR SEGMENT AT " + startPos + " TO " + endPos);
                break;
            }

            Node n = open.peek();
            if (n.pos.distSqr(endPos) <= stepSize * stepSize + 1) {
                finalNode = n;
                break;
            }

            // Propagate update to neighbors
            for (Node neighbor : n.getNeighbors()) {
                if (!isInRange(startPos, endPos, neighbor.pos)) {
                    continue;
                }
                Holder<Biome> biome = serverLevel.getNoiseBiome(neighbor.pos.getX(), 0, neighbor.pos.getZ());
                if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
                    continue;
                }

//                if (getPVNoiseAt(serverLevel, neighbor.pos) < -0.9) {
//                    continue;
//                }

                if (!open.contains(neighbor) && !closed.contains(neighbor)) {
                    neighbor.parent = n;
                    neighbor.g = Segment.calcG(serverLevel, n, neighbor);
                    neighbor.h = Segment.calcH(neighbor, endPos);
                    neighbor.f = neighbor.g + neighbor.h;
                    open.add(neighbor);
                }
            }
            open.remove(n);
            closed.add(n);
        }

        if (finalNode == null) {
            return new ArrayList<>();
        }

        // Store segment path
        segmentEndpoints.add(finalNode.pos.immutable());
        while (finalNode.parent != null) {
            finalNode = finalNode.parent;
            segmentEndpoints.add(finalNode.pos.immutable());
        }

        // Reverse list so that it goes from start to end
        segmentEndpoints = Lists.reverse(segmentEndpoints);

        // Construct road segments from endpoints
        List<DefaultRoadSegment> roadSegments = new ArrayList<>();
        for (int i = 0; i < segmentEndpoints.size() - 1; i++) {
            BlockPos segmentStartPos = segmentEndpoints.get(i);
            BlockPos segmentEndPos = segmentEndpoints.get(i + 1);
            DefaultRoadSegment roadSegment = new DefaultRoadSegment(segmentStartPos, segmentEndPos);
            roadSegments.add(roadSegment);
        }
        return roadSegments;
    }

    private boolean isInRange(BlockPos startPos, BlockPos endPos, BlockPos pos) {
        int minX = Math.min(startPos.getX(), endPos.getX()) - 50;
        int maxX = Math.max(startPos.getX(), endPos.getX()) + 50;
        int minZ = Math.min(startPos.getZ(), endPos.getZ()) - 50;
        int maxZ = Math.max(startPos.getZ(), endPos.getZ()) + 50;
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public static double calcH(BlockPos nodePos, BlockPos targetPos, double hScalar) {
        int xDiff = nodePos.getX() - targetPos.getX();
        int zDiff = nodePos.getZ() - targetPos.getZ();
//        double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff); // Euclidean
//        double distance = Math.max(Math.abs(xDiff), Math.abs(zDiff)); // Chebyshev
        double distance = Math.abs(xDiff) + Math.abs(zDiff); // Manhattan

        return distance * hScalar;
    }

    public static double calcG(
            Node n,
            ServerLevel serverLevel,
            BlockPos fromPos,
            BlockPos toPos,
            int neighborPathLength,
            double pathScalar,
            double slopeFactorThreshold,
            double highSlopeFactorScalar,
            double lowSlopeFactorScalar,
            double altitudePunishmentScalar) {

        // Cost due to the length of our path, in terms of nodes.
        double pathFactor = neighborPathLength * pathScalar + 1;

        // Cost due to the slope of the path, in terms of P/V noise.
        float pv1 = getPVNoiseAt(serverLevel, fromPos) * 10f;
        float pv2 = getPVNoiseAt(serverLevel, toPos) * 10f;
        float slope = pv2 - pv1;
        float slopeFactor;
        if (pv2 > slopeFactorThreshold) {
            slopeFactor = (float) (1 + slope * slope * highSlopeFactorScalar);
        } else {
            slopeFactor = (float) (1 + slope * slope * lowSlopeFactorScalar);
        }
//        float slopeFactor = (float) ((neighbor.avgPV - node.avgPV) * YungsRoadsCommon.CONFIG.general.slopeFactorScalar);

        // Cost due to the altitude of the path. Helps prevent roads from going up mountains unnecessarily.
        float altitudePunishment = 1f;
//        if (pv2 > 5.5f) {
        altitudePunishment = (Math.abs(pv2) * Math.abs(pv2) * (float) altitudePunishmentScalar) + 1;
//        }

        float riverPunishment = 1f;
        if (pv2 < -6.5) {
            riverPunishment = 10f;
        }

//        double g = (pathFactor + slopeFactor) * altitudePunishment;
        double g = (pathFactor + slopeFactor) * altitudePunishment * riverPunishment;

        // DEBUG
        n.pathFactor = pathFactor;
        n.slopeFactor = slopeFactor;
        n.altitudePunishment = altitudePunishment;
        n.g = g;

        return g;
    }

    private boolean isWithinDistance(BlockPos pos, BlockPos targetPos, int distance) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < distance * distance;
    }

    /**
     * Calculates the normal vector of the road at the given position.
     * @param road The road.
     * @param i The index of the node in the road's nodes list.
     *          This is used to determine the previous and next nodes in the list.
     * @param pos The position to calculate the normal at.
     * @return The normalized normal vector.
     */
    private static Vector3f calculateNormalAtNode(Road road, int i, BlockPos pos) {
        BlockPos offset;
        if (i == 0) {
            offset = road.nodes.get(i + 1).rawPos.subtract(pos);
        } else if (i == road.nodes.size() - 1) {
            offset = pos.subtract(road.nodes.get(i - 1).rawPos);
        } else {
            offset = road.nodes.get(i + 1).rawPos.subtract(road.nodes.get(i - 1).rawPos);
        }
        Vector3f tangent = new Vector3f(offset.getX(), 0, offset.getZ());
        tangent.normalize();

        return new Vector3f(tangent.z(), 0, -tangent.x());
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

    public class Node implements Comparable<Node> {
        public BlockPos pos;
        public Node parent = null;

        /**
         * The cost function f(n) = g(n) + h(n), where g(n) is the cost of the path from the starting point to node n (this node),
         * and h(n) is a heuristic that estimates the cost of the cheapest path from n to the goal.
         */
        public double f = Double.MAX_VALUE;

        /**
         * The move function, the cost of moving from the starting point to this node.
         */
        public double g = Double.MAX_VALUE;

        /**
         * The heuristic function, the estimated cost of moving from this node to the goal.
         */
        public double h = Double.MAX_VALUE;

        private ServerLevel serverLevel;
        private int pathLength;
        private double avgPV;
        private int stepSize;
        private boolean hasExtraNeighbors;

        // Cached vars for DEBUG purposes
        public double pathFactor = -99, slopeFactor = 99, altitudePunishment = -99;

        public Node(ServerLevel serverLevel, BlockPos pos, int stepSize, boolean hasExtraNeighbors) {
            this.serverLevel = serverLevel;
            this.pos = pos;
            this.pathLength = 1;
            this.avgPV = getPVNoiseAt(serverLevel, pos);
            this.stepSize = stepSize;
            this.hasExtraNeighbors = hasExtraNeighbors;
        }

        private Node createChild(BlockPos childPos) {
            Node childNode = new Node(this.serverLevel, childPos, this.stepSize, this.hasExtraNeighbors);
            childNode.avgPV = (getPVNoiseAt(serverLevel, childPos) + this.avgPV * this.pathLength) / (this.pathLength + 1);
            childNode.pathLength = this.pathLength + 1;
            return childNode;
        }

        public List<Node> getNeighbors() {
            List<Node> neighbors = new ArrayList<>();

            // Cardinal directions
            neighbors.add(this.createChild(this.pos.offset(stepSize, 0, 0)));
            neighbors.add(this.createChild(this.pos.offset(-stepSize, 0, 0)));
            neighbors.add(this.createChild(this.pos.offset(0, 0, stepSize)));
            neighbors.add(this.createChild(this.pos.offset(0, 0, -stepSize)));

            // Diagonal directions
            neighbors.add(this.createChild(this.pos.offset(stepSize, 0, stepSize)));
            neighbors.add(this.createChild(this.pos.offset(stepSize, 0, -stepSize)));
            neighbors.add(this.createChild(this.pos.offset(-stepSize, 0, stepSize)));
            neighbors.add(this.createChild(this.pos.offset(-stepSize, 0, -stepSize)));

            if (hasExtraNeighbors) {
                neighbors.add(this.createChild(this.pos.offset(stepSize, 0, 2 * stepSize)));
                neighbors.add(this.createChild(this.pos.offset(2 * stepSize, 0, stepSize)));
                neighbors.add(this.createChild(this.pos.offset(2 * stepSize, 0, -stepSize)));
                neighbors.add(this.createChild(this.pos.offset(stepSize, 0, -2 * stepSize)));
                neighbors.add(this.createChild(this.pos.offset(-stepSize, 0, -2 * stepSize)));
                neighbors.add(this.createChild(this.pos.offset(-2 * stepSize, 0, -stepSize)));
                neighbors.add(this.createChild(this.pos.offset(-2 * stepSize, 0, stepSize)));
                neighbors.add(this.createChild(this.pos.offset(-stepSize, 0, 2 * stepSize)));
            }

            return neighbors;
        }

        private static double calcH(Node node, BlockPos targetPos) {
            return AStarRoadGenerator.calcH(node.pos, targetPos, YungsRoadsCommon.CONFIG.advanced.path.hScalar);
        }

        private static double calcG(ServerLevel serverLevel, Node from, Node to) {
            return AStarRoadGenerator.calcG(to, serverLevel, from.pos, to.pos, to.pathLength,
                    YungsRoadsCommon.CONFIG.advanced.path.pathScalar,
                    YungsRoadsCommon.CONFIG.advanced.path.slopeFactorThreshold,
                    YungsRoadsCommon.CONFIG.advanced.path.highSlopeFactorScalar,
                    YungsRoadsCommon.CONFIG.advanced.path.lowSlopeFactorScalar,
                    YungsRoadsCommon.CONFIG.advanced.path.altitudePunishmentScalar);
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Node other)) {
                return super.equals(obj);
            }

            return this.pos.equals(other.pos);
        }
    }

    static class Segment {
        private static double calcH(Node node, BlockPos targetPos) {
            return AStarRoadGenerator.calcH(node.pos, targetPos, YungsRoadsCommon.CONFIG.advanced.segment.hScalar);
        }

        private static double calcG(ServerLevel serverLevel, Node from, Node to) {
            return AStarRoadGenerator.calcG(to, serverLevel, from.pos, to.pos, to.pathLength,
                    YungsRoadsCommon.CONFIG.advanced.segment.pathScalar,
                    YungsRoadsCommon.CONFIG.advanced.segment.slopeFactorThreshold,
                    YungsRoadsCommon.CONFIG.advanced.segment.highSlopeFactorScalar,
                    YungsRoadsCommon.CONFIG.advanced.segment.lowSlopeFactorScalar,
                    YungsRoadsCommon.CONFIG.advanced.segment.altitudePunishmentScalar);
        }
    }
}
