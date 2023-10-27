package com.yungnickyoung.minecraft.yungsroads.world.road.segment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public class DefaultRoadSegment {
    public static final Codec<DefaultRoadSegment> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("startPos").forGetter(DefaultRoadSegment::getStartPos),
            BlockPos.CODEC.fieldOf("endPos").forGetter(DefaultRoadSegment::getEndPos))
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
