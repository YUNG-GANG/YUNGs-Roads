package com.yungnickyoung.minecraft.yungsroads.mixin;

import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.overlay.DebugOverlayGui;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugOverlayGui.class)
public abstract class DebugOverlayGuiMixin {
    @Shadow
    protected abstract ServerWorld func_238515_d_();

    @Shadow
    @Final
    private Minecraft mc;

    @Inject(method = "getDebugInfoLeft", at = @At("RETURN"))
    public void attachStructureRegionPosToDebugOverlay(CallbackInfoReturnable<List<String>> cir) {
        List<String> list = cir.getReturnValue();
        ServerWorld serverworld = this.func_238515_d_();
        if (serverworld != null) {
            BlockPos blockpos = this.mc.getRenderViewEntity().getPosition();
            StructureRegionPos structureRegionPos = new StructureRegionPos(blockpos);
            String string = "Structure Region Pos: " + structureRegionPos;
            list.add(string);
        }
    }
}
