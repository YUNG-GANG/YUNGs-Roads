package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;

public class LinearRoadGenerator implements IRoadGenerator {
    private final ServerLevel serverLevel;

    public LinearRoadGenerator(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }

    @Override
    public Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2) {
        // Make sure our starting position is always the one with lesser x value
        BlockPos blockPos1 = pos1.x <= pos2.x ? pos1.getWorldPosition() : pos2.getWorldPosition();
        BlockPos blockPos2 = pos1.x <= pos2.x ? pos2.getWorldPosition() : pos1.getWorldPosition();

        int xDist = blockPos2.getX() - blockPos1.getX();
        int zDist = blockPos2.getZ() - blockPos1.getZ();

        Road road = new Road(blockPos1, blockPos2)
            .addRoadSegment(
                blockPos1,
                blockPos1.offset(xDist / 4, 0, zDist / 4))
            .addRoadSegment(
                blockPos1.offset(xDist / 4, 0, zDist / 4),
                blockPos1.offset(2 * xDist / 4, 0, 2 * zDist / 4))
            .addRoadSegment(
                blockPos1.offset(2 * xDist / 4, 0, 2 * zDist / 4),
                blockPos1.offset(3 * xDist / 4, 0, 3 * zDist / 4))
            .addRoadSegment(
                blockPos1.offset(3 * xDist / 4, 0, 3 * zDist / 4),
                blockPos2);

        // Ensure road does not cross ocean
        for (RoadSegment roadSegment : road.getRoadSegments()) {
            if (serverLevel.getChunkSource().getGenerator().getNoiseBiome(
                            QuartPos.fromBlock(roadSegment.getStartPos().getX()),
                            QuartPos.fromBlock(0),
                            QuartPos.fromBlock(roadSegment.getStartPos().getZ()))
                    .is(BiomeTags.IS_OCEAN)) {
                return Optional.empty();
            }
        }

        return Optional.of(road);
    }

    @Override
    public void placeRoad(Road road, WorldGenLevel level, Random rand, BlockPos blockPos, @Nullable BlockPos nearestVillage) {
        ChunkPos chunkPos = new ChunkPos(blockPos);

        // Short-circuit if this chunk isn't between the start/end points of the road
        if (chunkPos.getWorldPosition().getX() + 15 < road.getVillageStart().getX() || chunkPos.getWorldPosition().getX() > road.getVillageEnd().getX()) {
            return;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // Determine total slope of line from starting point to ending point
        int totalXDiff = road.getVillageEnd().getX() - road.getVillageStart().getX();
        int totalZDiff = road.getVillageEnd().getZ() - road.getVillageStart().getZ();
        double totalSlope = totalXDiff == 0 ? Integer.MAX_VALUE : totalZDiff / (double) totalXDiff;
        int xDir = totalXDiff >= 0 ? 1 : -1; // x direction multiplier
        int zDir = totalZDiff >= 0 ? 1 : -1; // z direction multiplier

        // Initialize mutable at starting point
        mutable.set(road.getVillageStart());

        YungsRoadsCommon.LOGGER.info(this);

        double slopeCounter = Math.abs(totalSlope);

        while (!isInRange(mutable, road.getVillageEnd())) {
            // Move in z direction
            while (slopeCounter >= 1 && !isInRange(mutable, road.getVillageEnd())) {
                placePath(level, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
                mutable.move(0, 0, zDir);
                slopeCounter--;
            }

            while (slopeCounter < 1 && !isInRange(mutable, road.getVillageEnd())) {
                placePath(level, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
                mutable.move(xDir, 0, 0);
                slopeCounter += Math.abs(totalSlope);
            }

            // Place path at final position
            placePath(level, rand, mutable, nearestVillage, chunkPos.x, chunkPos.z);
        }
    }
}
