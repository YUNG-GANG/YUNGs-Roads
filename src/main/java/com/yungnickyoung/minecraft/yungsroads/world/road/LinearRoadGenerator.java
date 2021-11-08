package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;

import java.util.Optional;
import java.util.Random;

public class LinearRoadGenerator implements IRoadGenerator {
    private final ServerWorld world;

    public LinearRoadGenerator(ServerWorld world) {
        this.world = world;
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.asBlockPos() : pos2.asBlockPos();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.asBlockPos() : pos1.asBlockPos();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        Road road = new Road(blockPos1, blockPos2)
            .addRoadSegment(
                blockPos1,
                blockPos1.add(xDist / 4, 0, zDist / 4))
            .addRoadSegment(
                blockPos1.add(xDist / 4, 0, zDist / 4),
                blockPos1.add(2 * xDist / 4, 0, 2 * zDist / 4))
            .addRoadSegment(
                blockPos1.add(2 * xDist / 4, 0, 2 * zDist / 4),
                blockPos1.add(3 * xDist / 4, 0, 3 * zDist / 4))
            .addRoadSegment(
                blockPos1.add(3 * xDist / 4, 0, 3 * zDist / 4),
                blockPos2);

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
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // Determine total slope of line from starting point to ending point
        int totalXDiff = road.getVillageEnd().getX() - road.getVillageStart().getX();
        int totalZDiff = road.getVillageEnd().getZ() - road.getVillageStart().getZ();
        double totalSlope = totalXDiff == 0 ? Integer.MAX_VALUE : totalZDiff / (double) totalXDiff;
        int xDir = totalXDiff >= 0 ? 1 : -1; // x direction multiplier
        int zDir = totalZDiff >= 0 ? 1 : -1; // z direction multiplier

        // Initialize mutable at starting point
        mutable.setPos(road.getVillageStart());

        YungsRoads.LOGGER.info(this);

        double slopeCounter = Math.abs(totalSlope);

        while (!isInRange(mutable, road.getVillageEnd())) {
            // Move in z direction
            while (slopeCounter >= 1 && !isInRange(mutable, road.getVillageEnd())) {
                placePath(world, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
                mutable.move(0, 0, zDir);
                slopeCounter--;
            }

            while (slopeCounter < 1 && !isInRange(mutable, road.getVillageEnd())) {
                placePath(world, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
                mutable.move(xDir, 0, 0);
                slopeCounter += Math.abs(totalSlope);
            }

            // Place path at final position
            placePath(world, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
        }
    }
}
