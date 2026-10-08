package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;
import java.util.function.BiConsumer;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {
    @Inject(method = "doPlace", at = @At("HEAD"), cancellable = true)
    private void yungsroads_preventTreesSpawningOnRoads(WorldGenLevel worldGenLevel, RandomSource random, BlockPos blockPos, BiConsumer<BlockPos, BlockState> $$3, BiConsumer<BlockPos, BlockState> $$4, FoliagePlacer.FoliageSetter $$5, TreeConfiguration $$6, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel serverLevel;
        if (worldGenLevel instanceof WorldGenRegion worldGenRegion) {
            serverLevel = worldGenRegion.getLevel();
        } else if (worldGenLevel instanceof ServerLevel sl) {
            serverLevel = sl;
        } else {
            YungsRoadsCommon.LOGGER.error("Unable to cast worldGenLevel to {} in TreeFeatureMixin", worldGenLevel.getClass().toString());
            return;
        }

        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) serverLevel).getStructureRegionCache();

        // Keeps a block clear past the road's edge, so trunks don't stand at its very edge
        // TODO - make this toggleable
        if (structureRegionCache.hasRoadNear(blockPos, 1)) {
            cir.setReturnValue(false);
        }
    }
}
