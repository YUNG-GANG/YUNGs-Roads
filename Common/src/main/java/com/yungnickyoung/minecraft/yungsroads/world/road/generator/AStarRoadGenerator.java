package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.google.common.collect.Lists;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.mixin.accessor.NoiseBasedChunkGeneratorAccessor;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.segment.DefaultRoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.TerrainShaper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;

public class AStarRoadGenerator extends AbstractRoadGenerator {
    public final ServerLevel serverLevel;

    public AStarRoadGenerator(ServerLevel serverLevel) {
        super();
        this.serverLevel = serverLevel;
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.getWorldPosition() : pos2.getWorldPosition();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.getWorldPosition() : pos1.getWorldPosition();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();
        int length = (int) Mth.sqrt(Mth.square(xDist) + Mth.square(zDist));

        // Construct road & road segments
        Road road = new Road(blockPos1, blockPos2);
        float slope = (blockPos2.getZ() - blockPos1.getZ()) / (float) (blockPos2.getX() - blockPos1.getX());

        final int minSegmentLength = 40;
        final int maxSegmentLength = 80;

        float startPV = getPVNoiseAt(road.getVillageStart());
        float endPV = getPVNoiseAt(road.getVillageEnd());
        float pvDiff = endPV - startPV;

        // Construct maps of average and actual P/V noise values along road
        Map<Integer, Float> idealPvMap = new HashMap<>();
        Map<Integer, Float> actualPvMap = new HashMap<>();
        for (int i = 0; i <= length; i++) {
            float fraction = (float) i / length;
            int x = (int) (blockPos1.getX() + fraction * xDist);
            int z = (int) (blockPos1.getZ() + fraction * zDist);
//            pvMap.put(x, getPVNoiseAt(new BlockPos(x, 0, z)));
            float idealPV = startPV + fraction * pvDiff;
            float actualPV = getPVNoiseAt(new BlockPos(x, 0, z));
            idealPvMap.put(x, idealPV);
            actualPvMap.put(x, actualPV);
        }

        int counter = 0;
        BlockPos.MutableBlockPos segmentStartPos = road.getVillageStart().mutable();
        BlockPos.MutableBlockPos segmentEndPos = new BlockPos.MutableBlockPos();
        while (counter < length) {
            float minPVDifference = Float.MAX_VALUE;
            BlockPos idealSegmentEndPos = null;
            int chosenSegmentLength = 0;

            // Find the most suitable point along the road to finish this segment.
            for (int segmentLength = minSegmentLength; segmentLength < maxSegmentLength; segmentLength++) {
                float fraction = (float) segmentLength / length;
                if (fraction > 1) fraction = 1; // Don't go past the end of the road
                int segEndX = (int) (segmentStartPos.getX() + fraction * xDist);
                int segEndZ = (int) (segmentStartPos.getZ() + fraction * zDist);
                if (segEndX > blockPos2.getX()) { // Don't go past the end of the road
                    segEndX = blockPos2.getX();
                    segEndZ = blockPos2.getZ();
                }
                segmentEndPos.set(segEndX, 0, segEndZ);

//                float pv = getPVNoiseAt(segmentEndPos);
                float pvDifferenceFromIdeal = Math.abs(actualPvMap.get(segEndX) - idealPvMap.get(segEndX));

                if (pvDifferenceFromIdeal < minPVDifference) {
                    minPVDifference = pvDifferenceFromIdeal;
                    idealSegmentEndPos = segmentEndPos.immutable();
                    chosenSegmentLength = segmentLength;
                }

                if (counter + segmentLength >= length) break; // Don't go past the end of the road
            }

            // Add road segment
            DefaultRoadSegment roadSegment = new DefaultRoadSegment(segmentStartPos.immutable(), idealSegmentEndPos);
            road.addRoadSegment(roadSegment);

            segmentStartPos.set(roadSegment.getEndPos());
            counter += chosenSegmentLength;
        }


//        int counter = 0;
//        while (counter < length) {
//            // Find the point along this segment with a P/V noise closest to what we're looking for
//
//
//
//            int newCounter = counter + minSegmentLength + new Random().nextInt(maxSegmentLength - minSegmentLength);
//            if (newCounter > length) newCounter = length;
//
//            float p0 = (float) counter / length;
//            float p1 = (float) newCounter / length;
//            road.addRoadSegment(
//                    blockPos1.offset(xDist * p0, 0, zDist * p0),
//                    blockPos1.offset(xDist * p1, 0, zDist * p1));
//
//            counter = newCounter;
//        }



//        final int segmentLength = 80;
//
//        int counter = 0;
//        while (counter < length) {
//            int newCounter = counter + segmentLength;
//            if (newCounter > length) newCounter = length;
//
//            float p0 = (float) counter / length;
//            float p1 = (float) newCounter / length;
//            road.addRoadSegment(
//                    blockPos1.offset(xDist * p0, 0, zDist * p0),
//                    blockPos1.offset(xDist * p1, 0, zDist * p1));
//
//            counter = newCounter;
//        }

        //        int numSegments = sqrLength / 50;
//        for (int i = 0; i < numSegments; i++) {
//            road.addRoadSegment(
//                    blockPos1.offset(xDist * i / numSegments, 0, zDist * i / numSegments),
//                    blockPos1.offset(xDist * (i + 1) / numSegments, 0, zDist * (i + 1) / numSegments));
//        }

//        road.addRoadSegment(blockPos1, blockPos2);

        // Ensure road does not cross ocean
        for (DefaultRoadSegment roadSegment : road.getRoadSegments()) {
            if (serverLevel.getChunkSource().getGenerator().getNoiseBiome(
                            QuartPos.fromBlock(roadSegment.getStartPos().getX()),
                            QuartPos.fromBlock(0),
                            QuartPos.fromBlock(roadSegment.getStartPos().getZ()))
                    .is(BiomeTags.IS_OCEAN)) {
                return Optional.empty();
            }
        }


        // generate road

//        road = new Road(new BlockPos(0, 0, 0), new BlockPos(20, 0, 20));

        // A* algorithm
        int i = 0;
        for (DefaultRoadSegment roadSegment : road.getRoadSegments()) {
            i++;
            PriorityQueue<Node> closed = new PriorityQueue<>();
            PriorityQueue<Node> open = new PriorityQueue<>();
//            BlockPos targetPos = road.getVillageEnd();
            BlockPos targetPos = roadSegment.getEndPos();

            // Initialize start node
//            Node start = new Node(road.getVillageStart().mutable());
            Node start = new Node(roadSegment.getStartPos());
            start.g = 0;
            start.f = start.g + calcH(start, targetPos, serverLevel);
            open.add(start);

            Node finalNode = null;

            int iterations = 0;

            // Begin generating path
            while (!open.isEmpty()) {
                iterations++;
                if (iterations > 1_000_000) {
                    YungsRoadsCommon.LOGGER.info("ITERATIONS TOO LONG FOR SEGMENT AT " + roadSegment.getStartPos() + " TO " + roadSegment.getEndPos());
                    break;
                }

                Node n = open.peek();
                if (n.pos.distSqr(targetPos) <= 10) {
                    finalNode = n;
                    break;
                }

                // Propagate update to neighbors
                for (Node m : n.getNeighbors()) {
//                if (m.pos.getX() > targetPos.getX()) continue;
                    if (!isInRange(roadSegment, m.pos)) continue;

//                    double totalWeight = n.g + 1;
//                double totalWeight = n.g + calcG(m, n.pos);
//                    double totalWeight = n.g + 1;

//                    if (!open.contains(m) && !closed.contains(m)) {
//                        m.parent = n;
//                        m.g = totalWeight;
//                        m.f = m.g + calcH(m, targetPos, serverLevel);
//                        open.add(m);
//                    } else {
//                        if (totalWeight < m.g) {
//                            m.parent = n;
//                            m.g = totalWeight;
//                            m.f = m.g + calcH(m, targetPos, serverLevel);
//
//                            if (closed.contains(m)) {
//                                closed.remove(m);
//                                open.add(m);
//                            }
//                        }
//                    }
                    if (!open.contains(m) && !closed.contains(m)) {
                        double moveCost = calcG(n, m);
                        m.parent = n;
                        m.g = moveCost;
                        m.f = m.g + calcH(m, targetPos, serverLevel);
                        open.add(m);
                    }
                }
//                }

                open.remove(n);
                closed.add(n);
            }

            // Place path
            if (finalNode != null) {
                // Place last path
//            this.placePath(level, rand, finalNode.pos, chunkPos, config);
                road.positions.add(finalNode.pos.immutable());
                if (YungsRoadsCommon.DEBUG_MODE) {
                    DebugRenderer.getInstance().addPath(new ChunkPos(finalNode.pos), null);
                }

                // Place all other node paths until no nodes remain
                while (finalNode.parent != null) {
                    finalNode = finalNode.parent;
//                this.placePath(level, rand, finalNode.pos, chunkPos, config);
                    road.positions.add(finalNode.pos.immutable());
                    if (YungsRoadsCommon.DEBUG_MODE) {
                        DebugRenderer.getInstance().addPath(new ChunkPos(finalNode.pos), null);
                    }
                }
            }

            YungsRoadsCommon.LOGGER.info("SEGMENT " + i + " OPEN: " + open.size() + ", CLOSED: " + closed.size() + ", ITERATIONS: " + iterations);
        }


        // Determine total slope of line from starting point to ending point
//        int totalXDiff = road.getVillageEnd().getX() - road.getVillageStart().getX();
//        int totalZDiff = road.getVillageEnd().getZ() - road.getVillageStart().getZ();
//        double totalSlope = totalXDiff == 0 ? Integer.MAX_VALUE : totalZDiff / (double) totalXDiff;
//        int xDir = totalXDiff >= 0 ? 1 : -1; // x direction multiplier
//        int zDir = totalZDiff >= 0 ? 1 : -1; // z direction multiplier
//
//        double slopeCounter = Math.abs(totalSlope);
//        BlockPos.MutableBlockPos mutable = road.getVillageStart().mutable();
//
//        while (!isWithin10Blocks(mutable, road.getVillageEnd())) {
//            // Move in z direction
//            while (slopeCounter >= 1 && !isWithin10Blocks(mutable, road.getVillageEnd())) {
////                placePath(level, rand, mutable, chunkPos, config, null, nearestVillage);
//                road.positions.add(mutable.immutable());
//                if (YungsRoadsCommon.DEBUG_MODE) {
//                    DebugRenderer.getInstance().addPath(new ChunkPos(mutable), null);
//                }
//                mutable.move(0, 0, zDir);
//                slopeCounter--;
//            }
//
//            // Move in x direction
//            while (slopeCounter < 1 && !isWithin10Blocks(mutable, road.getVillageEnd())) {
////                placePath(level, rand, mutable, chunkPos, config, null, nearestVillage);
//                road.positions.add(mutable.immutable());
//                if (YungsRoadsCommon.DEBUG_MODE) {
//                    DebugRenderer.getInstance().addPath(new ChunkPos(mutable), null);
//                }
//                mutable.move(xDir, 0, 0);
//                slopeCounter += Math.abs(totalSlope);
//            }
//
//            // Place path at final position
////            placePath(level, rand, mutable, chunkPos, config, null, nearestVillage);
//            road.positions.add(mutable.immutable());
//            if (YungsRoadsCommon.DEBUG_MODE) {
//                DebugRenderer.getInstance().addPath(new ChunkPos(mutable), null);
//            }
//        }


        return Optional.of(road);
    }

    private boolean isInRange(Road road, BlockPos pos) {
        int minX = Math.min(road.getVillageStart().getX(), road.getVillageEnd().getX());
        int maxX = Math.max(road.getVillageStart().getX(), road.getVillageEnd().getX());
        int minZ = Math.min(road.getVillageStart().getZ(), road.getVillageEnd().getZ());
        int maxZ = Math.max(road.getVillageStart().getZ(), road.getVillageEnd().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private boolean isInRange(DefaultRoadSegment roadSegment, BlockPos pos) {
        int minX = Math.min(roadSegment.getStartPos().getX(), roadSegment.getEndPos().getX()) - 50;
        int maxX = Math.max(roadSegment.getStartPos().getX(), roadSegment.getEndPos().getX()) + 50;
        int minZ = Math.min(roadSegment.getStartPos().getZ(), roadSegment.getEndPos().getZ()) - 50;
        int maxZ = Math.max(roadSegment.getStartPos().getZ(), roadSegment.getEndPos().getZ()) + 50;
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private boolean isWithin10Blocks(BlockPos pos, BlockPos targetPos) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < 10 * 10;
    }

    @Override
    public void placeRoad(Road road, WorldGenLevel level, Random rand, BlockPos blockPos, RoadFeatureConfiguration config, @Nullable BlockPos nearestVillage) {
        // The position of the chunk we're currently confined to
        ChunkPos chunkPos = new ChunkPos(blockPos);

        // Short-circuit if this chunk isn't between the start/end points of the road
        if (!containsRoad(chunkPos, road)) {
            return;
        }

        // Debug markers at road endpoints
        if (YungsRoadsCommon.DEBUG_MODE) {
            placeDebugMarker(level, chunkPos, road.getVillageStart(), Blocks.EMERALD_BLOCK.defaultBlockState());
            placeDebugMarker(level, chunkPos, road.getVillageEnd(), Blocks.EMERALD_BLOCK.defaultBlockState());

            for (DefaultRoadSegment segment : road.getRoadSegments()) {
                placeDebugMarker(level, chunkPos, segment.getStartPos(), Blocks.GOLD_BLOCK.defaultBlockState());
                placeDebugMarker(level, chunkPos, segment.getEndPos(), Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }


        for (int i = 0; i < road.positions.size(); i++) {
            BlockPos pos = road.positions.get(i);
            placePath(level, rand, pos, chunkPos, config, null, nearestVillage);

            if (i < road.positions.size() - 1) {
                BlockPos nextPos = road.positions.get(i + 1);
                BlockPos middlePos = new BlockPos((pos.getX() + nextPos.getX()) / 2, 0, (pos.getZ() + nextPos.getZ()) / 2);
                placePath(level, rand, middlePos, chunkPos, config, null, nearestVillage);
            }
        }


        // Determine road segments we need to process
        List<DefaultRoadSegment> roadSegments = new ArrayList<>();
        for (DefaultRoadSegment roadSegment : road.getRoadSegments()) {
            if (containsRoadSegment(chunkPos, roadSegment)) {
                roadSegments.add(roadSegment);
            }
        }

//        for (DefaultRoadSegment roadSegment : roadSegments) {
//            // A* algorithm
//            PriorityQueue<Node> closed = new PriorityQueue<>();
//            PriorityQueue<Node> open = new PriorityQueue<>();
//            BlockPos.MutableBlockPos targetPos = roadSegment.getEndPos().mutable();
////            targetPos.setY(getSurfaceHeight(world, targetPos));
//
//            // Initialize start node
//            Node start = new Node(roadSegment.getStartPos().mutable());
////            if (isInChunk(chunkPos, start.pos)) {
////                start.pos.setY(getSurfaceHeight(world, start.pos));
////            }
//
//            start.g = 0;
//            start.f = start.g + calcH(start, targetPos);
//
//            open.add(start);
//            Node path = null;

        // Begin generating path
//            while (!open.isEmpty()) {
//                Node n = open.peek();
////                if (n.pos == roadSegment.getEndPos()) {
//                if (n.pos.distSqr(targetPos) <= 10) {
//                    path = n;
//                    break;
//                }
//
//                // Propagate update to neighbors
//                for (Node m : n.getNeighbors()) {
//                    if (m.pos.getX() > targetPos.getX()) continue;
//
////                    if (isInChunk(chunkPos, m.pos)) {
////                        m.pos.setY(getSurfaceHeight(world, m.pos));
////                    }
//                    double totalWeight = n.g + calcG(m, n.pos);
////                    double totalWeight = n.g + 1;
//
//                    if (!open.contains(m) && !closed.contains(m)) {
//                        m.parent = n;
//                        m.g = totalWeight;
//                        m.f = m.g + calcH(m, targetPos);
//                        open.add(m);
//                    } else {
//                        if (totalWeight < m.g) {
//                            m.parent = n;
//                            m.g = totalWeight;
//                            m.f = m.g + calcH(m, targetPos);
//
//                            if (closed.contains(m)) {
//                                closed.remove(m);
//                                open.add(m);
//                            }
//                        }
//                    }
//                }
////                }
//
//                open.remove(n);
//                closed.add(n);
//            }
//
//            // Place path
//            if (path != null) {
//                // Place last path
//                this.placePath(level, rand, path.pos, chunkPos, config);
//
//                // Place all other node paths until no nodes remain
//                while (path.parent != null) {
//                    path = path.parent;
//                    this.placePath(level, rand, path.pos, chunkPos, config);
//                }
//            }
//        }
    }

    private double calcH(Node node, BlockPos targetPos, ServerLevel serverLevel) {
        int xDiff = node.pos.getX() - targetPos.getX();
        int zDiff = node.pos.getZ() - targetPos.getZ();
        double flatDistance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
//        double chebyshevDistance = Math.max(Math.abs(xDiff), Math.abs(zDiff));



//        return chebyshevDistance;
        return flatDistance;
//        return Math.sqrt(node.pos.distanceSq(targetPos));
    }

    private double calcG(Node node, Node neighbor) {
//        int xDiff = node.pos.getX() - startPos.getX();
//        int zDiff = node.pos.getZ() - startPos.getZ();
//        return Math.sqrt(xDiff * xDiff + zDiff * zDiff);

        double pathWeight = node.g + 1;


        float pv1 = getPVNoiseAt(node.pos) * 10f;
        float pv2 = getPVNoiseAt(neighbor.pos) * 10f;
        float slope = pv2 - pv1;
        float slopeFactor = 1 + slope * slope * 50;

        float altitudePunishment = pv2 > 5.5f ? 10f : 1f;

        return pathWeight * slopeFactor * altitudePunishment;
    }

    private float getPVNoiseAt(BlockPos pos) {
        ChunkGenerator chunkGenerator = serverLevel.getChunkSource().getGenerator();
        DensityFunction.SinglePointContext p1 = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
        double ridgeP1 = ((NoiseBasedChunkGeneratorAccessor) chunkGenerator).getRouter().ridges().compute(p1);
        return TerrainShaper.peaksAndValleys((float) ridgeP1);
    }

    static class Node implements Comparable<Node> {
        public BlockPos pos;
        public Node parent = null;

        /**
         * The cost function f(n) = g(n) + h(n), where g(n) is the cost of the path from the starting point to n,
         * and h(n) is a heuristic that estimates the cost of the cheapest path from n to the goal.
         */
        public double f = Double.MAX_VALUE;

        /**
         * The move function, the cost of moving from the starting point to a given node n.
         */
        public double g = Double.MAX_VALUE;

        // Direction info, used to prevent backtracking
        private static final List<Direction> possibleDirections = Lists.newArrayList(Direction.NORTH, Direction.EAST, Direction.SOUTH);
        private Direction sourceDirection;

        public Node(BlockPos pos) {
            this.pos = pos;
        }

        public Node(BlockPos pos, Direction sourceDirection) {
            this.pos = pos;
            this.sourceDirection = sourceDirection;
        }

        public List<Node> getNeighbors() {
//            List<Direction> directions = new ArrayList<>(possibleDirections);
//            if (this.sourceDirection != null) {
//                directions.remove(sourceDirection);
//            }
//
            List<Node> neighbors = new ArrayList<>();
//            for (Direction direction : directions) {
//                neighbors.add(new Node(this.pos.relative(direction, 1).mutable(), direction.getOpposite()));
//            }

            int stepSize = 2;

            // Cardinal directions
            neighbors.add(new Node(this.pos.offset(stepSize, 0, 0).mutable()));
            neighbors.add(new Node(this.pos.offset(-stepSize, 0, 0).mutable()));
            neighbors.add(new Node(this.pos.offset(0, 0, stepSize).mutable()));
            neighbors.add(new Node(this.pos.offset(0, 0, -stepSize).mutable()));

            // Diagonal directions
            neighbors.add(new Node(this.pos.offset(stepSize, 0, stepSize).mutable()));
            neighbors.add(new Node(this.pos.offset(-stepSize, 0, stepSize).mutable()));
            neighbors.add(new Node(this.pos.offset(stepSize, 0, -stepSize).mutable()));
            neighbors.add(new Node(this.pos.offset(-stepSize, 0, -stepSize).mutable()));

            return neighbors;
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
}
