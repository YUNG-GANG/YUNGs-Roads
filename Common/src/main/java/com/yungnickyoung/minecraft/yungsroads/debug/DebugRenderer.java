package com.yungnickyoung.minecraft.yungsroads.debug;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DebugRenderer {
    /** Singleton logic **/
    private static final DebugRenderer instance = new DebugRenderer();
    private DebugRenderer() {}
    public static DebugRenderer getInstance() {
        return instance;
    }

    public boolean enabled = true;
    public final Map<ChunkPos, Integer> villages = new HashMap<>();
    public final Map<ChunkPos, Integer> paths = new HashMap<>();
    public final Map<StructureRegionPos, Integer> structureRegions = new HashMap<>();
    private final Random random = new Random();

    public void renderMap(GuiGraphics guiGraphics) {
        if (!YungsRoadsCommon.CONFIG.debug.enableDebugMap || !enabled) {
            return;
        }

        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int xCenter = width - width / 2;
        int yCenter = height - height / 2;

        // Render background
//        fill(matrixStack, xCenter - 128, yCenter - 128, xCenter + 128, yCenter + 128, 0x90808080);

        BlockPos playerPos = Minecraft.getInstance().player == null
                ? null
                : Minecraft.getInstance().player.blockPosition();
        if (playerPos != null) {
            ChunkPos playerChunkPos = new ChunkPos(playerPos);
            // Render structure regions
            synchronized (structureRegions) {
                structureRegions.forEach(((structureRegionPos, color) -> {
                    ChunkPos relativeChunkStartPos = new ChunkPos(structureRegionPos.getX() * 256 - playerChunkPos.x, structureRegionPos.getZ() * 256 - playerChunkPos.z);
                    int renderXStart = xCenter + relativeChunkStartPos.x - 1;
                    int renderYStart = yCenter + relativeChunkStartPos.z - 1;
                    guiGraphics.fill(renderXStart, renderYStart, renderXStart + 256, renderYStart + 256, color);
                }));
            }
            // Render paths
            synchronized (paths) {
                for (ChunkPos pathPos : paths.keySet()) {
                    int color = paths.get(pathPos);
                    ChunkPos relativeChunkPos = new ChunkPos(pathPos.x - playerChunkPos.x, pathPos.z - playerChunkPos.z);
                    int renderX = xCenter + relativeChunkPos.x;
                    int renderY = yCenter + relativeChunkPos.z;
                    guiGraphics.fill(renderX - 1, renderY - 1, renderX, renderY, color);
                }
            }
            // Render villages
            synchronized (villages) {
                for (ChunkPos villagePos : villages.keySet()) {
                    int color = villages.get(villagePos);
                    ChunkPos relativeChunkPos = new ChunkPos(villagePos.x - playerChunkPos.x, villagePos.z - playerChunkPos.z);
                    int renderX = xCenter + relativeChunkPos.x;
                    int renderY = yCenter + relativeChunkPos.z;
                    guiGraphics.fill(renderX - 1, renderY - 1, renderX, renderY, color);
                }
            }
        }

        // Render player
        guiGraphics.fill(xCenter - 1, yCenter - 1, xCenter, yCenter, 0xB0FF0000);
    }

    public void addEndpointPos(ChunkPos pos) {
        synchronized (villages) {
            villages.putIfAbsent(pos, getRandomColor());
        }
    }

    public void addPath(ChunkPos pathPos, ChunkPos nearestVillagePos) {
        synchronized (paths) {
            paths.putIfAbsent(pathPos, villages.getOrDefault(nearestVillagePos, 0xFF000000));
        }
    }

    public void addStructureRegion(StructureRegionPos pos) {
        synchronized (structureRegions) {
            structureRegions.putIfAbsent(pos, (random.nextInt() | 0xFF000000) & 0x20FFFFFF);
        }
    }

    public void clearAll() {
        synchronized (villages) {
            villages.clear();
        }
        synchronized (paths) {
            paths.clear();
        }
        synchronized (structureRegions) {
            structureRegions.clear();
        }
    }

    private int getRandomColor() {
        return random.nextInt() | 0xFF000000;
    }
}
