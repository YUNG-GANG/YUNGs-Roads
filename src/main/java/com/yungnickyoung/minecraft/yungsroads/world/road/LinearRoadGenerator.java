package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;

import java.util.Optional;

public class LinearRoadGenerator implements IRoadGenerator {
    private final ServerWorld world;

    public LinearRoadGenerator(ServerWorld world) {
        this.world = world;
    }

    @Override
    public Optional<Road> generate(ChunkPos pos1, ChunkPos pos2) {
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
}
