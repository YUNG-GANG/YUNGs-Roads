package com.yungnickyoung.minecraft.yungsroads.module;

import com.mojang.blaze3d.platform.InputConstants;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.DebugRenderer;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.lwjgl.glfw.GLFW;

public class DebugModuleNeoForge {
//    private static int timer = 20;
//    private static boolean canUpdate = true;
    private static final KeyMapping keyMapping = new KeyMapping("key.yungsroads.debugMapKey",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.ui");

    public static void init(IEventBus eventBus) {
        if (!YungsRoadsCommon.DEBUG_MODE) {
            return;
        }

        eventBus.addListener(DebugModuleNeoForge::onRegisterKeyMappingsEvent);
        NeoForge.EVENT_BUS.addListener(DebugModuleNeoForge::onWorldUnload);
        NeoForge.EVENT_BUS.addListener(DebugModuleNeoForge::onClientTick);
    }

//    public static void renderDebugMap(RenderGameOverlayEvent.Pre event) {
//        if (event.getType() == RenderGameOverlayEvent.ElementType.DEBUG) {
//            DebugRenderer.getInstance().render(Minecraft.getInstance(), event.getMatrixStack());
//        }
//    }

    private static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent event) {
        event.register(keyMapping);

//        if (event.getKey() == 292 && Screen.hasControlDown()) {
//            if (canUpdate) {
//                DebugRenderer.getInstance().enabled = !DebugRenderer.getInstance().enabled;
//
//                // Reset timer
//                canUpdate = false;
//                timer = 20;
//            }
//        }
    }

    public static void onWorldUnload(LevelEvent.Unload event) {
        DebugRenderer.getInstance().clearAll();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (keyMapping.consumeClick()) {
            DebugRenderer.getInstance().enabled = !DebugRenderer.getInstance().enabled;
        }
    }
}
