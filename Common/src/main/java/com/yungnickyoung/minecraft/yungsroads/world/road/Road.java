package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsroads.world.road.generator.AStarRoadGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.road.segment.DefaultRoadSegment;
import com.yungnickyoung.minecraft.yungsroads.world.road.segment.RoadSegmentType;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class Road {
    public static final Codec<Road> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("villageStart").forGetter(Road::getVillageStart),
            BlockPos.CODEC.fieldOf("villageEnd").forGetter(Road::getVillageEnd),
            DebugNode.CODEC.listOf().fieldOf("positions").forGetter(road -> road.positions),
            RoadSegmentType.ROAD_SEGMENT_CODEC.listOf().fieldOf("roadSegments").forGetter(Road::getRoadSegments))
        .apply(builder, Road::new));

    private final BlockPos villageStart;
    private final BlockPos villageEnd;
    private final List<DefaultRoadSegment> roadSegments;


    public List<DebugNode> positions;


    public Road(BlockPos village1, BlockPos village2, List<DebugNode> positions, List<DefaultRoadSegment> roadSegments) {
        this.villageStart = village1.getX() <= village2.getX() ? village1 : village2;
        this.villageEnd = this.villageStart == village1 ? village2 : village1;
        this.positions = positions;
        this.roadSegments = roadSegments;
    }

    public Road(BlockPos village1, BlockPos village2) {
        this(village1, village2, new ArrayList<>(), new ArrayList<>());
    }

    public BlockPos getVillageStart() {
        return villageStart;
    }

    public BlockPos getVillageEnd() {
        return villageEnd;
    }

    public List<DefaultRoadSegment> getRoadSegments() {
        return roadSegments;
    }

    public Road addRoadSegment(DefaultRoadSegment roadSegment) {
        this.roadSegments.add(roadSegment);
        return this;
    }

    public Road addRoadSegment(BlockPos startPos, BlockPos endPos) {
        DefaultRoadSegment roadSegment = new DefaultRoadSegment(startPos, endPos);
        return this.addRoadSegment(roadSegment);
    }

    @Override
    public String toString() {
        return String.format("Road %s - %s (%d segments)", villageStart, villageEnd, roadSegments.size());
    }

    public static class DebugNode implements Comparable<DebugNode> {
        public static final Codec<DebugNode> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        BlockPos.CODEC.fieldOf("rawPos").forGetter(node -> node.rawPos),
                        BlockPos.CODEC.fieldOf("jitteredPos").forGetter(node -> node.jitteredPos),
                        Codec.DOUBLE.fieldOf("f").forGetter(node -> node.f),
                        Codec.DOUBLE.fieldOf("g").forGetter(node -> node.g),
                        Codec.DOUBLE.fieldOf("h").forGetter(node -> node.h),
                        Codec.DOUBLE.fieldOf("pathFactor").forGetter(node -> node.pathFactor),
                        Codec.DOUBLE.fieldOf("slopeFactor").forGetter(node -> node.slopeFactor),
                        Codec.DOUBLE.fieldOf("altitudePunishment").forGetter(node -> node.altitudePunishment)
                ).apply(builder, DebugNode::new));

        public BlockPos rawPos, jitteredPos;
        public double f, g, h;
        public double pathFactor, slopeFactor, altitudePunishment;

        private DebugNode(BlockPos rawPos, BlockPos jitteredPos, double f, double g, double h, double pathFactor, double slopeFactor, double altitudePunishment) {
            this.rawPos = rawPos;
            this.jitteredPos = jitteredPos;
            this.f = f;
            this.g = g;
            this.h = h;
            this.pathFactor = pathFactor;
            this.slopeFactor = slopeFactor;
            this.altitudePunishment = altitudePunishment;
        }

        public DebugNode(AStarRoadGenerator.Node node) {
            this.rawPos = node.pos;
            this.f = node.f;
            this.g = node.g;
            this.h = node.h;
            this.pathFactor = node.pathFactor;
            this.slopeFactor = node.slopeFactor;
            this.altitudePunishment = node.altitudePunishment;
        }

        @Override
        public int compareTo(DebugNode o) {
            return this.jitteredPos.compareTo(o.jitteredPos);
        }
    }
}
