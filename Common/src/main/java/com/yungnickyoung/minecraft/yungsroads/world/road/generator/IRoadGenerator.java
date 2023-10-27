package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.world.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypeSettings;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.RoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Material;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;

public interface IRoadGenerator {
    /**
     * Attempts to generate a {@link Road} connecting two chunk positions.<br />
     * Note that this simply constructs the {@link Road} object. Blocks are not actually placed until
     * {@link RoadFeature#place(FeaturePlaceContext)}.
     *
     * @return Road connecting the two positions, if one was successfully generated.
     */
    Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2);

    /**
     * Places the {@link Road} for blocks within a given chunk.
     *
     * @param road The {@link Road} to place.
     * @param world The world, passed in during feature generation.
     * @param rand Random passed in during feature generation.
     * @param blockPos A block pos within the chunk we want to operate on. Should be passed in during feature generation.
     *                 Note that ONLY this chunk will be modified during this function call. No other chunks will be touched,
     *                 even if they contain Road positions.
     * @param nearestVillage The location of the nearest village to this point.
     *                       Only used for rendering the debug view.
     */
    void placeRoad(Road road, WorldGenLevel world, Random rand, BlockPos blockPos, RoadFeatureConfiguration config, @Nullable BlockPos nearestVillage);

    default double getRoadSizeRadius() {
        return 2.83;
    }

    default boolean isInRange(BlockPos pos, BlockPos targetPos) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < 10 * 10;
    }

    default void placePath(WorldGenLevel world, Random random, BlockPos pos, BlockPos nearestVillage, int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        double roadWidthSq = getRoadSizeRadius() * getRoadSizeRadius();

        for (int x = -2; x < 3; x++) {
            for (int z = -2; z < 3; z++) {
                if (x * x + z * z < roadWidthSq) {
                    mutable.set(pos.getX() + x, pos.getY(), pos.getZ() + z);
                    mutable.setY(getSurfaceHeight(world, mutable));

//                    int seaLevelDistance = mutable.getY() - world.getSeaLevel();
//                    int yCompression = seaLevelDistance / 6;
//
//                    // Place air to destroy any floating plants and the like
//                    if (yCompression > 0) {
//                        mutable.move(Direction.UP);
//                        world.setBlockState(mutable, Blocks.AIR.defaultBlockState()(), 2);
//                        mutable.move(Direction.DOWN);
//                    }
//
//                    for (int y = 0; y < yCompression; y++) {
//                        world.setBlockState(mutable, Blocks.AIR.defaultBlockState()(), 2);
//                        mutable.move(Direction.DOWN);
//                    }

                    placePathBlock(world, random, mutable, nearestVillage);
                }
            }
        }
    }

    BlockStateRandomizer dirtReplacer = new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())
            .addBlock(Blocks.COBBLESTONE.defaultBlockState(), .25f)
            .addBlock(Blocks.STONE.defaultBlockState(), .25f)
            .addBlock(Blocks.ANDESITE.defaultBlockState(), .25f)
            .addBlock(Blocks.GRAVEL.defaultBlockState(), .25f);
    BlockStateRandomizer sandReplacer = new BlockStateRandomizer(Blocks.SAND.defaultBlockState())
            .addBlock(Blocks.GRAVEL.defaultBlockState(), .6f)
            .addBlock(Blocks.COBBLESTONE.defaultBlockState(), .2f)
            .addBlock(Blocks.ANDESITE.defaultBlockState(), .2f);
    BlockStateRandomizer snowReplacer = new BlockStateRandomizer(Blocks.SNOW_BLOCK.defaultBlockState())
            .addBlock(Blocks.DIRT_PATH.defaultBlockState(), .95f);
    BlockStateRandomizer stoneReplacer = new BlockStateRandomizer(Blocks.STONE.defaultBlockState())
            .addBlock(Blocks.COBBLESTONE.defaultBlockState(), .7f)
            .addBlock(Blocks.ANDESITE.defaultBlockState(), .2f);

    default void placePathBlock(WorldGenLevel world, Random random, BlockPos pos, RoadFeatureConfiguration config, @Nullable BlockPos nearestVillage, CarvingMask blockMask) {
        if (blockMask.get(pos.getX(), pos.getY(), pos.getZ())) return;
        placePathBlock(world, random, pos, config, nearestVillage);
        blockMask.set(pos.getX(), pos.getY(), pos.getZ());
    }

    default void placePathBlock(WorldGenLevel level, Random random, BlockPos pos, RoadFeatureConfiguration config, @Nullable BlockPos nearestVillage) {
        BlockState currState = level.getBlockState(pos);

        // Check for water to place bridge block.
        if (currState.getMaterial() == Material.WATER) {
            level.setBlock(pos, config.bridgeBlockStates.get(random), 2);
        }

        for (RoadTypeSettings roadTypeSettings : config.roadTypes) {
            if (roadTypeSettings.matches(level, pos)) {
                level.setBlock(pos, roadTypeSettings.pathBlockStates.get(random), 2);
                break;
            }
        }

        if (YungsRoadsCommon.DEBUG_MODE && nearestVillage != null) {
            DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));
        }
    }

    default void placePathBlock(WorldGenLevel world, Random random, BlockPos pos, @Nullable BlockPos nearestVillage) {
        BlockState currState = world.getBlockState(pos);
        if (currState == Blocks.GRASS_BLOCK.defaultBlockState() || currState == Blocks.DIRT.defaultBlockState() || currState == Blocks.PODZOL.defaultBlockState()) {
            if (world.getBiome(pos).value().coldEnoughToSnow(pos)) {
                world.setBlock(pos, snowReplacer.get(random), 2);
            } else {
                world.setBlock(pos, dirtReplacer.get(random), 2);
            }
        } else if (currState == Blocks.STONE.defaultBlockState() || currState == Blocks.ANDESITE.defaultBlockState() || currState == Blocks.GRANITE.defaultBlockState()) {
            world.setBlock(pos, stoneReplacer.get(random), 2);
        } else if (currState == Blocks.SNOW_BLOCK.defaultBlockState()) {
            world.setBlock(pos, snowReplacer.get(random), 2);
        } else if (currState == Blocks.SAND.defaultBlockState() || currState == Blocks.SANDSTONE.defaultBlockState()) {
            world.setBlock(pos, sandReplacer.get(random), 2);
        } else if (currState.getMaterial() == Material.WATER && pos.getY() == world.getSeaLevel() - 1) {
            world.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        }
        if (YungsRoadsCommon.DEBUG_MODE && nearestVillage != null) {
//            DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));
        }
    }

    default void placeDebugMarker(WorldGenLevel level, ChunkPos chunkPos, BlockPos blockPos, BlockState markerBlock) {
        if (isInChunk(chunkPos, blockPos)) {
            BlockPos.MutableBlockPos mutable = blockPos.mutable();
            mutable.setY(getSurfaceHeight(level, mutable));

            for (int y = 0; y < 10; y++) {
                mutable.move(Direction.UP);
                level.setBlock(mutable, markerBlock, 2);
            }
        }
    }

    default boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return chunkPos.equals(new ChunkPos(blockPos));
    }

    /**
     * Checks if the BlockPos is within a 1-chunk radius of the given ChunkPos.
     */
    default boolean isInValidRangeForChunk(ChunkPos chunkPos, BlockPos blockPos) {
        ChunkPos targetChunkPos = new ChunkPos(blockPos);
        return targetChunkPos.x >= chunkPos.x - 1 &&
               targetChunkPos.x <= chunkPos.x + 1 &&
               targetChunkPos.z >= chunkPos.z - 1 &&
               targetChunkPos.z <= chunkPos.z + 1;

    }

    default int getSurfaceHeight(WorldGenLevel world, BlockPos pos) {
        return world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) - 1;
    }

    default boolean containsRoad(ChunkPos chunkPos, Road road) {
        int roadStartX = road.getVillageStart().getX();
        int roadEndX = road.getVillageEnd().getX();
        int chunkStartX = chunkPos.getMinBlockX();
        int chunkEndX = chunkPos.getMaxBlockX();
        int chunkPad = 64; // We pad the cutoff by 4 chunks to allow for curved roads that temporarily exceed the min or max x-value
                           // defined by the road segment's start/end positions. The 4 here is arbitrary and may not cover
                           // all scenarios, but covers most without incurring too much performance cost.
        return (roadStartX >= chunkStartX - chunkPad || roadEndX >= chunkStartX - chunkPad)
                && (roadStartX <= chunkEndX  + chunkPad || roadEndX <= chunkEndX + chunkPad);
    }

    default boolean containsRoadSegment(ChunkPos chunkPos, RoadSegment roadSegment) {
        int roadSegmentStartX = roadSegment.getStartPos().getX();
        int roadSegmentEndX = roadSegment.getEndPos().getX();
        int chunkStartX = chunkPos.getMinBlockX();
        int chunkEndX = chunkPos.getMaxBlockX();
        int chunkPad = 64; // We pad the cutoff by 4 chunks to allow for curved roads that temporarily exceed the min or max x-value
                           // defined by the road segment's start/end positions. The 4 here is arbitrary and may not cover
                           // all scenarios, but covers most without incurring too much performance cost.
        return (roadSegmentStartX >= chunkStartX - chunkPad || roadSegmentEndX >= chunkStartX - chunkPad)
                && (roadSegmentStartX <= chunkEndX  + chunkPad || roadSegmentEndX <= chunkEndX + chunkPad);
    }
}
