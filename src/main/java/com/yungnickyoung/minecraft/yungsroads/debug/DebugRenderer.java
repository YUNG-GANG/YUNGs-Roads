package com.yungnickyoung.minecraft.yungsroads.debug;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DebugRenderer extends AbstractGui {
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

    public void render(Minecraft mc, MatrixStack matrixStack) {
        int width = mc.getMainWindow().getScaledWidth();
        int height = mc.getMainWindow().getScaledHeight();

        int xCenter = width - width / 2;
        int yCenter = height - height / 2;

        // Render background
        fill(matrixStack, xCenter - 128, yCenter - 128, xCenter + 128, yCenter + 128, 0x90808080);

        BlockPos playerPos = mc.player == null ? null : mc.player.getPosition();
        if (playerPos != null) {
            ChunkPos playerChunkPos = new ChunkPos(playerPos);
            // Render strucure regions
            synchronized (structureRegions) {
                structureRegions.forEach(((structureRegionPos, color) -> {
                    ChunkPos relativeChunkStartPos = new ChunkPos(structureRegionPos.getX() * 256 - playerChunkPos.x, structureRegionPos.getZ() * 256 - playerChunkPos.z);
                    int renderXStart = xCenter + relativeChunkStartPos.x - 1;
                    int renderYStart = yCenter + relativeChunkStartPos.z - 1;
                    fill(matrixStack, renderXStart, renderYStart, renderXStart + 256, renderYStart + 256, color);
                }));
            }
            // Render paths
            synchronized (paths) {
                for (ChunkPos pathPos : paths.keySet()) {
                    int color = paths.get(pathPos);
                    ChunkPos relativeChunkPos = new ChunkPos(pathPos.x - playerChunkPos.x, pathPos.z - playerChunkPos.z);
                    int renderX = xCenter + relativeChunkPos.x;
                    int renderY = yCenter + relativeChunkPos.z;
                    fill(matrixStack, renderX - 1, renderY - 1, renderX, renderY, color);
                }
            }
            // Render villages
            synchronized (villages) {
                for (ChunkPos villagePos : villages.keySet()) {
                    int color = villages.get(villagePos);
                    ChunkPos relativeChunkPos = new ChunkPos(villagePos.x - playerChunkPos.x, villagePos.z - playerChunkPos.z);
                    int renderX = xCenter + relativeChunkPos.x;
                    int renderY = yCenter + relativeChunkPos.z;
                    fill(matrixStack, renderX - 1, renderY - 1, renderX, renderY, color);
                }
            }
        }

        // Render player
        fill(matrixStack, xCenter - 1, yCenter - 1, xCenter, yCenter, 0xB0FF0000);
    }

    public void addVillage(ChunkPos pos) {
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

    private int getRandomColor() {
        return random.nextInt() | 0xFF000000;
    }
}
