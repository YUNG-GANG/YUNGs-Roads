package com.yungnickyoung.minecraft.yungsroads.init;

import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public class YRModDebug {
    static int timer = 20;
    static boolean canUpdate = true;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(YRModDebug::renderDebugMap);
        MinecraftForge.EVENT_BUS.addListener(YRModDebug::onKeyPress);
        MinecraftForge.EVENT_BUS.addListener(YRModDebug::onTick);
    }

    public static void renderDebugMap(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.DEBUG) {
            if (DebugRenderer.getInstance().enabled) {
                DebugRenderer.getInstance().render(Minecraft.getInstance(), event.getMatrixStack());
                event.setCanceled(true);
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

    public static void onTick(TickEvent.RenderTickEvent event) {
        timer--;
        if (timer < 0) {
            canUpdate = true;
            timer = 20;
        }
    }
}
