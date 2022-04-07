package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

public class RoadSegment {
    public static final Codec<RoadSegment> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("startPos").forGetter(RoadSegment::getStartPos),
            BlockPos.CODEC.fieldOf("p1").forGetter(RoadSegment::getP1),
            BlockPos.CODEC.fieldOf("p2").forGetter(RoadSegment::getP2),
            BlockPos.CODEC.fieldOf("endPos").forGetter(RoadSegment::getEndPos))
        .apply(builder, RoadSegment::new));
    private final BlockPos startPos, endPos, p1, p2;

    public RoadSegment(BlockPos startPos, BlockPos endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.p1 = null;
        this.p2 = null;
    }

    public RoadSegment(BlockPos startPos, BlockPos p1, BlockPos p2, BlockPos endPos) {
        this.startPos = startPos;
        this.p1 = p1;
        this.p2 = p2;
        this.endPos = endPos;
    }

    public BlockPos[] getPoints() {
        return new BlockPos[]{startPos, p1, p2, endPos};
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    public BlockPos getEndPos() {
        return endPos;
    }

    public BlockPos getP1() {
        return p1;
    }

    public BlockPos getP2() {
        return p2;
    }

    @Override
    public String toString() {
        return "Road Segment [" + startPos + ", "+ p1 + ", " + p2 + ", " + endPos + "]";
    }
}
