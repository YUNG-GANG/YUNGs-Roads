package com.yungnickyoung.minecraft.yungsroads.debug;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.yungnickyoung.minecraft.yungsroads.YungsRoads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class DebugRenderer extends AbstractGui {
    private static final DebugRenderer instance = new DebugRenderer();
    private DebugRenderer() {}

    public boolean enabled = true;
    public final Map<ChunkPos, Integer> villages = new HashMap<>();
    public final Map<ChunkPos, Integer> paths = new HashMap<>();
    private final Random random = new Random();

    public static DebugRenderer getInstance() {
        return instance;
    }

    public void render(Minecraft mc, MatrixStack matrixStack) {
        int width = mc.getMainWindow().getScaledWidth();
        int height = mc.getMainWindow().getScaledHeight();

        int xCenter = width - width / 2;
        int yCenter = height - height / 2;

        fill(matrixStack, xCenter - 128, yCenter - 128, xCenter + 128, yCenter + 128, 0x90808080);

        BlockPos playerPos = mc.player == null ? null : mc.player.getPosition();
        if (playerPos != null) {
            ChunkPos playerChunkPos = new ChunkPos(playerPos);
            synchronized (paths) {
                for (ChunkPos pathPos : paths.keySet()) {
                    int color = paths.get(pathPos);
                    ChunkPos relativeChunkPos = new ChunkPos(pathPos.x - playerChunkPos.x, pathPos.z - playerChunkPos.z);
                    int renderX = xCenter + relativeChunkPos.x;
                    int renderY = yCenter + relativeChunkPos.z;
                    fill(matrixStack, renderX - 1, renderY - 1, renderX, renderY, color);
                }
            }
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

        fill(matrixStack, xCenter - 1, yCenter - 1, xCenter, yCenter, 0xB0FF0000);
    }

    public void addVillage(ChunkPos pos) {
        synchronized (villages) {
//            if (villages.stream().noneMatch(e -> e.pos == pos))
//                villages.add(new Entry(pos));
            villages.putIfAbsent(pos, getRandomColor());
        }
    }

    public void addPath(ChunkPos pathPos, ChunkPos nearestVillagePos) {
        synchronized (paths) {
//            paths.add(new Entry(pathPos, getColorForVillage(nearestVillagePos)));
            paths.putIfAbsent(pathPos, villages.getOrDefault(nearestVillagePos, 0xFF000000));
        }
    }

    private int getRandomColor() {
        return random.nextInt() | 0xFF000000;
    }

//    public int getColorForVillage(ChunkPos pos) {
//        Optional<Entry> entry = villages.stream().filter(e -> e.pos == pos).findFirst();
//        if (entry.isPresent()) return entry.get().color;
//        YungsRoads.LOGGER.error("No debug entry found for pos {}!", pos);
//        return 0xB0000000;
//    }

    private static class Entry {
        public ChunkPos pos;
        public int color;

        public Entry(ChunkPos pos, int color) {
            this.pos = pos;
            this.color = color;
        }

        public Entry(ChunkPos pos) {
            Random r = new Random();
            int i = r.nextInt();
            int color = (i & 0x00FFFFFF) | 0xFF000000;
            this.pos = pos;
            this.color = color;
        }
    }
}
