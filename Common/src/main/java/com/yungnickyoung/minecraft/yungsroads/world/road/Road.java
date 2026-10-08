package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Road {
    private final BlockPos startPos;
    private final BlockPos endPos;

    /** The id of the road type the road was generated with. */
    public final ResourceLocation roadType;

    /** The index of the road type's variant the road was generated with. */
    public final int variant;

    /** The settings of the road's type and variant, which it's routed, shaped, and placed with. Not saved. */
    public final RoadSettings settings;

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

    /**
     * The stretches where the road runs too far below the ground to cut down to it, which are bored through as tunnels,
     * as ranges of {@link #positions}, in order from start to end.
     */
    public List<Span> tunnels;

    public Road(BlockPos endpoint1, BlockPos endpoint2, ResourceLocation roadType, int variant, RoadSettings settings,
                List<DebugNode> nodes, List<BlockPos> positions, List<Bridge> bridges, List<Span> landBridges, List<Span> tunnels) {
        this.startPos = endpoint1.getX() <= endpoint2.getX() ? endpoint1 : endpoint2;
        this.endPos = this.startPos == endpoint1 ? endpoint2 : endpoint1;
        this.roadType = roadType;
        this.variant = variant;
        this.settings = settings;
        this.nodes = nodes;
        this.positions = positions;
        this.bridges = bridges;
        this.landBridges = landBridges;
        this.tunnels = tunnels;
    }

    /** A road of the chosen type with no route yet. */
    public Road(BlockPos startPos, BlockPos endPos, RoadTypes.Choice roadType) {
        this(startPos, endPos, roadType.typeId(), roadType.variant(), roadType.settings(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * The codec for saving roads. Loaded roads get the settings of their type and variant from the given road types.
     *
     * @param roadTypes May be null if the codec is only used for encoding.
     */
    public static Codec<Road> codec(@Nullable RoadTypes roadTypes) {
        return RecordCodecBuilder.create(builder -> builder
                .group(
                        BlockPos.CODEC.fieldOf("start_pos").forGetter(Road::getStartPos),
                        BlockPos.CODEC.fieldOf("end_pos").forGetter(Road::getEndPos),
                        ResourceLocation.CODEC.fieldOf("road_type").forGetter(road -> road.roadType),
                        Codec.INT.fieldOf("variant").forGetter(road -> road.variant),
                        DebugNode.CODEC.listOf().fieldOf("nodes").forGetter(road -> road.nodes),
                        BlockPos.CODEC.listOf().fieldOf("positions").forGetter(road -> road.positions),
                        Bridge.CODEC.listOf().fieldOf("bridges").forGetter(road -> road.bridges),
                        Span.CODEC.listOf().fieldOf("land_bridges").forGetter(road -> road.landBridges),
                        Span.CODEC.listOf().fieldOf("tunnels").forGetter(road -> road.tunnels))
                .apply(builder, (startPos, endPos, roadType, variant, nodes, positions, bridges, landBridges, tunnels) ->
                        new Road(startPos, endPos, roadType, variant, roadTypes.settings(roadType, variant),
                                nodes, positions, bridges, landBridges, tunnels)));
    }

    /**
     * Orders roads by their endpoints. Decides which road a position belongs to where roads overlap, so every chunk
     * agrees.
     */
    public static int compare(Road a, Road b) {
        int byStart = Long.compare(a.startPos.asLong(), b.startPos.asLong());
        return byStart != 0 ? byStart : Long.compare(a.endPos.asLong(), b.endPos.asLong());
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
     * The values are kept for the tuning screen's map and Inspect page.
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
