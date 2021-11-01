package com.yungnickyoung.minecraft.yungsroads.mixin.accessor;

import net.minecraft.world.gen.feature.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Structure.class)
public interface StructureAccessor {

    @Invoker(value = "func_230365_b_")
    boolean isLinearSeparation();
}
