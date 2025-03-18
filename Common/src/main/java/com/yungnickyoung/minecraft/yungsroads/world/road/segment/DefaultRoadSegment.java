package com.yungnickyoung.minecraft.yungsroads.world.road.segment;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public class DefaultRoadSegment {
    public static final MapCodec<DefaultRoadSegment> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("start_pos").forGetter(DefaultRoadSegment::getStartPos),
            BlockPos.CODEC.fieldOf("end_pos").forGetter(DefaultRoadSegment::getEndPos))
        .apply(builder, DefaultRoadSegment::new));

    private final BlockPos startPos, endPos;

    public DefaultRoadSegment(BlockPos startPos, BlockPos endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    public BlockPos getEndPos() {
        return endPos;
    }

    public RoadSegmentType<?> type() {
        return RoadSegmentType.DEFAULT;
    }

    @Override
    public String toString() {
        return "Road Segment [" + startPos + ", " + endPos + "]";
    }
}
