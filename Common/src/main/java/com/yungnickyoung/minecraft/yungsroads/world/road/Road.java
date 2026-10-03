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
            BlockPos.CODEC.listOf().fieldOf("positions").forGetter(road -> road.positions),
            Bridge.CODEC.listOf().fieldOf("bridges").forGetter(road -> road.bridges),
            Span.CODEC.listOf().fieldOf("land_bridges").forGetter(road -> road.landBridges))
        .apply(builder, Road::new));

    private final BlockPos startPos;
    private final BlockPos endPos;

    /** The pathfinding nodes making up this road, in order from start to end. */
    public List<DebugNode> nodes;

    /**
     * The road's center line, rasterized to block positions. Each y is the road's surface height there: the y of the
     * block the road is placed at, smoothed along the road. See {@code RoadProfile}.
     */
    public List<BlockPos> positions;

    /** The road's water crossings, in order from start to end. Each spans two consecutive {@link #nodes}. */
    public List<Bridge> bridges;

    /**
     * The dips in the terrain the road crosses on land bridges, as ranges of {@link #positions}, in order from start to
     * end. Holes that routing can't see, such as ravines, aren't included, since they're only found when placing.
     */
    public List<Span> landBridges;

    public Road(BlockPos endpoint1, BlockPos endpoint2, List<DebugNode> nodes, List<BlockPos> positions, List<Bridge> bridges,
                List<Span> landBridges) {
        this.startPos = endpoint1.getX() <= endpoint2.getX() ? endpoint1 : endpoint2;
        this.endPos = this.startPos == endpoint1 ? endpoint2 : endpoint1;
        this.nodes = nodes;
        this.positions = positions;
        this.bridges = bridges;
        this.landBridges = landBridges;
    }

    public Road(BlockPos startPos, BlockPos endPos) {
        this(startPos, endPos, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    public BlockPos getEndPos() {
        return endPos;
    }

    /**
     * Whether the segment from node n to node n + 1 is a bridge.
     */
    public boolean isBridgeSegment(int n) {
        BlockPos from = this.nodes.get(n).rawPos;
        BlockPos to = this.nodes.get(n + 1).rawPos;
        for (Bridge bridge : this.bridges) {
            if (sameColumn(bridge.start, from) && sameColumn(bridge.end, to)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameColumn(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    @Override
    public String toString() {
        return String.format("Road %s - %s (%d nodes, %d bridges)", startPos, endPos, nodes.size(), bridges.size());
    }

    /**
     * A straight water crossing between two banks, from the bank nearer the road's start to the other. Its ends are
     * at the same x and z as the nodes it spans, but their y values are the deck heights at each bank: the bank's
     * surface height, or sea level if that's higher.
     */
    public record Bridge(BlockPos start, BlockPos end) {
        public static final Codec<Bridge> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        BlockPos.CODEC.fieldOf("start").forGetter(Bridge::start),
                        BlockPos.CODEC.fieldOf("end").forGetter(Bridge::end)
                ).apply(builder, Bridge::new));
    }

    /**
     * A range of a road's {@link #positions}, from the first index to the last, inclusive.
     */
    public record Span(int first, int last) {
        public static final Codec<Span> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        Codec.INT.fieldOf("first").forGetter(Span::first),
                        Codec.INT.fieldOf("last").forGetter(Span::last)
                ).apply(builder, Span::new));
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
