package com.yungnickyoung.minecraft.yungsroads;

import com.yungnickyoung.minecraft.yungsroads.debug.client.RoadDebugClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class YungsRoadsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!YungsRoadsCommon.DEBUG_MODE) {
            return;
        }

        KeyBindingHelper.registerKeyBinding(RoadDebugClient.OPEN_SCREEN_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> RoadDebugClient.onClientTick());
        // Fired while the camera's rotation is applied to the model-view matrix
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> RoadDebugClient.onRenderLevel(context.camera()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> RoadDebugClient.onDisconnect());
    }
}
