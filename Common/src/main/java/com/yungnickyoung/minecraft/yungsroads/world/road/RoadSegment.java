package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public class RoadSegment {
    public static final Codec<RoadSegment> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("startPos").forGetter(RoadSegment::getStartPos),
            BlockPos.CODEC.fieldOf("endPos").forGetter(RoadSegment::getEndPos))
        .apply(builder, RoadSegment::new));

    private final BlockPos startPos, endPos;

    public RoadSegment(BlockPos startPos, BlockPos endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public RoadSegment(BlockPos startPos, BlockPos p1, BlockPos p2, BlockPos endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    public BlockPos getEndPos() {
        return endPos;
    }

    public BlockPos getMiddlePos() {
        return new BlockPos((startPos.getX() + endPos.getX()) / 2, 0, (startPos.getZ() + endPos.getZ()) / 2);
    }

    @Override
    public String toString() {
        return "Road Segment [" + startPos + ", " + endPos + "]";
    }
}
