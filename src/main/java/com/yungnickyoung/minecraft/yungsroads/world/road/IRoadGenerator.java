package com.yungnickyoung.minecraft.yungsroads.world.road;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import com.yungnickyoung.minecraft.yungsroads.world.RoadFeature;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
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
    Optional<Road> generate(ChunkPos pos1, ChunkPos pos2);
}
