package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.world.road.decoration.ConfiguredRoadDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoadTypeConfig {
    public static final Codec<RoadTypeConfig> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    BlockState.CODEC.listOf().fieldOf("target_blocks").forGetter(settings -> settings.targetBlocks),
                    TempEnum.CODEC.fieldOf("target_temperature").forGetter(settings -> settings.targetTemperature),
                    BlockStateRandomizer.CODEC.fieldOf("path_blockstates").forGetter(settings -> settings.pathBlockStates),
                    BlockStateRandomizer.CODEC.optionalFieldOf("fill_blockstates").forGetter(settings -> settings.fillBlockStates),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("road_size_radius").forGetter(settings -> settings.roadSizeRadius),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("road_size_variation").forGetter(settings -> settings.roadSizeVariation))
//                    ConfiguredRoadDecoration.CODEC.listOf().fieldOf("decorations").forGetter(settings -> settings.decorations))
            .apply(instance, RoadTypeConfig::new));

    public final List<BlockState> targetBlocks;
    public final TempEnum targetTemperature;
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
    private RoadTypeConfig(List<BlockState> targetBlocks, TempEnum targetTemperature, BlockStateRandomizer pathBlockStates,
                           Optional<BlockStateRandomizer> fillBlockStates, float roadSizeRadius, float roadSizeVariation) {
        this(targetBlocks, targetTemperature, pathBlockStates, fillBlockStates, roadSizeRadius, roadSizeVariation, new ArrayList<>());
    }

    public RoadTypeConfig(List<BlockState> targetBlocks, TempEnum targetTemperature, BlockStateRandomizer pathBlockStates,
                          Optional<BlockStateRandomizer> fillBlockStates, float roadSizeRadius, float roadSizeVariation,
                          List<ConfiguredRoadDecoration> decorations) {
        this.targetBlocks = targetBlocks;
        this.targetTemperature = targetTemperature;
        this.pathBlockStates = pathBlockStates;
        this.fillBlockStates = fillBlockStates;
        this.roadSizeRadius = roadSizeRadius;
        this.roadSizeVariation = roadSizeVariation;
        this.decorations = decorations;
    }

    public boolean matches(LevelAccessor levelAccessor, BlockPos pos) {
        BlockState currState = levelAccessor.getBlockState(pos);

        // Validate biome temp
        if (this.targetTemperature == TempEnum.COLD && !levelAccessor.getBiome(pos).value().coldEnoughToSnow(pos)) {
            return false;
        }
        if (this.targetTemperature == TempEnum.WARM && !levelAccessor.getBiome(pos).value().warmEnoughToRain(pos)) {
            return false;
        }

        // Check for matching target block
        return this.targetBlocks.stream().anyMatch(blockState -> blockState.is(currState.getBlock()));
    }
}
