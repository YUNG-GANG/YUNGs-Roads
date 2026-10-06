package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.AdvancedSetting;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A top-down map of the roads and terrain around the player. Drag to pan, scroll to zoom, and right-click to teleport.
 */
public class RoadMapWidget extends AbstractWidget {
    private static final double MIN_BLOCKS_PER_PIXEL = 0.25;
    private static final double MAX_BLOCKS_PER_PIXEL = 64;

    /** Terrain isn't sampled when tiles would be smaller than this on screen, since there would be too many. */
    private static final double MIN_TILE_PIXELS = 24;

    /** How close the cursor must be to a node to show its info, in pixels. */
    private static final double HOVER_DISTANCE = 6;

    private static final int BACKGROUND_COLOR = 0xFF101418;
    private static final int REGION_BORDER_COLOR = 0xA0FFFFFF;
    private static final int PREVIOUS_ROAD_COLOR = 0xC0FF4040;
    private static final int NODE_COLOR = 0xFFFFFFFF;
    private static final int BRIDGE_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int ENDPOINT_COLOR = 0xFF40FF40;
    private static final int PLAYER_COLOR = 0xFFFF2020;

    /** The settings used to color terrain, so pending changes can be previewed before they're applied. */
    private final Supplier<ConfigModule.Advanced> previewSettings;

    private double centerX, centerZ;
    private double blocksPerPixel = 4;
    private boolean followPlayer = true;
    private boolean dragging = false;

    public RoadMapWidget(int x, int y, int width, int height, Supplier<ConfigModule.Advanced> previewSettings) {
        super(x, y, width, height, Component.literal("Road map"));
        this.previewSettings = previewSettings;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null || minecraft.player == null) {
            return;
        }
        if (this.followPlayer) {
            this.centerX = minecraft.player.getX();
            this.centerZ = minecraft.player.getZ();
        }

        guiGraphics.enableScissor(getX(), getY(), getRight(), getBottom());
        guiGraphics.fill(getX(), getY(), getRight(), getBottom(), BACKGROUND_COLOR);
        // Tiles are drawn immediately, so the buffered background must be drawn before them
        guiGraphics.flush();

        boolean terrainShown = renderTerrain(guiGraphics, level);
        List<StructureRegion> regions = visibleRegions(level);
        if (RoadDebugClient.showRegionBorders) {
            renderRegionBorders(guiGraphics);
        }
        if (RoadDebugClient.showPreviousRoads) {
            Set<StructureRegion> current = new HashSet<>(regions);
            for (StructureRegion region : RoadTuning.previousRegions(level)) {
                if (!current.contains(region)) {
                    region.getRoads().forEach(road -> renderRoute(guiGraphics, road, PREVIOUS_ROAD_COLOR, 1.5f, false));
                }
            }
        }
        for (StructureRegion region : regions) {
            for (Road road : region.getRoads()) {
                renderRoute(guiGraphics, road, RoadOverlayRenderer.roadColor(road), 2f, true);
                if (RoadDebugClient.showNodes) {
                    for (Road.DebugNode node : road.nodes) {
                        fillAround(guiGraphics, toScreenX(node.jitteredPos.getX()), toScreenY(node.jitteredPos.getZ()), 1, NODE_COLOR);
                    }
                }
            }
            for (long endpointChunk : region.getRoadEndpointChunks()) {
                ChunkPos chunkPos = new ChunkPos(endpointChunk);
                float x = toScreenX(chunkPos.getMinBlockX());
                float y = toScreenY(chunkPos.getMinBlockZ());
                fillAround(guiGraphics, x, y, 3, 0xFF000000);
                fillAround(guiGraphics, x, y, 2, ENDPOINT_COLOR);
            }
        }
        renderPlayer(guiGraphics, minecraft);
        guiGraphics.flush();

        Font font = minecraft.font;
        String scale = String.format("%.2f blocks/px", this.blocksPerPixel);
        if (RoadDebugClient.terrainLayer != TerrainTiles.Layer.NONE && !terrainShown) {
            scale += " - zoom in to see terrain";
        }
        guiGraphics.drawString(font, scale, getX() + 4, getBottom() - 12, 0xFFFFFFFF);
        guiGraphics.disableScissor();

        if (isMouseOver(mouseX, mouseY) && !this.dragging) {
            renderHoverInfo(guiGraphics, font, level, regions, mouseX, mouseY);
        }
    }

    /**
     * Draws the terrain tiles covering the map.
     *
     * @return Whether terrain is shown at the current zoom level.
     */
    private boolean renderTerrain(GuiGraphics guiGraphics, ServerLevel level) {
        if (RoadDebugClient.terrainLayer == TerrainTiles.Layer.NONE) {
            return false;
        }
        TerrainTiles tiles = RoadDebugClient.terrainTiles(level, YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
        int step = tiles.step();
        double tileBlocks = TerrainTiles.TILE_SIZE * step;
        if (tileBlocks / this.blocksPerPixel < MIN_TILE_PIXELS) {
            return false;
        }

        // Each pixel is centered on its lattice point, so tiles start half a step before their first point
        double halfStep = step / 2.0;
        int minTileX = Mth.floor((toWorldX(getX()) + halfStep) / tileBlocks);
        int maxTileX = Mth.floor((toWorldX(getRight()) + halfStep) / tileBlocks);
        int minTileZ = Mth.floor((toWorldZ(getY()) + halfStep) / tileBlocks);
        int maxTileZ = Mth.floor((toWorldZ(getBottom()) + halfStep) / tileBlocks);

        // Request tiles nearest the center first, since only a few are sampled at a time
        int centerTileX = Mth.floor((this.centerX + halfStep) / tileBlocks);
        int centerTileZ = Mth.floor((this.centerZ + halfStep) / tileBlocks);
        List<int[]> visible = new ArrayList<>();
        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                visible.add(new int[]{tileX, tileZ});
            }
        }
        visible.sort((a, b) -> Integer.compare(
                Math.max(Math.abs(a[0] - centerTileX), Math.abs(a[1] - centerTileZ)),
                Math.max(Math.abs(b[0] - centerTileX), Math.abs(b[1] - centerTileZ))));

        ConfigModule.Advanced settings = this.previewSettings.get();
        float pixelScale = (float) (step / this.blocksPerPixel);
        for (int[] tile : visible) {
            var texture = tiles.texture(tile[0], tile[1], RoadDebugClient.terrainLayer, settings);
            if (texture == null) {
                continue;
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(toScreenX(tile[0] * tileBlocks - halfStep), toScreenY(tile[1] * tileBlocks - halfStep), 0);
            guiGraphics.pose().scale(pixelScale, pixelScale, 1);
            guiGraphics.blit(texture, 0, 0, 0, 0, TerrainTiles.TILE_SIZE, TerrainTiles.TILE_SIZE, TerrainTiles.TILE_SIZE, TerrainTiles.TILE_SIZE);
            guiGraphics.pose().popPose();
        }
        return true;
    }

    private void renderRegionBorders(GuiGraphics guiGraphics) {
        int regionSize = 1 << StructureRegionPos.REGION_SIZE_SHIFT;
        int minRegionX = Mth.floor(toWorldX(getX()) / regionSize);
        int maxRegionX = Mth.floor(toWorldX(getRight()) / regionSize);
        int minRegionZ = Mth.floor(toWorldZ(getY()) / regionSize);
        int maxRegionZ = Mth.floor(toWorldZ(getBottom()) / regionSize);
        for (int regionX = minRegionX; regionX <= maxRegionX + 1; regionX++) {
            int x = Math.round(toScreenX((double) regionX * regionSize));
            guiGraphics.fill(x, getY(), x + 1, getBottom(), REGION_BORDER_COLOR);
        }
        for (int regionZ = minRegionZ; regionZ <= maxRegionZ + 1; regionZ++) {
            int y = Math.round(toScreenY((double) regionZ * regionSize));
            guiGraphics.fill(getX(), y, getRight(), y + 1, REGION_BORDER_COLOR);
        }
    }

    /**
     * Draws the road's center line through its jittered nodes.
     *
     * @param highlightBridges Whether to outline bridges, so they stand out whatever the road's color.
     */
    private void renderRoute(GuiGraphics guiGraphics, Road road, int color, float thickness, boolean highlightBridges) {
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = guiGraphics.pose().last().pose();
        for (int n = 0; n + 1 < road.nodes.size(); n++) {
            var from = road.nodes.get(n).jitteredPos;
            var to = road.nodes.get(n + 1).jitteredPos;
            float x1 = toScreenX(from.getX()), y1 = toScreenY(from.getZ());
            float x2 = toScreenX(to.getX()), y2 = toScreenY(to.getZ());
            if (highlightBridges && road.isBridgeSegment(n)) {
                segment(consumer, pose, x1, y1, x2, y2, thickness + 2, BRIDGE_OUTLINE_COLOR);
            }
            segment(consumer, pose, x1, y1, x2, y2, thickness, color);
        }
    }

    private void renderPlayer(GuiGraphics guiGraphics, Minecraft minecraft) {
        float x = toScreenX(minecraft.player.getX());
        float y = toScreenY(minecraft.player.getZ());
        float yaw = minecraft.player.getYRot() * Mth.DEG_TO_RAD;
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        segment(consumer, guiGraphics.pose().last().pose(), x, y, x - Mth.sin(yaw) * 10, y + Mth.cos(yaw) * 10, 1.5f, PLAYER_COLOR);
        fillAround(guiGraphics, x, y, 3, PLAYER_COLOR);
    }

    private void renderHoverInfo(GuiGraphics guiGraphics, Font font, ServerLevel level, List<StructureRegion> regions, int mouseX, int mouseY) {
        int x = Mth.floor(toWorldX(mouseX));
        int z = Mth.floor(toWorldZ(mouseY));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format("x: %d, z: %d", x, z)));

        TerrainTiles tiles = RoadDebugClient.terrainTiles(level, YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
        Double height = tiles.heightAt(x, z);
        Double grade = tiles.gradeAt(x, z);
        if (height != null && grade != null) {
            if (height.isNaN()) {
                lines.add(Component.literal("Ocean (impassable)"));
            } else {
                ConfigModule.Advanced settings = this.previewSettings.get();
                lines.add(Component.literal(String.format("Height: %.1f%s, steepest grade: %.2f", height, tiles.isWater(height) ? " (water)" : "", grade)));
                if (tiles.isWater(height)) {
                    lines.add(Component.literal(String.format("Only crossed by straight bridges, up to %s blocks long",
                            AdvancedSetting.MAX_BRIDGE_LENGTH.format(settings.maxBridgeLength))));
                    lines.add(Component.literal(String.format("Flat bridge cost: run × (1 + %s) = run × %s",
                            AdvancedSetting.WATER_WEIGHT.format(settings.waterWeight), AdvancedSetting.WATER_WEIGHT.format(1 + settings.waterWeight))));
                } else if (grade > settings.maxGrade) {
                    lines.add(Component.literal(String.format("Too steep to cross (max grade %s)",
                            AdvancedSetting.MAX_GRADE.format(settings.maxGrade))));
                } else {
                    lines.add(Component.literal(String.format("Steepest step cost: run × (1 + %s × max(0, %.2f² - %s²)) = run × %.1f",
                            AdvancedSetting.SLOPE_WEIGHT.format(settings.slopeWeight), grade,
                            AdvancedSetting.FREE_GRADE.format(settings.freeGrade), tiles.stepCost(grade, settings))));
                }
            }
        }

        Road.DebugNode hoveredNode = null;
        Road hoveredRoad = null;
        double closestDistSq = HOVER_DISTANCE * HOVER_DISTANCE;
        for (StructureRegion region : regions) {
            for (Road road : region.getRoads()) {
                for (Road.DebugNode node : road.nodes) {
                    double dx = toScreenX(node.jitteredPos.getX()) - mouseX;
                    double dy = toScreenY(node.jitteredPos.getZ()) - mouseY;
                    double distSq = dx * dx + dy * dy;
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        hoveredNode = node;
                        hoveredRoad = road;
                    }
                }
            }
        }
        if (hoveredNode != null) {
            lines.add(Component.literal(String.format("Node cost so far: %.0f, weighted distance left: %.0f, priority: %.0f",
                    hoveredNode.g, hoveredNode.h, hoveredNode.g + hoveredNode.h)));
            lines.add(Component.literal(String.format("Road %s to %s, %d nodes",
                    hoveredRoad.getStartPos().toShortString(), hoveredRoad.getEndPos().toShortString(), hoveredRoad.nodes.size())));
        }
        lines.add(Component.literal("Right-click to teleport").withStyle(style -> style.withColor(0xA0A0A0)));
        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Loaded regions with roads that could be on the map. Doesn't load or generate any. */
    private List<StructureRegion> visibleRegions(ServerLevel level) {
        StructureRegionCache cache = ((IStructureRegionCacheProvider) level).getStructureRegionCache();
        List<StructureRegion> regions = new ArrayList<>();
        for (long regionKey : StructureRegionCache.regionKeysNearArea(
                Mth.floor(toWorldX(getX())), Mth.floor(toWorldZ(getY())), Mth.floor(toWorldX(getRight())), Mth.floor(toWorldZ(getBottom())))) {
            StructureRegion region = cache.getRegionIfLoaded(regionKey);
            if (region != null) {
                regions.add(region);
            }
        }
        return regions;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (button == 0) {
            this.dragging = true;
            return true;
        }
        if (button == 1) {
            teleportTo(Mth.floor(toWorldX(mouseX)), Mth.floor(toWorldZ(mouseY)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging) {
            return false;
        }
        this.followPlayer = false;
        this.centerX -= dragX * this.blocksPerPixel;
        this.centerZ -= dragY * this.blocksPerPixel;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0) {
            return false;
        }
        // Zoom around the cursor, keeping the block under it in place
        double worldX = toWorldX(mouseX);
        double worldZ = toWorldZ(mouseY);
        this.blocksPerPixel = Mth.clamp(this.blocksPerPixel * Math.pow(1.25, -scrollY), MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
        if (!this.followPlayer) {
            this.centerX = worldX - (mouseX - centerScreenX()) * this.blocksPerPixel;
            this.centerZ = worldZ - (mouseY - centerScreenY()) * this.blocksPerPixel;
        }
        return true;
    }

    /** Centers the map on the player again, following them as they move. */
    public void recenter() {
        this.followPlayer = true;
    }

    private void teleportTo(int x, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null || minecraft.player == null) {
            return;
        }
        var playerId = minecraft.player.getUUID();
        level.getServer().execute(() -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                // Heights are only available for loaded chunks, so load the destination first
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                player.teleportTo(level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
            }
        });
        this.followPlayer = true;
    }

    private float centerScreenX() {
        return getX() + getWidth() / 2f;
    }

    private float centerScreenY() {
        return getY() + getHeight() / 2f;
    }

    private float toScreenX(double worldX) {
        return (float) (centerScreenX() + (worldX - this.centerX) / this.blocksPerPixel);
    }

    private float toScreenY(double worldZ) {
        return (float) (centerScreenY() + (worldZ - this.centerZ) / this.blocksPerPixel);
    }

    private double toWorldX(double screenX) {
        return this.centerX + (screenX - centerScreenX()) * this.blocksPerPixel;
    }

    private double toWorldZ(double screenY) {
        return this.centerZ + (screenY - centerScreenY()) * this.blocksPerPixel;
    }

    private static void fillAround(GuiGraphics guiGraphics, float x, float y, int radius, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        guiGraphics.fill(ix - radius, iy - radius, ix + radius, iy + radius, color);
    }

    /** Adds a line segment as a quad, since the GUI has no line primitive. */
    private static void segment(VertexConsumer consumer, Matrix4f pose, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length == 0) {
            return;
        }
        float nx = -dy / length * thickness / 2;
        float ny = dx / length * thickness / 2;
        // Wind the same way as GuiGraphics#fill, so the quad isn't culled
        consumer.addVertex(pose, x1 - nx, y1 - ny, 0).setColor(color);
        consumer.addVertex(pose, x1 + nx, y1 + ny, 0).setColor(color);
        consumer.addVertex(pose, x2 + nx, y2 + ny, 0).setColor(color);
        consumer.addVertex(pose, x2 - nx, y2 - ny, 0).setColor(color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
