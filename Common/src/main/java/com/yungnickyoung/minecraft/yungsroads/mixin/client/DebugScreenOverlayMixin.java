package com.yungnickyoung.minecraft.yungsroads.mixin.client;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Shadow
    protected abstract ServerLevel getServerLevel();

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    public void yungsroads_attachExtraInfoToDebugOverlay(CallbackInfoReturnable<List<String>> cir) {
        if (!YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info) {
            return;
        }

        List<String> list = cir.getReturnValue();
        ServerLevel serverLevel = this.getServerLevel();
        if (serverLevel != null) {
            // Structure region
            BlockPos blockpos = this.minecraft.getCameraEntity().blockPosition();
            StructureRegionPos structureRegionPos = new StructureRegionPos(blockpos);
            String string = "Structure Region Pos: " + structureRegionPos;
            list.add(string);

            // Values generated at this Node during road generation.
            // Only already loaded regions are searched, since this runs on the render thread.
            if (minecraft.player != null) {
                BlockPos playerPos = minecraft.player.blockPosition();
                StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) serverLevel).getStructureRegionCache();
                Road.DebugNode debugNode = structureRegionCache.findLoadedDebugNodeAt(playerPos);

                String nodeInfo = debugNode == null
                        ? "Road node: N/A"
                        : String.format("Road node: f: %.2f, g: %.2f, h: %.2f", debugNode.g + debugNode.h, debugNode.g, debugNode.h);
                list.add(nodeInfo);
            }
        }
    }
}
