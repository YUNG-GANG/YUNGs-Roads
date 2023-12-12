package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

public class DebugModuleForge {
    static int timer = 20;
    static boolean canUpdate = true;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(DebugModuleForge::renderDebugMap);
        MinecraftForge.EVENT_BUS.addListener(DebugModuleForge::onKeyPress);
        MinecraftForge.EVENT_BUS.addListener(DebugModuleForge::onTick);
        MinecraftForge.EVENT_BUS.addListener(DebugModuleForge::onWorldUnload);
    }

    public static void renderDebugMap(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.DEBUG) {
            if (DebugRenderer.getInstance().enabled) {
                DebugRenderer.getInstance().render(Minecraft.getInstance(), event.getMatrixStack());
            }
        }
    }

    public static void onKeyPress(InputEvent.KeyInputEvent event) {
        if (event.getKey() == 292 && Screen.hasControlDown()) {
            if (canUpdate) {
                DebugRenderer.getInstance().enabled = !DebugRenderer.getInstance().enabled;

                // Reset timer
                canUpdate = false;
                timer = 20;
            }
        }
    }

    public static void onWorldUnload(WorldEvent.Unload event) {
        DebugRenderer.getInstance().clearAll();
    }

    public static void onTick(TickEvent.RenderTickEvent event) {
        timer--;
        if (timer < 0) {
            canUpdate = true;
            timer = 20;
        }
    }
}
