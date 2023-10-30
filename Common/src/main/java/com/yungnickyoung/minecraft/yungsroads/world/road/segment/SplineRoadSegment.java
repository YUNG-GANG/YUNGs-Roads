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
    public SplineRoadSegment(BlockPos startPos, BlockPos endPos, Random random) {
        super(startPos, endPos);

        int xDist = endPos.getX() - startPos.getX();
        int zDist = endPos.getZ() - startPos.getZ();
        int xDir = xDist > 0 ? 1 : -1;
        int zDir = zDist > 0 ? 1 : -1;
        int xDistAbs = Math.abs(xDist);
        int zDistAbs = Math.abs(zDist);

        int p1x = xDistAbs == 0 ? startPos.getX() : startPos.getX() + random.nextInt(xDistAbs) * xDir;
        int p1z = zDistAbs == 0 ? startPos.getZ() : startPos.getZ() + random.nextInt(zDistAbs) * zDir;
        int p2x = xDistAbs == 0 ? endPos.getX() : endPos.getX() + random.nextInt(xDistAbs) * -xDir;
        int p2z = zDistAbs == 0 ? endPos.getZ() : endPos.getZ() + random.nextInt(zDistAbs) * -zDir;
//        BlockPos p1 = startPos.offset(random.nextInt(maxPointOffset) - maxPointOffset / 4, 0, random.nextInt(maxPointOffset) - maxPointOffset / 4);
//        BlockPos p2 = endPos.offset(random.nextInt(maxPointOffset) - maxPointOffset / 4, 0, random.nextInt(maxPointOffset) - maxPointOffset / 4);
        this.p1 = new BlockPos(p1x, 0, p1z);
        this.p2 = new BlockPos(p2x, 0, p2z);
    }

    private SplineRoadSegment(BlockPos startPos, BlockPos p1, BlockPos p2, BlockPos endPos) {
        super(startPos, endPos);
        this.p1 = p1;
        this.p2 = p2;
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
