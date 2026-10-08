package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import com.yungnickyoung.minecraft.yungsroads.world.road.decoration.ConfiguredRoadDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The blocks of a road over ground of a certain kind, such as dirt or sand. Each column of a road uses the
 * first of its road type's surfaces that matches the ground there.
 */
public class RoadSurfaceConfig {
    /**
     * One of the ground blocks a surface is used on: a block id, or a block tag starting with {@code #}, such as
     * {@code #minecraft:dirt}, which also matches blocks other mods add to it. Files written before tags were supported
     * have block states, which are read as their block's id.
     */
    public static final Codec<ExtraCodecs.TagOrElementLocation> TARGET_BLOCK_CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, BlockState.CODEC)
            .xmap(either -> either.map(Function.identity(), state -> block(state.getBlock())), Either::<ExtraCodecs.TagOrElementLocation, BlockState>left)
            .validate(target -> target.tag() || BuiltInRegistries.BLOCK.containsKey(target.id())
                    ? DataResult.success(target)
                    : DataResult.error(() -> "Unknown block " + target.id()));

    public static final Codec<RoadSurfaceConfig> CODEC = RecordCodecBuilder.create((instance) -> instance
            .group(
                    TARGET_BLOCK_CODEC.listOf().fieldOf("target_blocks").forGetter(settings -> settings.targetBlocks),
                    BlockStateRandomizer.CODEC.fieldOf("path_blockstates").forGetter(settings -> settings.pathBlockStates),
                    BlockStateRandomizer.CODEC.optionalFieldOf("fill_blockstates").forGetter(settings -> settings.fillBlockStates))
//                    ConfiguredRoadDecoration.CODEC.listOf().fieldOf("decorations").forGetter(settings -> settings.decorations))
            .apply(instance, RoadSurfaceConfig::new));

    /** Used where none of a road type's surfaces match the ground. */
    public static final RoadSurfaceConfig FALLBACK = new RoadSurfaceConfig(
            List.of(block(Blocks.DIRT), block(Blocks.GRASS_BLOCK), block(Blocks.PODZOL), block(Blocks.DIRT_PATH)),
            new BlockStateRandomizer(Blocks.DIRT_PATH.defaultBlockState())
                    .addBlock(Blocks.GRASS_BLOCK.defaultBlockState(), 0.05f),
            Optional.of(new BlockStateRandomizer(Blocks.DIRT.defaultBlockState())),
            List.of());

    /** The ground blocks the surface replaces, as block ids and block tags. See {@link #TARGET_BLOCK_CODEC}. */
    public final List<ExtraCodecs.TagOrElementLocation> targetBlocks;
    public final BlockStateRandomizer pathBlockStates;

    /**
     * The blocks that build up the ground under a road raised above it, including the ground block the road covers.
     * Without it, the ground's own block is copied, so a raised road's sides blend in with the terrain.
     */
    public final Optional<BlockStateRandomizer> fillBlockStates;
    public final List<ConfiguredRoadDecoration> decorations;

    /** The target blocks given by id, and those given by tag, for matching the ground quickly. */
    private final Set<Block> targetBlockSet = new HashSet<>();
    private final List<TagKey<Block>> targetTags = new ArrayList<>();

    // TODO temporary constructor to get it to compile. Delete this when redoing the codec/registration system for road decorations
    private RoadSurfaceConfig(List<ExtraCodecs.TagOrElementLocation> targetBlocks, BlockStateRandomizer pathBlockStates,
                              Optional<BlockStateRandomizer> fillBlockStates) {
        this(targetBlocks, pathBlockStates, fillBlockStates, new ArrayList<>());
    }

    public RoadSurfaceConfig(List<ExtraCodecs.TagOrElementLocation> targetBlocks, BlockStateRandomizer pathBlockStates,
                             Optional<BlockStateRandomizer> fillBlockStates, List<ConfiguredRoadDecoration> decorations) {
        this.targetBlocks = List.copyOf(targetBlocks);
        this.pathBlockStates = pathBlockStates;
        this.fillBlockStates = fillBlockStates;
        this.decorations = decorations;
        for (ExtraCodecs.TagOrElementLocation target : this.targetBlocks) {
            if (target.tag()) {
                this.targetTags.add(TagKey.create(Registries.BLOCK, target.id()));
            } else {
                BuiltInRegistries.BLOCK.getOptional(target.id()).ifPresent(this.targetBlockSet::add);
            }
        }
    }

    /** A target block given by its id. */
    public static ExtraCodecs.TagOrElementLocation block(Block block) {
        return new ExtraCodecs.TagOrElementLocation(BuiltInRegistries.BLOCK.getKey(block), false);
    }

    public boolean matches(LevelAccessor levelAccessor, BlockPos pos) {
        BlockState state = levelAccessor.getBlockState(pos);
        if (this.targetBlockSet.contains(state.getBlock())) {
            return true;
        }
        for (TagKey<Block> tag : this.targetTags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
