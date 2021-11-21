package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SplineRoadGenerator implements IRoadGenerator {
    private final ServerWorld world;
    private final SharedSeedRandom random = new SharedSeedRandom();

    public SplineRoadGenerator(ServerWorld world) {
        this.world = world;
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.asBlockPos() : pos2.asBlockPos();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.asBlockPos() : pos1.asBlockPos();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        random.setLargeFeatureSeedWithSalt(world.getSeed(), pos1.x, 0, pos1.z);

        Road road = new Road(blockPos1, blockPos2)
                .addSplineRoadSegment(
                        blockPos1,
                        blockPos1.add(xDist / 4, 0, zDist / 4),
                        random)
                .addSplineRoadSegment(
                        blockPos1.add(xDist / 4, 0, zDist / 4),
                        blockPos1.add(2 * xDist / 4, 0, 2 * zDist / 4),
                        random)
                .addSplineRoadSegment(
                        blockPos1.add(2 * xDist / 4, 0, 2 * zDist / 4),
                        blockPos1.add(3 * xDist / 4, 0, 3 * zDist / 4),
                        random)
                .addSplineRoadSegment(
                        blockPos1.add(3 * xDist / 4, 0, 3 * zDist / 4),
                        blockPos2,
                        random);

        // Ensure road does not cross ocean
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (world.getChunkProvider().getChunkGenerator().getBiomeProvider().getNoiseBiome(
                            (roadSegment.getStartPos().getX() >> 2) + 2,
                            0,
                            (roadSegment.getStartPos().getZ() >> 2) + 2)
                    .getCategory() == Biome.Category.OCEAN) {
                return Optional.empty();
            }
        }

        return Optional.of(road);
    }

    @Override
    public void placeRoad(Road road, ISeedReader world, Random rand, ChunkPos chunkPos, BlockPos nearestVillage) {
        // Short-circuit if this chunk isn't between the start/end points of the road
        if (!containsRoad(chunkPos, road)) {
            return;
        }

        // Determine road segments we need to process for this chunk
        List<RoadSegment> roadSegments = new ArrayList<>();
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (containsRoadSegment(chunkPos, roadSegment)) {
                roadSegments.add(roadSegment);
            }
        }

        random.setLargeFeatureSeed(world.getSeed(), road.getVillageStart().getX() >> 4, road.getVillageEnd().getZ() >> 4);

        for (RoadSegment roadSegment : roadSegments) {
            BlockPos p0 = roadSegment.getStartPos();
            BlockPos p1 = roadSegment.getP1();
            BlockPos p2 = roadSegment.getP2();
            BlockPos p3 = roadSegment.getEndPos();
            BlockPos[] pts = new BlockPos[]{p0, p1, p2, p3};

            // Debug markers
            if (isInChunk(chunkPos, p0)) {
                BlockPos.Mutable mutable = p0.toMutable();
                mutable.setY(getSurfaceHeight(world, mutable));

                for (int y = 0; y < 10; y++) {
                    mutable.move(Direction.UP);
                    world.setBlockState(mutable, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
                }
            }
            if (isInChunk(chunkPos, p3)) {
                BlockPos.Mutable mutable = p3.toMutable();
                mutable.setY(getSurfaceHeight(world, mutable));

                for (int y = 0; y < 10; y++) {
                    mutable.move(Direction.UP);
                    world.setBlockState(mutable, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
                }
            }

            float t = 0;
            BlockPos pos;
            while (t <= 1f) {
                pos = getPoint(pts, t);
                if (isInChunk(chunkPos, pos)) {
                    placePath(world, rand, pos, nearestVillage, chunkPos.x, chunkPos.z);
                }
                t += 0.001f;
            }
        }

        YungsRoads.LOGGER.info("Generated {}", road);
    }

    private BlockPos getPoint(BlockPos[] pts, float t) {
        float omt = 1f - t;
        float omt2 = omt * omt;
        float t2 = t * t;
        return multiply(pts[0], omt2 * omt).add(
               multiply(pts[1], 3f * omt2 * t)).add(
               multiply(pts[2], 3f * omt * t2)).add(
               multiply(pts[3], t2 * t));
    }

    private BlockPos multiply(BlockPos pos, float amp) {
        return new BlockPos(pos.getX() * amp, pos.getY() * amp, pos.getZ() * amp);
    }
}
