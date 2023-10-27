package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
            RoadSegmentType.ROAD_SEGMENT_CODEC.listOf().fieldOf("roadSegments").forGetter(Road::getRoadSegments))
        .apply(builder, Road::new));

    private final BlockPos villageStart;
    private final BlockPos villageEnd;
    private final List<DefaultRoadSegment> roadSegments;

    public Road(BlockPos village1, BlockPos village2, List<DefaultRoadSegment> roadSegments) {
        this.villageStart = village1.getX() <= village2.getX() ? village1 : village2;
        this.villageEnd = this.villageStart == village1 ? village2 : village1;
        this.roadSegments = roadSegments;
    }

    public Road(BlockPos village1, BlockPos village2) {
        this(village1, village2, new ArrayList<>());
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
}
