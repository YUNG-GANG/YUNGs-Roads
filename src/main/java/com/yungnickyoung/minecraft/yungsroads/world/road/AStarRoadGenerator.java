package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.google.common.collect.Lists;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

public class AStarRoadGenerator implements IRoadGenerator {
    public final ServerWorld world;

    public AStarRoadGenerator(ServerWorld world) {
        this.world = world;
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos startPos = pos1.x <= pos2.x ? pos1.asBlockPos() : pos2.asBlockPos();
        BlockPos endPos = pos1.x <= pos2.x ? pos2.asBlockPos() : pos1.asBlockPos();

        int xDist = endPos.getX() - startPos.getX();
        int zDist = endPos.getZ() - startPos.getZ();

//        Road road = new Road(startPos, endPos)
//                .addRoadSegment(
//                        startPos,
//                        startPos.add(xDist / 4, 0, zDist / 4))
//                .addRoadSegment(
//                        startPos.add(xDist / 4, 0, zDist / 4),
//                        startPos.add(2 * xDist / 4, 0, 2 * zDist / 4))
//                .addRoadSegment(
//                        startPos.add(2 * xDist / 4, 0, 2 * zDist / 4),
//                        startPos.add(3 * xDist / 4, 0, 3 * zDist / 4))
//                .addRoadSegment(
//                        startPos.add(3 * xDist / 4, 0, 3 * zDist / 4),
//                        endPos);

        Road road = new Road(startPos, endPos);
        double slope = (endPos.getZ() - startPos.getZ()) / (double) (endPos.getX() - startPos.getX());
        // z = slope * (x - endpos.x) + endpos.z

        BlockPos.Mutable segmentStartPos = startPos.toMutable();
        BlockPos.Mutable segmentEndPos = startPos.toMutable();

        while (segmentStartPos.getX() <= endPos.getX()) {
            // Set segment end pos
            segmentEndPos.setX((segmentStartPos.getX() / 16) * 16 + 15);
            if (segmentEndPos.getX() > endPos.getX()) segmentEndPos.setX(endPos.getX());
            segmentEndPos.setZ((int) (slope * (segmentEndPos.getX() - endPos.getX())) + endPos.getZ());

            // Add segment
            road.addRoadSegment(new BlockPos(segmentStartPos), new BlockPos(segmentEndPos));

            // Update segment start pos
            segmentStartPos.setX(((segmentStartPos.getX() + 16) / 16) * 16);
            segmentStartPos.setZ((int) (slope * segmentStartPos.getX()) + startPos.getZ());
            segmentStartPos.setZ((int) (slope * (segmentStartPos.getX() - endPos.getX())) + endPos.getZ());
        }

        // Ensure road does not cross ocean
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (world.getChunkProvider().getChunkGenerator().getBiomeProvider().getNoiseBiome(
                            (roadSegment.getStartPos().getX() >> 2) + 2,
                            0,
                            (roadSegment.getStartPos().getZ() >> 2) + 2)
                    .getCategory() == Biome.Category.OCEAN) {
                return Optional.empty();
            }
        }

        return Optional.of(road);
    }

    @Override
    public void placeRoad(Road road, ISeedReader world, Random rand, ChunkPos chunkPos, BlockPos nearestVillage) {
        // Short-circuit if this chunk isn't between the start/end points of the road
        if (chunkPos.asBlockPos().getX() + 15 < road.getVillageStart().getX() || chunkPos.asBlockPos().getX() > road.getVillageEnd().getX()) {
            return;
        }

        // Determine road segments we need to process
        List<RoadSegment> roadSegments = new ArrayList<>();
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (containsRoadSegment(chunkPos, roadSegment)) {
                roadSegments.add(roadSegment);
            }
        }

        for (RoadSegment roadSegment : roadSegments) {
            // A* algorithm
            PriorityQueue<Node> closed = new PriorityQueue<>();
            PriorityQueue<Node> open = new PriorityQueue<>();
            BlockPos.Mutable targetPos = roadSegment.getEndPos().toMutable();
//            targetPos.setY(getSurfaceHeight(world, targetPos));

            // Initialize start node
            Node start = new Node(roadSegment.getStartPos().toMutable());
//            if (isInChunk(chunkPos, start.pos)) {
//                start.pos.setY(getSurfaceHeight(world, start.pos));
//            }

            start.g = 0;
            start.f = start.g + calcH(start, targetPos);

            open.add(start);
            Node path = null;

            // Begin generating path
            while (open.size() > 0) {
                Node n = open.peek();
//                if (n.pos == roadSegment.getEndPos()) {
                if (n.pos.distanceSq(targetPos) <= 10) {
                    path = n;
                    break;
                }

                // Propagate update to neighbors
                for (Node m : n.getNeighbors()) {
                    if (m.pos.getX() > targetPos.getX()) continue;

//                    if (isInChunk(chunkPos, m.pos)) {
//                        m.pos.setY(getSurfaceHeight(world, m.pos));
//                    }
                    double totalWeight = n.g + calcG(m, n.pos);
//                    double totalWeight = n.g + 1;

                    if (!open.contains(m) && !closed.contains(m)) {
                        m.parent = n;
                        m.g = totalWeight;
                        m.f = m.g + calcH(m, targetPos);
                        open.add(m);
                    } else {
                        if (totalWeight < m.g) {
                            m.parent = n;
                            m.g = totalWeight;
                            m.f = m.g + calcH(m, targetPos);

                            if (closed.contains(m)) {
                                closed.remove(m);
                                open.add(m);
                            }
                        }
                    }
                }
//                }

                open.remove(n);
                closed.add(n);
            }

            // Place path
            if (path != null) {
                // Place last path
                this.placePath(world, rand, path.pos, nearestVillage, chunkPos.x, chunkPos.z);

                // Place all other node paths until no nodes remain
                while (path.parent != null) {
                    path = path.parent;
                    this.placePath(world, rand, path.pos, nearestVillage, chunkPos.x, chunkPos.z);
                }
            }
        }
    }

    private double calcH(Node node, BlockPos targetPos) {
        int xDiff = node.pos.getX() - targetPos.getX();
        int zDiff = node.pos.getZ() - targetPos.getZ();
        return Math.sqrt(xDiff * xDiff + zDiff * zDiff);
//        return Math.sqrt(node.pos.distanceSq(targetPos));
    }

    private double calcG(Node node, BlockPos startPos) {
        int xDiff = node.pos.getX() - startPos.getX();
        int zDiff = node.pos.getZ() - startPos.getZ();
        return Math.sqrt(xDiff * xDiff + zDiff * zDiff);
    }

    private boolean containsRoadSegment(ChunkPos chunkPos, RoadSegment roadSegment) {
        return (roadSegment.getStartPos().getX() >= chunkPos.getXStart() || roadSegment.getEndPos().getX() >= chunkPos.getXStart())
            && (roadSegment.getStartPos().getX() <= chunkPos.getXEnd() || roadSegment.getEndPos().getX() <= chunkPos.getXEnd());
    }

    static class Node implements Comparable<Node>{
        public BlockPos.Mutable pos;
        public Node parent = null;

        // Evaluation functions
        public double f = Double.MAX_VALUE;
        public double g = Double.MAX_VALUE;

        // Direction info, used to prevent backtracking
        private static final List<Direction> possibleDirections = Lists.newArrayList(Direction.NORTH, Direction.EAST, Direction.SOUTH);
        private Direction sourceDirection;

        public Node(BlockPos.Mutable pos) {
            this.pos = pos;
        }

        public Node(BlockPos.Mutable pos, Direction sourceDirection) {
            this.pos = pos;
            this.sourceDirection = sourceDirection;
        }

        public List<Node> getNeighbors() {
            List<Direction> directions = new ArrayList<>(possibleDirections);
            if (this.sourceDirection != null) {
                directions.remove(sourceDirection);
            }

            List<Node> neighbors = new ArrayList<>();
            for (Direction direction : directions) {
                neighbors.add(new Node(this.pos.offset(direction).toMutable(), direction.getOpposite()));
            }
            return neighbors;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Node)) {
                return super.equals(obj);
            }

            return this.pos.equals(((Node) obj).pos);
        }
    }
}
