package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;
import java.util.function.BiConsumer;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {
    @Inject(method = "doPlace", at = @At("HEAD"), cancellable = true)
    private void yungsroads_preventTreesSpawningOnRoads(WorldGenLevel worldGenLevel, Random $$1, BlockPos blockPos, BiConsumer<BlockPos, BlockState> $$3, BiConsumer<BlockPos, BlockState> $$4, TreeConfiguration $$5, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel serverLevel;
        if (worldGenLevel instanceof WorldGenRegion worldGenRegion) {
            serverLevel = worldGenRegion.getLevel();
        } else if (worldGenLevel instanceof ServerLevel sl) {
            serverLevel = sl;
        } else {
            YungsRoadsCommon.LOGGER.error("Unable to cast worldGenLevel to {} in TreeFeatureMixin", worldGenLevel.getClass().toString());
            return;
        }

        BlockPos pos = new BlockPos(blockPos.getX(), 0, blockPos.getZ());

        StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) serverLevel).getStructureRegionCache();
        StructureRegionPos structureRegionPos = new StructureRegionPos(pos);
        StructureRegion structureRegion = structureRegionCache.getRegion(structureRegionPos);

        // TODO - make this toggleable, and make the range either configurable or derived from road width
        if (structureRegion.hasRoadInRange(pos, 3)) {
            cir.setReturnValue(false);
        }
    }
}
