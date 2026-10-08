package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Client entry points for the road debug tools, called by each loader's event hooks. Only used in debug mode.
 * <p>
 * The tools read and change road data directly on the integrated server, so they only work in singleplayer.
 */
public final class RoadDebugClient {
    public static final KeyMapping OPEN_SCREEN_KEY = new KeyMapping("key.yungsroads.debugScreen",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.misc");

    /** View options, shared by the map and the world overlay. Not saved. */
    public static boolean showWorldOverlay = true;
    public static boolean showPreviousRoads = true;
    public static boolean showNodes = false;
    public static boolean showRegionBorders = true;
    public static TerrainTiles.Layer terrainLayer = TerrainTiles.Layer.COST;

    @Nullable
    private static TerrainTiles terrainTiles;

    private RoadDebugClient() {
    }

    public static void onClientTick() {
        while (OPEN_SCREEN_KEY.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (serverLevel() == null) {
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("yungsroads.screen.singleplayer_only"), true);
                }
            } else if (ClientMilestone.TEST_WORLD_WARNING_ACCEPTED.isReached()) {
                minecraft.setScreen(new RoadDebugScreen());
            } else {
                minecraft.setScreen(testWorldWarning(minecraft));
            }
        }
    }

    /**
     * Warns that the debug screen changes roads in chunks that already generated, so it's only for test worlds. Shown
     * until the player accepts it once, which opens the screen. Cancelling returns to the game.
     */
    private static Screen testWorldWarning(Minecraft minecraft) {
        return new ConfirmScreen(accepted -> {
            if (accepted) {
                ClientMilestone.TEST_WORLD_WARNING_ACCEPTED.reach();
                minecraft.setScreen(serverLevel() == null ? null : new RoadDebugScreen());
            } else {
                minecraft.setScreen(null);
            }
        },
                Component.translatable("yungsroads.screen.warning.title").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                Component.translatable("yungsroads.screen.warning.text"),
                Component.translatable("yungsroads.screen.warning.accept"),
                CommonComponents.GUI_CANCEL);
    }

    /** Releases the map's terrain tiles, which hold onto the level they were sampled from. */
    public static void onDisconnect() {
        if (terrainTiles != null) {
            terrainTiles.close();
            terrainTiles = null;
        }
    }

    /** Renders the world overlay. Must be called while the camera's rotation is applied to the model-view matrix. */
    public static void onRenderLevel(Camera camera) {
        ServerLevel serverLevel = serverLevel();
        if (showWorldOverlay && serverLevel != null) {
            RoadOverlayRenderer.render(serverLevel, camera);
        }
    }

    /**
     * The integrated server's copy of the level the player is in, or null if not in singleplayer.
     * Road data lives on the server, so the debug tools read it from here.
     */
    @Nullable
    public static ServerLevel serverLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null) {
            return null;
        }
        return server.getLevel(minecraft.level.dimension());
    }

    /**
     * Returns the terrain tiles for the given level and lattice step, replacing the cached tiles if either changed.
     */
    static TerrainTiles terrainTiles(ServerLevel level, int step) {
        if (terrainTiles == null || !terrainTiles.isFor(level, step)) {
            if (terrainTiles != null) {
                terrainTiles.close();
            }
            terrainTiles = new TerrainTiles(level, step);
        }
        return terrainTiles;
    }
}
