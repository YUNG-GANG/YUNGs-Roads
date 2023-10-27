package com.yungnickyoung.minecraft.yungsroads.world.road.segment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Random;

public class SplineRoadSegment extends DefaultRoadSegment {
    public static final Codec<SplineRoadSegment> CODEC = RecordCodecBuilder.create(builder -> builder
        .group(
            BlockPos.CODEC.fieldOf("startPos").forGetter(SplineRoadSegment::getStartPos),
            BlockPos.CODEC.fieldOf("p1").forGetter(SplineRoadSegment::getP1),
            BlockPos.CODEC.fieldOf("p2").forGetter(SplineRoadSegment::getP2),
            BlockPos.CODEC.fieldOf("endPos").forGetter(SplineRoadSegment::getEndPos))
        .apply(builder, SplineRoadSegment::new));

    private final BlockPos p1, p2;

    public SplineRoadSegment(BlockPos startPos, BlockPos p1, BlockPos p2, BlockPos endPos) {
        super(startPos, endPos);
        this.p1 = p1;
        this.p2 = p2;
    }

    public static SplineRoadSegment createSplineRoadSegment(BlockPos startPos, BlockPos endPos, Random random) {
        double length = Math.sqrt(startPos.distSqr(endPos));
        int maxPointOffset = (int) (length);
        BlockPos p1 = startPos.offset(random.nextInt(maxPointOffset) - maxPointOffset / 2, 0, random.nextInt(maxPointOffset) - maxPointOffset / 2);
        BlockPos p2 = endPos.offset(random.nextInt(maxPointOffset) - maxPointOffset / 2, 0, random.nextInt(maxPointOffset) - maxPointOffset / 2);
        return new SplineRoadSegment(startPos, p1, p2, endPos);
    }

    public BlockPos[] getPoints() {
        return new BlockPos[]{getStartPos(), p1, p2, getEndPos()};
    }

    public Vec3[] getPointsAsVec() {
        return Arrays.stream(getPoints()).map(pos -> new Vec3(pos.getX(), pos.getY(), pos.getZ())).toArray(Vec3[]::new);
    }

    public BlockPos getP1() {
        return p1;
    }

    public BlockPos getP2() {
        return p2;
    }

    @Override
    public RoadSegmentType<?> type() {
        return RoadSegmentType.SPLINE;
    }

    @Override
    public String toString() {
        return "Spline Road Segment [" + getStartPos() + ", "+ p1 + ", " + p2 + ", " + getEndPos() + "]";
    }
}
