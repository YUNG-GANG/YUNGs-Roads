package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.world.road.decoration.ConfiguredRoadDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The blocks and width of a road over ground of a certain kind, such as dirt or sand. Each column of a road uses the
 * first of its road type's surfaces that matches the ground there.
 */
public class RoadSurfaceConfig {
    public static final Codec<RoadSurfaceConfig> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    BlockState.CODEC.listOf().fieldOf("target_blocks").forGetter(settings -> settings.targetBlocks),
                    BlockStateRandomizer.CODEC.fieldOf("path_blockstates").forGetter(settings -> settings.pathBlockStates),
                    BlockStateRandomizer.CODEC.optionalFieldOf("fill_blockstates").forGetter(settings -> settings.fillBlockStates),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("road_size_radius").forGetter(settings -> settings.roadSizeRadius),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("road_size_variation").forGetter(settings -> settings.roadSizeVariation))
//                    ConfiguredRoadDecoration.CODEC.listOf().fieldOf("decorations").forGetter(settings -> settings.decorations))
            .apply(instance, RoadSurfaceConfig::new));

    /** Used where none of a road type's surfaces match the ground. */
    public static final RoadSurfaceConfig FALLBACK = new RoadSurfaceConfig(
            List.of(Blocks.DIRT.defaultBlockState(),
                    Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.PODZOL.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState()),
            new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())
                    .addBlock(Blocks.GRASS_BLOCK.defaultBlockState(), 0.05f),
            Optional.of(new BlockStateRandomizer(Blocks.DIRT.defaultBlockState())),
            1.5f,
            2.0f,
            List.of());

    public final List<BlockState> targetBlocks;
    public final BlockStateRandomizer pathBlockStates;

    /**
     * The blocks that build up the ground under a road raised above it, including the ground block the road covers.
     * Without it, the ground's own block is copied, so a raised road's sides blend in with the terrain.
     */
    public final Optional<BlockStateRandomizer> fillBlockStates;
    public final float roadSizeRadius;
    public final float roadSizeVariation;
    public final List<ConfiguredRoadDecoration> decorations;

    // TODO temporary constructor to get it to compile. Delete this when redoing the codec/registration system for road decorations
    private RoadSurfaceConfig(List<BlockState> targetBlocks, BlockStateRandomizer pathBlockStates,
                              Optional<BlockStateRandomizer> fillBlockStates, float roadSizeRadius, float roadSizeVariation) {
        this(targetBlocks, pathBlockStates, fillBlockStates, roadSizeRadius, roadSizeVariation, new ArrayList<>());
    }

    public RoadSurfaceConfig(List<BlockState> targetBlocks, BlockStateRandomizer pathBlockStates,
                             Optional<BlockStateRandomizer> fillBlockStates, float roadSizeRadius, float roadSizeVariation,
                             List<ConfiguredRoadDecoration> decorations) {
        this.targetBlocks = targetBlocks;
        this.pathBlockStates = pathBlockStates;
        this.fillBlockStates = fillBlockStates;
        this.roadSizeRadius = roadSizeRadius;
        this.roadSizeVariation = roadSizeVariation;
        this.decorations = decorations;
    }

    public boolean matches(LevelAccessor levelAccessor, BlockPos pos) {
        BlockState currState = levelAccessor.getBlockState(pos);
        return this.targetBlocks.stream().anyMatch(blockState -> blockState.is(currState.getBlock()));
    }
}
