package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class Road {
    public static final Codec<Road> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("start_pos").forGetter(Road::getStartPos),
            BlockPos.CODEC.fieldOf("end_pos").forGetter(Road::getEndPos),
            DebugNode.CODEC.listOf().fieldOf("nodes").forGetter(road -> road.nodes),
            BlockPos.CODEC.listOf().fieldOf("positions").forGetter(road -> road.positions))
        .apply(builder, Road::new));

    private final BlockPos startPos;
    private final BlockPos endPos;

    /** The pathfinding nodes making up this road, in order from start to end. */
    public List<DebugNode> nodes;

    /** The road's center line, rasterized to block positions. Y values are not meaningful. */
    public List<BlockPos> positions;

    public Road(BlockPos endpoint1, BlockPos endpoint2, List<DebugNode> nodes, List<BlockPos> positions) {
        this.startPos = endpoint1.getX() <= endpoint2.getX() ? endpoint1 : endpoint2;
        this.endPos = this.startPos == endpoint1 ? endpoint2 : endpoint1;
        this.nodes = nodes;
        this.positions = positions;
    }

    public Road(BlockPos startPos, BlockPos endPos) {
        this(startPos, endPos, new ArrayList<>(), new ArrayList<>());
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    public BlockPos getEndPos() {
        return endPos;
    }

    @Override
    public String toString() {
        return String.format("Road %s - %s (%d nodes)", startPos, endPos, nodes.size());
    }

    /**
     * A pathfinding node along a road, with the A* values it was found with.
     * The values are kept for the F3 debug overlay.
     */
    public static class DebugNode {
        public static final Codec<DebugNode> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        BlockPos.CODEC.fieldOf("rawPos").forGetter(node -> node.rawPos),
                        BlockPos.CODEC.fieldOf("jitteredPos").forGetter(node -> node.jitteredPos),
                        Codec.DOUBLE.fieldOf("g").forGetter(node -> node.g),
                        Codec.DOUBLE.fieldOf("h").forGetter(node -> node.h)
                ).apply(builder, DebugNode::new));

        public BlockPos rawPos, jitteredPos;

        /** Accumulated path cost from the road's start to this node. */
        public double g;

        /** Heuristic estimate of the remaining cost from this node to the road's end. */
        public double h;

        private DebugNode(BlockPos rawPos, BlockPos jitteredPos, double g, double h) {
            this.rawPos = rawPos;
            this.jitteredPos = jitteredPos;
            this.g = g;
            this.h = h;
        }

        public DebugNode(BlockPos rawPos, double g, double h) {
            this(rawPos, rawPos, g, h);
        }
    }
}
