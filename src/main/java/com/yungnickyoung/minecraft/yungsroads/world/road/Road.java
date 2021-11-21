package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Road {
    public static final Codec<Road> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("villageStart").forGetter(Road::getVillageStart),
            BlockPos.CODEC.fieldOf("villageEnd").forGetter(Road::getVillageEnd),
            RoadSegment.CODEC.listOf().fieldOf("roadSegments").forGetter(Road::getRoadSegments))
        .apply(builder, Road::new));

    private final BlockPos villageStart;
    private final BlockPos villageEnd;
    private final List<RoadSegment> roadSegments;

    public Road(BlockPos village1, BlockPos village2, List<RoadSegment> roadSegments) {
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

    public List<RoadSegment> getRoadSegments() {
        return roadSegments;
    }

    public Road addRoadSegment(RoadSegment roadSegment) {
        this.roadSegments.add(roadSegment);
        return this;
    }

    public Road addRoadSegment(BlockPos startPos, BlockPos endPos) {
        RoadSegment roadSegment = new RoadSegment(startPos, endPos);
        return this.addRoadSegment(roadSegment);
    }

    public Road addSplineRoadSegment(BlockPos startPos, BlockPos endPos, Random random) {
        BlockPos p1 = startPos.add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
        BlockPos p2 = endPos.add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
        RoadSegment roadSegment = new RoadSegment(startPos, p1, p2, endPos);
        return this.addRoadSegment(roadSegment);
    }

    @Override
    public String toString() {
        return String.format("Road %s - %s (%d segments)", villageStart, villageEnd, roadSegments.size());
    }
}
