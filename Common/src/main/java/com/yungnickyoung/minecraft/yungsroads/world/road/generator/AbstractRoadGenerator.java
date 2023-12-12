package com.yungnickyoung.minecraft.yungsroads.world.road.generator;

import com.yungnickyoung.minecraft.yungsapi.noise.FastNoise;
import com.yungnickyoung.minecraft.yungsapi.world.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadFeatureConfiguration;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypeConfig;
import com.yungnickyoung.minecraft.yungsroads.world.config.TempEnum;
import com.yungnickyoung.minecraft.yungsroads.world.feature.RoadFeature;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.road.segment.DefaultRoadSegment;
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
import java.util.List;
import java.util.Optional;
import java.util.Random;

public abstract class AbstractRoadGenerator {
    private static final RoadTypeConfig DEFAULT_SETTINGS = new RoadTypeConfig(
            List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.DIRT_PATH),
            TempEnum.ANY,
            new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())
                    .addBlock(Blocks.GRASS_BLOCK.defaultBlockState(), 0.05f),
            1.5f,
            2.0f,
            List.of());

    private final FastNoise noise;

    public AbstractRoadGenerator() {
        this.noise = new FastNoise();
        this.noise.SetNoiseType(FastNoise.NoiseType.Simplex);
        this.noise.SetFrequency(.012f);
        this.noise.SetFractalOctaves(1);
    }

    /**
     * Attempts to generate a {@link Road} connecting two chunk positions.<br />
     * Note that this simply constructs the {@link Road} object. Blocks are not actually placed until
     * {@link RoadFeature#place(FeaturePlaceContext)}.
     *
     * @return Road connecting the two positions, if one was successfully generated.
     */
    public abstract Optional<Road> generateRoad(ChunkPos pos1, ChunkPos pos2);

    /**
     * Places the {@link Road} for blocks within a given chunk.
     *
     * @param road           The {@link Road} to place.
     * @param world          The world, passed in during feature generation.
     * @param rand           Random passed in during feature generation.
     * @param blockPos       A block pos within the chunk we want to operate on. Should be passed in during feature generation.
     *                       Note that ONLY this chunk will be modified during this function call. No other chunks will be touched,
     *                       even if they contain Road positions.
     * @param nearestVillage The location of the nearest village to this point.
     *                       Only used for rendering the debug view.
     */
    public abstract void placeRoad(Road road, WorldGenLevel world, Random rand, BlockPos blockPos,
                                   RoadFeatureConfiguration config, @Nullable BlockPos nearestVillage);

    /**
     * Determines the road type settings for a given position.
     * This is based on the biome and temperature at this position.
     */
    RoadTypeConfig getRoadTypeAtPos(WorldGenLevel level, BlockPos pos, RoadFeatureConfiguration config) {
        int surfaceHeight = getSurfaceHeight(level, pos);
        BlockPos surfacePos = new BlockPos(pos.getX(), surfaceHeight, pos.getZ());

        for (RoadTypeConfig roadType : config.roadTypes) {
            if (roadType.matches(level, surfacePos)) {
                return roadType;
            }
        }

        return DEFAULT_SETTINGS;
    }

    void placePath(WorldGenLevel level, Random random, BlockPos pos, ChunkPos chunkPos, RoadFeatureConfiguration config) {
        placePath(level, random, pos, chunkPos, config, null, null);
    }

    void placePath(WorldGenLevel level, Random random, BlockPos pos, ChunkPos chunkPos, RoadFeatureConfiguration config, @Nullable CarvingMask blockMask, @Nullable BlockPos nearestVillage) {
        if (!isInValidRangeForChunk(chunkPos, pos)) {
            return;
        }

        BlockPos.MutableBlockPos mutable = pos.mutable();

        // Determine the road type settings for this position.
        // This is based on the biome and temperature at this position.
//        int surfaceHeight = getSurfaceHeight(level, mutable);
//        mutable.setY(surfaceHeight);

        RoadTypeConfig roadTypeConfig = getRoadTypeAtPos(level, pos, config);
//        for (RoadTypeConfig roadType : config.roadTypes) {
//            if (roadType.matches(level, mutable)) {
//                roadTypeConfig = roadType;
//                break;
//            }
//        }

        // Determine path buffer space at this position.
        // This is used to subtly vary the path's width to make its shape more interesting.
        double pathBufferSpace = (noise.GetNoise(pos.getX(), pos.getZ()) + 1) * roadTypeConfig.roadSizeVariation;

        // Determine the furthest away a block can be placed from the current position.
        // Distances are kept as squared values as an optimization.
        double maxRoadDistSq = roadTypeConfig.roadSizeRadius * roadTypeConfig.roadSizeRadius + pathBufferSpace;

        // At each path position, we place a small circle of blocks at surface height
        for (int x = -2; x < 3; x++) {
            for (int z = -2; z < 3; z++) {
                if (x * x + z * z < maxRoadDistSq) {
                    mutable.set(pos.getX() + x, 0, pos.getZ() + z);

                    if (!isInValidRangeForChunk(chunkPos, mutable)) {
                        continue;
                    }

                    // Adjust y-coordinate based on surface height
                    int surfaceHeight = getSurfaceHeight(level, mutable);
                    mutable.setY(surfaceHeight);

                    placePathBlock(level, random, mutable, config, blockMask, nearestVillage);
                }
            }
        }
    }

    /**
     * Places a single path block at the given position.
     * Uses the RoadFeatureConfiguration to determine which block to place.
     */
    private void placePathBlock(WorldGenLevel level, Random random, BlockPos pos, RoadFeatureConfiguration config, @Nullable CarvingMask blockMask, @Nullable BlockPos nearestVillage) {
        if (blockMask != null && blockMask.get(pos.getX(), pos.getY(), pos.getZ())) return;

        BlockState currState = level.getBlockState(pos);
        RoadTypeConfig roadTypeConfig = getRoadTypeAtPos(level, pos, config);

        // Check for water to place bridge block.
        if (currState.getMaterial() == Material.WATER) {
            level.setBlock(pos, config.bridgeBlockStates.get(random), 2);
        }

        // Otherwise, set path block
        level.setBlock(pos, roadTypeConfig.pathBlockStates.get(random), 2);

        if (YungsRoadsCommon.DEBUG_MODE && nearestVillage != null) {
            DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));
        }

        if (blockMask != null) blockMask.set(pos.getX(), pos.getY(), pos.getZ());
    }

    void DEBUGplacePath(WorldGenLevel level, BlockPos pos, ChunkPos chunkPos, @Nullable CarvingMask blockMask, @Nullable BlockPos nearestVillage, BlockState blockState) {
        if (!isInValidRangeForChunk(chunkPos, pos)) {
            return;
        }
        DEBUGplaceBlock(level, new BlockPos(pos.getX(), getSurfaceHeight(level, pos), pos.getZ()), blockState, blockMask, nearestVillage);
    }

    private void DEBUGplaceBlock(WorldGenLevel level, BlockPos pos, BlockState blockState, @Nullable CarvingMask blockMask, @Nullable BlockPos nearestVillage) {
        if (blockMask != null && blockMask.get(pos.getX(), pos.getY(), pos.getZ())) return;

        level.setBlock(pos, blockState, 2);

        if (YungsRoadsCommon.DEBUG_MODE && nearestVillage != null) {
            DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));
        }

        if (blockMask != null) blockMask.set(pos.getX(), pos.getY(), pos.getZ());
    }

    void placeDebugMarker(WorldGenLevel level, ChunkPos chunkPos, BlockPos blockPos, BlockState markerBlock) {
        if (isInChunk(chunkPos, blockPos)) {
            BlockPos.MutableBlockPos mutable = blockPos.mutable();
            mutable.setY(getSurfaceHeight(level, mutable));

            for (int y = 0; y < 10; y++) {
                mutable.move(Direction.UP);
                if (level.getBlockState(mutable).isAir()) {
                    level.setBlock(mutable, markerBlock, 2);
                }
            }
        }
    }

    boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return chunkPos.equals(new ChunkPos(blockPos));
    }

    /**
     * Checks if the BlockPos is within a 1-chunk radius of the given ChunkPos.
     */
    boolean isInValidRangeForChunk(ChunkPos chunkPos, BlockPos blockPos) {
        ChunkPos targetChunkPos = new ChunkPos(blockPos);
        return targetChunkPos.x >= chunkPos.x - 1 &&
                targetChunkPos.x <= chunkPos.x + 1 &&
                targetChunkPos.z >= chunkPos.z - 1 &&
                targetChunkPos.z <= chunkPos.z + 1;

    }

    int getSurfaceHeight(WorldGenLevel world, BlockPos pos) {
        return world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) - 1;
    }

    boolean containsRoad(ChunkPos chunkPos, Road road) {
        int roadStartX = road.getVillageStart().getX();
        int roadEndX = road.getVillageEnd().getX();
        int chunkStartX = chunkPos.getMinBlockX();
        int chunkEndX = chunkPos.getMaxBlockX();
        int chunkPad = 64; // We pad the cutoff by 4 chunks to allow for curved roads that temporarily exceed the min or max x-value
        // defined by the road segment's start/end positions. The 4 here is arbitrary and may not cover
        // all scenarios, but covers most without incurring too much performance cost.
        return (roadStartX >= chunkStartX - chunkPad || roadEndX >= chunkStartX - chunkPad)
                && (roadStartX <= chunkEndX + chunkPad || roadEndX <= chunkEndX + chunkPad);
    }

    boolean containsRoadSegment(ChunkPos chunkPos, DefaultRoadSegment roadSegment) {
        int roadSegmentStartX = roadSegment.getStartPos().getX();
        int roadSegmentEndX = roadSegment.getEndPos().getX();
        int chunkStartX = chunkPos.getMinBlockX();
        int chunkEndX = chunkPos.getMaxBlockX();
        int chunkPad = 64; // We pad the cutoff by 4 chunks to allow for curved roads that temporarily exceed the min or max x-value
        // defined by the road segment's start/end positions. The 4 here is arbitrary and may not cover
        // all scenarios, but covers most without incurring too much performance cost.
        return (roadSegmentStartX >= chunkStartX - chunkPad || roadSegmentEndX >= chunkStartX - chunkPad)
                && (roadSegmentStartX <= chunkEndX + chunkPad || roadSegmentEndX <= chunkEndX + chunkPad);
    }
}
