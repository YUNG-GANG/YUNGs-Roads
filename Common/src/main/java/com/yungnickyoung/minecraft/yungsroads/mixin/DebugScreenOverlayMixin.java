package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
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
import java.util.Optional;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin extends GuiComponent {
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

            // Values generated at this Node during road generation
            if (minecraft.player != null) {
                BlockPos playerPos = minecraft.player.blockPosition();
                StructureRegionCache structureRegionCache = ((IStructureRegionCacheProvider) serverLevel).getStructureRegionCache();
                StructureRegion structureRegion = structureRegionCache.getRegion(structureRegionPos);
                Optional<Road> roadOptional = structureRegion.getRoadAt(playerPos);

                String fStr = "N/A";
                String gStr = "N/A";
                String hStr = "N/A";
                String pathFactorStr = "N/A";
                String slopeFactorStr = "N/A";
                String altitudeFactorStr = "N/A";

                if (roadOptional.isPresent()) {
                    Road road = roadOptional.get();
                    Optional<Road.DebugNode> debugNodeOptional = road.positions.stream()
                            .filter(node -> node.jitteredPos.getX() == playerPos.getX() && node.jitteredPos.getZ() == playerPos.getZ())
                            .findFirst();
                    if (debugNodeOptional.isPresent()) {
                        Road.DebugNode debugNode = debugNodeOptional.get();
                        fStr = String.format("%.2f", debugNode.f);
                        gStr = String.format("%.2f", debugNode.g);
                        hStr = String.format("%.2f", debugNode.h);
                        pathFactorStr = String.format("%.2f", debugNode.pathFactor);
                        slopeFactorStr = String.format("%.2f", debugNode.slopeFactor);
                        altitudeFactorStr = String.format("%.2f", debugNode.altitudePunishment);
                    }
//                    String string2 = "pathFactor: " + AStarRoadGenerator.calcG(serverLevel, )
                }

                String string2 = "f: " + fStr + ", g: " + gStr + ", h: " + hStr;
                String string3 = "pathFactor: " + pathFactorStr + " slopeFactor: " + slopeFactorStr + " altitudeFactor: " + altitudeFactorStr;
                list.add(string2);
                list.add(string3);
            }
        }
    }
}
