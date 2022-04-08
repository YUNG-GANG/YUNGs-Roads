package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;

import javax.annotation.Nullable;
import java.util.*;

public class AStarRoadGenerator implements IRoadGenerator {
    public final ServerLevel serverLevel;

    public AStarRoadGenerator(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.getWorldPosition() : pos2.getWorldPosition();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.getWorldPosition() : pos1.getWorldPosition();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        // Construct road & road segments
        Road road = new Road(blockPos1, blockPos2);
        int numSegments = 4;
        for (int i = 0; i < numSegments; i++) {
            road.addRoadSegment(
                    blockPos1.offset(xDist * i / numSegments, 0, zDist * i / numSegments),
                    blockPos1.offset(xDist * (i + 1) / numSegments, 0, zDist * (i + 1) / numSegments));
        }

        // Ensure road does not cross ocean
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (serverLevel.getChunkSource().getGenerator().getNoiseBiome(
                            QuartPos.fromBlock(roadSegment.getStartPos().getX()),
                            QuartPos.fromBlock(0),
                            QuartPos.fromBlock(roadSegment.getStartPos().getZ()))
                    .is(BiomeTags.IS_OCEAN)) {
                return Optional.empty();
            }
        }

        return Optional.of(road);
    }

    @Override
    public void placeRoad(Road road, WorldGenLevel level, Random rand, BlockPos blockPos, @Nullable BlockPos nearestVillage) {
        ChunkPos chunkPos = new ChunkPos(blockPos);

        // Short-circuit if this chunk isn't between the start/end points of the road
        if (!containsRoad(chunkPos, road)) {
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
            BlockPos.MutableBlockPos targetPos = roadSegment.getEndPos().mutable();
//            targetPos.setY(getSurfaceHeight(world, targetPos));

            // Initialize start node
            Node start = new Node(roadSegment.getStartPos().mutable());
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
                if (n.pos.distSqr(targetPos) <= 10) {
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
                this.placePath(level, rand, path.pos, nearestVillage, chunkPos.x, chunkPos.z);

                // Place all other node paths until no nodes remain
                while (path.parent != null) {
                    path = path.parent;
                    this.placePath(level, rand, path.pos, nearestVillage, chunkPos.x, chunkPos.z);
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

    static class Node implements Comparable<Node>{
        public BlockPos.MutableBlockPos pos;
        public Node parent = null;

        // Evaluation functions
        public double f = Double.MAX_VALUE;
        public double g = Double.MAX_VALUE;

        // Direction info, used to prevent backtracking
        private static final List<Direction> possibleDirections = Lists.newArrayList(Direction.NORTH, Direction.EAST, Direction.SOUTH);
        private Direction sourceDirection;

        public Node(BlockPos.MutableBlockPos pos) {
            this.pos = pos;
        }

        public Node(BlockPos.MutableBlockPos pos, Direction sourceDirection) {
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
                neighbors.add(new Node(this.pos.relative(direction).mutable(), direction.getOpposite()));
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
