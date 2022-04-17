package com.yungnickyoung.minecraft.yungsroads.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yungnickyoung.minecraft.yungsroads.module.PlacementModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.Random;

public class BlockBelowFilter extends PlacementFilter {
    public static final Codec<BlockBelowFilter> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(BlockPredicate.CODEC.fieldOf("blockBelowPredicate").forGetter(filter -> filter.blockBelowPredicate))
            .apply(builder, BlockBelowFilter::new));
    private final BlockPredicate blockBelowPredicate;

    private BlockBelowFilter(BlockPredicate blockBelowPredicate) {
        this.blockBelowPredicate = blockBelowPredicate;
    }

    public static BlockBelowFilter forPredicate(BlockPredicate blockBelowPredicate) {
        return new BlockBelowFilter(blockBelowPredicate);
    }

    protected boolean shouldPlace(PlacementContext placementContext, Random random, BlockPos blockPos) {
        return this.blockBelowPredicate.test(placementContext.getLevel(), blockPos.below());
    }

    public PlacementModifierType<?> type() {
        return PlacementModule.BLOCK_BELOW_FILTER;
    }
}
