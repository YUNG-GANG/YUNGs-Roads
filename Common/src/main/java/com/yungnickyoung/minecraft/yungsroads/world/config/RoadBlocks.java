package com.yungnickyoung.minecraft.yungsroads.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;

/**
 * The blocks a road type builds with. Not tunable in game, so it's shared between copies of {@link RoadSettings}.
 *
 * @param surfaces The road's blocks and width over each kind of ground. Each column uses the first that matches.
 * @param bridgeBlockStates The blocks bridges over water and land bridges are built from.
 * @param bridgeRailingBlockStates The railings along the edges of land bridges with a drop beside them. Fences, walls,
 *                                 panes, and bars connect to each other. Without it, land bridges have no railings.
 * @param tunnelLiningBlockStates The blocks lining the walls and ceilings of tunnels, which also close off any caves
 *                                the tunnels pass through. Without it, tunnels are bare, with only unstable blocks and
 *                                fluids in their walls replaced by solid ones.
 */
public record RoadBlocks(List<RoadSurfaceConfig> surfaces, BlockStateRandomizer bridgeBlockStates,
                         Optional<BlockStateRandomizer> bridgeRailingBlockStates,
                         Optional<BlockStateRandomizer> tunnelLiningBlockStates) {
    public static final Codec<RoadBlocks> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    RoadSurfaceConfig.CODEC.listOf().fieldOf("surfaces").forGetter(RoadBlocks::surfaces),
                    BlockStateRandomizer.CODEC.fieldOf("bridge_blockstates").forGetter(RoadBlocks::bridgeBlockStates),
                    BlockStateRandomizer.CODEC.optionalFieldOf("bridge_railing_blockstates").forGetter(RoadBlocks::bridgeRailingBlockStates),
                    BlockStateRandomizer.CODEC.optionalFieldOf("tunnel_lining_blockstates").forGetter(RoadBlocks::tunnelLiningBlockStates))
            .apply(instance, RoadBlocks::new));

    /** Only used if the default road type is missing, so roads can still be placed. */
    public static final RoadBlocks FALLBACK = new RoadBlocks(List.of(RoadSurfaceConfig.FALLBACK),
            new BlockStateRandomizer(Blocks.OAK_PLANKS.defaultBlockState()), Optional.empty(), Optional.empty());
}
