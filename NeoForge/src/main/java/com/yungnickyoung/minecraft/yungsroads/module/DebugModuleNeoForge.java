package com.yungnickyoung.minecraft.yungsroads.module;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.client.RoadDebugClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Hooks up the client side of the road debug tools. Only loaded on the client.
 */
public class DebugModuleNeoForge {
    public static void init(IEventBus eventBus) {
        if (!YungsRoadsCommon.DEBUG_MODE) {
            return;
        }

        eventBus.addListener(DebugModuleNeoForge::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(DebugModuleNeoForge::onClientTick);
        NeoForge.EVENT_BUS.addListener(DebugModuleNeoForge::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(DebugModuleNeoForge::onLoggingOut);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RoadDebugClient.OPEN_SCREEN_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        RoadDebugClient.onClientTick();
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Fired while the camera's rotation is applied to the model-view matrix
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            RoadDebugClient.onRenderLevel(event.getCamera());
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RoadDebugClient.onDisconnect();
    }
}
