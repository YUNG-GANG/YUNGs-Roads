package com.yungnickyoung.minecraft.yungsroads.world.road;

import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import com.yungnickyoung.minecraft.yungsroads.world.RoadFeature;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.NoFeatureConfig;

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
     * @param chunkPos The position of the chunk we want to operate on. Should be passed in during feature generation.
     *                 Note that ONLY this chunk will be modified during this function call. No other chunks will be touched,
     *                 even if they contain Road positions.
     * @param nearestVillage The location of the nearest village to this point.
     *                       Only used for rendering the debug view.
     */
    void placeRoad(Road road, ISeedReader world, Random rand, ChunkPos chunkPos, BlockPos nearestVillage);

    default boolean isInRange(BlockPos pos, BlockPos targetPos) {
        double xDiff = pos.getX() - targetPos.getX();
        double zDiff = pos.getZ() - targetPos.getZ();
        return xDiff * xDiff + zDiff * zDiff < 10 * 10;
    }

    default void placePath(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage, int chunkX, int chunkZ) {
        BlockPos.Mutable mutable = pos.toMutable();

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (x * x + z * z < 9) {
                    mutable.setPos(pos.getX() + x, pos.getY(), pos.getZ() + z);
                    if (mutable.getX() >> 4 == chunkX && mutable.getZ() >> 4 == chunkZ) {
                        mutable.setY(world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, mutable.getX(), mutable.getZ()) - 1);
                        placePathBlock(world, random, mutable, nearestVillage);
                    }
                }
            }
        }
    }

    default void placePathBlock(ISeedReader world, Random random, BlockPos pos, BlockPos nearestVillage) {
        BlockState currState = world.getBlockState(pos);
        if (random.nextFloat() < .5f) {
            if (currState == Blocks.GRASS_BLOCK.getDefaultState() || currState == Blocks.DIRT.getDefaultState()) {
                world.setBlockState(pos, Blocks.GRASS_PATH.getDefaultState(), 2);
            } else if (currState == Blocks.SAND.getDefaultState() || currState == Blocks.RED_SAND.getDefaultState()) {
                world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 2);
            } else if (currState.getMaterial() == Material.WATER) {
                world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState(), 2);
            }
        }
        DebugRenderer.getInstance().addPath(new ChunkPos(pos), new ChunkPos(nearestVillage));
    }
}
