package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.segment.DefaultRoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;

public class LinearRoadGenerator extends AbstractRoadGenerator {
    private final ServerLevel serverLevel;

    public LinearRoadGenerator(ServerLevel serverLevel) {
        super();
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
        for (DefaultRoadSegment roadSegment : road.getRoadSegments()) {
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
    public void placeRoad(Road road, WorldGenLevel level, Random rand, BlockPos blockPos, RoadFeatureConfiguration config, @Nullable BlockPos nearestVillage) {
        // The position of the chunk we're currently confined to
        ChunkPos chunkPos = new ChunkPos(blockPos);

        // Short-circuit if this chunk isn't between the start/end points of the road
        if (!containsRoad(chunkPos, road)) {
            return;
        }

        // Debug markers at road endpoints points
        if (YungsRoadsCommon.DEBUG_MODE) {
            placeDebugMarker(level, chunkPos, road.getVillageStart(), Blocks.EMERALD_BLOCK.defaultBlockState());
            placeDebugMarker(level, chunkPos, road.getVillageEnd(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        }

        // Determine total slope of line from starting point to ending point
        int totalXDiff = road.getVillageEnd().getX() - road.getVillageStart().getX();
        int totalZDiff = road.getVillageEnd().getZ() - road.getVillageStart().getZ();
        double totalSlope = totalXDiff == 0 ? Integer.MAX_VALUE : totalZDiff / (double) totalXDiff;
        int xDir = totalXDiff >= 0 ? 1 : -1; // x direction multiplier
        int zDir = totalZDiff >= 0 ? 1 : -1; // z direction multiplier

        double slopeCounter = Math.abs(totalSlope);
        BlockPos.MutableBlockPos mutable = road.getVillageStart().mutable();

        while (!isWithin10Blocks(mutable, road.getVillageEnd())) {
            // Move in z direction
            while (slopeCounter >= 1 && !isWithin10Blocks(mutable, road.getVillageEnd())) {
                placePath(level, rand, mutable, chunkPos, config, null, nearestVillage);
                mutable.move(0, 0, zDir);
                slopeCounter--;
            }

            // Move in x direction
            while (slopeCounter < 1 && !isWithin10Blocks(mutable, road.getVillageEnd())) {
                placePath(level, rand, mutable, chunkPos, config, null, nearestVillage);
                mutable.move(xDir, 0, 0);
                slopeCounter += Math.abs(totalSlope);
            }

            // Place path at final position
            placePath(level, rand, mutable, chunkPos, config, null, nearestVillage);
        }
    }

    private boolean isWithin10Blocks(BlockPos pos, BlockPos targetPos) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < 10 * 10;
    }
}
