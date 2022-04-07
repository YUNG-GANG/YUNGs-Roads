package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsapi.world.BlockSetSelector;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import com.yungnickyoung.minecraft.yungsroads.world.RoadFeature;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.NoFeatureConfig;

import java.util.BitSet;
import java.util.Optional;
import java.util.Random;

public interface IRoadGenerator {
    /**
     * Attempts to generate a {@link Road} connecting two chunk positions.<br />
     * Note that this simply constructs the {@link Road} object. Blocks are not actually placed until
     * {@link RoadFeature#generate(ISeedReader, ChunkGenerator, Random, BlockPos, NoFeatureConfig)}.
     *
     * @return Road connecting the two positions, if one was successfully able to be generated.
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
    void placeRoad(Road road, ISeedReader world, Random rand, BlockPos blockPos, BlockPos nearestVillage);

    default double getRoadWidth() {
        return 2.83;
    }

    default boolean isInRange(BlockPos pos, BlockPos targetPos) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < 10 * 10;
    }

    default void placePath(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage, int chunkX, int chunkZ) {
        BlockPos.Mutable mutable = pos.toMutable();
        double roadWidthSq = getRoadWidth() * getRoadWidth();

        for (int x = -2; x < 3; x++) {
            for (int z = -2; z < 3; z++) {
                if (x * x + z * z < roadWidthSq) {
                    mutable.setPos(pos.getX() + x, pos.getY(), pos.getZ() + z);
                    mutable.setY(getSurfaceHeight(world, mutable));

//                    int seaLevelDistance = mutable.getY() - world.getSeaLevel();
//                    int yCompression = seaLevelDistance / 6;
//
//                    // Place air to destroy any floating plants and the like
//                    if (yCompression > 0) {
//                        mutable.move(Direction.UP);
//                        world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 2);
//                        mutable.move(Direction.DOWN);
//                    }
//
//                    for (int y = 0; y < yCompression; y++) {
//                        world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 2);
//                        mutable.move(Direction.DOWN);
//                    }

                    placePathBlock(world, random, mutable, nearestVillage);
                }
            }
        }
    }

    BlockSetSelector dirtReplacer = new BlockSetSelector(Blocks.DIRT.getDefaultState())
            .addBlock(Blocks.GRASS_PATH.getDefaultState(), .6f)
            .addBlock(Blocks.GRASS_BLOCK.getDefaultState(), .2f);
    BlockSetSelector sandReplacer = new BlockSetSelector(Blocks.SAND.getDefaultState())
            .addBlock(Blocks.GRAVEL.getDefaultState(), .6f)
            .addBlock(Blocks.SANDSTONE.getDefaultState(), .3f);
    BlockSetSelector snowReplacer = new BlockSetSelector(Blocks.SNOW_BLOCK.getDefaultState())
            .addBlock(Blocks.ICE.getDefaultState(), .6f);
    BlockSetSelector stoneReplacer = new BlockSetSelector(Blocks.STONE.getDefaultState())
            .addBlock(Blocks.COBBLESTONE.getDefaultState(), .6f);

    default void placePathBlock(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage, BitSet blockMask) {
        int mask = Math.floorMod(pos.getX(), 16) | (Math.floorMod(pos.getZ(), 16) << 4) | (pos.getY() << 8);
        if (blockMask.get(mask)) return;
        placePathBlock(world, random, pos, nearestVillage);
        blockMask.set(mask);
    }

    default void placePathBlock(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage) {
        BlockState currState = world.getBlockState(pos);
        if (currState == Blocks.GRASS_BLOCK.getDefaultState() || currState == Blocks.DIRT.getDefaultState()) {
            world.setBlockState(pos, dirtReplacer.get(random), 2);
        } else if (currState == Blocks.STONE.getDefaultState() || currState == Blocks.ANDESITE.getDefaultState() || currState == Blocks.GRANITE.getDefaultState()) {
            world.setBlockState(pos, stoneReplacer.get(random), 2);
        } else if (currState == Blocks.SNOW_BLOCK.getDefaultState()) {
            world.setBlockState(pos, snowReplacer.get(random), 2);
        } else if (currState == Blocks.SAND.getDefaultState() || currState == Blocks.SANDSTONE.getDefaultState()) {
            world.setBlockState(pos, sandReplacer.get(random), 2);
        } else if (currState.getMaterial() == Material.WATER && pos.getY() == world.getSeaLevel() - 1) {
            world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState(), 2);
        }
        DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));

    }

    default boolean isInChunk(ChunkPos chunkPos, BlockPos blockPos) {
        return chunkPos.equals(new ChunkPos(blockPos));
    }

    default boolean isInValidRangeForChunk(ChunkPos chunkPos, BlockPos blockPos) {
        ChunkPos targetChunkPos = new ChunkPos(blockPos);
        return targetChunkPos.x >= chunkPos.x - 1 &&
               targetChunkPos.x <= chunkPos.x + 1 &&
               targetChunkPos.z >= chunkPos.z - 1 &&
               targetChunkPos.z <= chunkPos.z + 1;

    }

    default int getSurfaceHeight(ISeedReader world, BlockPos pos) {
        return world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) - 1;
    }

    default boolean containsRoad(ChunkPos chunkPos, Road road) {
        int roadStartX = road.getVillageStart().getX();
        int roadEndX = road.getVillageEnd().getX();
        int chunkStartX = chunkPos.getXStart();
        int chunkEndX = chunkPos.getXEnd();
        int chunkPad = 64; // We pad the cutoff by 4 chunks to allow for curved roads that temporarily exceed the min or max x-value
                           // defined by the road segment's start/end positions. The 4 here is arbitrary and may not cover
                           // all scenarios, but covers most without incurring too much performance cost.
        return (roadStartX >= chunkStartX - chunkPad || roadEndX >= chunkStartX - chunkPad)
                && (roadStartX <= chunkEndX  + chunkPad || roadEndX <= chunkEndX + chunkPad);
    }

    default boolean containsRoadSegment(ChunkPos chunkPos, RoadSegment roadSegment) {
        int roadSegmentStartX = roadSegment.getStartPos().getX();
        int roadSegmentEndX = roadSegment.getEndPos().getX();
        int chunkStartX = chunkPos.getXStart();
        int chunkEndX = chunkPos.getXEnd();
        int chunkPad = 64; // We pad the cutoff by 4 chunks to allow for curved roads that temporarily exceed the min or max x-value
                           // defined by the road segment's start/end positions. The 4 here is arbitrary and may not cover
                           // all scenarios, but covers most without incurring too much performance cost.
        return (roadSegmentStartX >= chunkStartX - chunkPad || roadSegmentEndX >= chunkStartX - chunkPad)
                && (roadSegmentStartX <= chunkEndX  + chunkPad || roadSegmentEndX <= chunkEndX + chunkPad);
    }
}
