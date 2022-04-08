package com.yungnickyoung.minecraft.yungsroads.mixin;

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

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin extends GuiComponent {
    @Shadow
    protected abstract ServerLevel getServerLevel();

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    public void attachStructureRegionPosToDebugOverlay(CallbackInfoReturnable<List<String>> cir) {
        List<String> list = cir.getReturnValue();
        ServerLevel serverworld = this.getServerLevel();
        if (serverworld != null) {
            BlockPos blockpos = this.minecraft.getCameraEntity().blockPosition();
            StructureRegionPos structureRegionPos = new StructureRegionPos(blockpos);
            String string = "Structure Region Pos: " + structureRegionPos;
            list.add(string);
        }
    }
}
