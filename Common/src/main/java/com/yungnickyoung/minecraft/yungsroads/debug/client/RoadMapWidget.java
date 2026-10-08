package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadNetwork;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A top-down map of the roads and terrain around the player. Drag to pan, scroll to zoom, and right-click to teleport.
 * <p>
 * The spot under the cursor, or a spot pinned by clicking, is inspected: the inspect page describes it, with the road
 * nearest it, which the map highlights. A line in the corner gives the hovered spot's position and height at a glance.
 */
public class RoadMapWidget extends AbstractWidget {
    private static final double MIN_BLOCKS_PER_PIXEL = 0.25;
    private static final double MAX_BLOCKS_PER_PIXEL = 64;

    /** Terrain isn't sampled when tiles would be smaller than this on screen, since there would be too many. */
    private static final double MIN_TILE_PIXELS = 24;

    /** How close an inspected spot must be to a node to inspect its road, in pixels. Also how close a click unpins. */
    private static final double HOVER_DISTANCE = 6;
    /** How far the mouse can move while pressed and still count as a click rather than a drag, in pixels. */
    private static final double CLICK_DISTANCE = 3;

    private static final int BACKGROUND_COLOR = 0xFF101418;
    private static final int REGION_BORDER_COLOR = 0xA0FFFFFF;
    private static final int PREVIOUS_ROAD_COLOR = 0xC0FF4040;
    private static final int NODE_COLOR = 0xFFFFFFFF;
    private static final int BRIDGE_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int ENDPOINT_COLOR = 0xFF40FF40;
    private static final int PLAYER_COLOR = 0xFFFF2020;
    private static final int LABEL_BACKGROUND_COLOR = 0xA0000000;
    private static final int NOTE_COLOR = 0xFFC0C0C0;
    private static final int INSPECTED_COLOR = 0xFFFFD030;
    /** The widest the no road network note gets before wrapping, in pixels. Fits most file paths. */
    private static final int NETWORK_TEXT_WIDTH = 340;

    /**
     * The road type whose costs color the terrain, so pending changes can be previewed before they're applied.
     *
     * @param typeName The road type's name, for labeling its costs.
     */
    public record Preview(Component typeName, RoadSettings settings) {
    }

    /**
     * A spot on the map being inspected, with the road nearest it if any is close.
     *
     * @param pinned Whether the spot was pinned by clicking, rather than being under the cursor.
     * @param node   The road's node nearest the spot, or null if no road is close.
     */
    public record Inspection(int x, int z, boolean pinned, @Nullable Road road, @Nullable Road.DebugNode node) {
    }

    private record Spot(int x, int z) {
    }

    /** The road type to preview, or null if there's none, as in a dimension without a road network. */
    private final Supplier<Preview> preview;
    /** Called when a spot is pinned, so it can be shown. */
    private final Runnable onPin;

    private double centerX, centerZ;
    private double blocksPerPixel = 4;
    private boolean followPlayer = true;
    private boolean dragging = false;
    /** Where the mouse was pressed, and whether it has since moved far enough to be a drag rather than a click. */
    private double pressX, pressY;
    private boolean moved = false;
    /** Whether hovering is ignored, such as while a widget drawn over the map is hovered. */
    private boolean hoverInfoHidden = false;

    @Nullable
    private Spot pinned;
    /** The spot under the cursor when the map was last drawn, or null if the cursor wasn't over the map. */
    @Nullable
    private Spot hovered;
    @Nullable
    private Inspection inspection;

    public RoadMapWidget(int x, int y, int width, int height, Supplier<Preview> preview, Runnable onPin) {
        super(x, y, width, height, Component.translatable("yungsroads.screen.title"));
        this.preview = preview;
        this.onPin = onPin;
    }

    /** The pinned spot, or else the spot under the cursor, as of when the map was last drawn. Null if neither. */
    @Nullable
    public Inspection inspection() {
        return this.inspection;
    }

    public void unpin() {
        this.pinned = null;
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
        this.hovered = isMouseOver(mouseX, mouseY) && !this.dragging && !this.hoverInfoHidden
                ? new Spot(Mth.floor(toWorldX(mouseX)), Mth.floor(toWorldZ(mouseY)))
                : null;
        this.inspection = inspect(this.pinned != null ? this.pinned : this.hovered, this.pinned != null, regions);
        Road inspectedRoad = this.inspection == null ? null : this.inspection.road();
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
                if (road == inspectedRoad) {
                    renderRoute(guiGraphics, road, INSPECTED_COLOR, 6f, false);
                }
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
        if (this.pinned != null) {
            renderPin(guiGraphics, toScreenX(this.pinned.x + 0.5), toScreenY(this.pinned.z + 0.5));
        }
        guiGraphics.flush();

        Font font = minecraft.font;
        Component scale = Component.translatable("yungsroads.map.scale", String.format("%.2f", this.blocksPerPixel));
        if (RoadDebugClient.terrainLayer != TerrainTiles.Layer.NONE && !terrainShown) {
            scale = Component.translatable("yungsroads.map.zoom_in", scale);
        }
        guiGraphics.drawString(font, scale, getX() + 4, getBottom() - 12, 0xFFFFFFFF);

        Optional<RoadNetwork> network = generatorOf(level).getRoadNetwork();
        renderNetworkLabel(guiGraphics, font, level, network.isPresent());
        if (network.isEmpty()) {
            renderNoNetworkNote(guiGraphics, font, level);
        }
        if (this.hovered != null) {
            renderReadout(guiGraphics, font, level, this.hovered);
        }
        guiGraphics.disableScissor();
    }

    /**
     * Gives the hovered spot's position and height in the bottom right corner, so the map tells something at a glance
     * while the inspect page is closed.
     */
    private void renderReadout(GuiGraphics guiGraphics, Font font, ServerLevel level, Spot spot) {
        Component readout = Component.translatable("yungsroads.map.coordinates", spot.x, spot.z);
        Double height = RoadDebugClient.terrainTiles(level, YungsRoadsCommon.CONFIG.advanced.nodeStepDistance).heightAt(spot.x, spot.z);
        if (height != null) {
            readout = height.isNaN()
                    ? Component.translatable("yungsroads.map.readout_ocean", readout)
                    : Component.translatable("yungsroads.map.readout", readout, String.format("%.0f", height));
        }
        int x = getRight() - 4 - font.width(readout);
        int y = getBottom() - 12;
        guiGraphics.fill(x - 2, y - 2, getRight() - 2, y + font.lineHeight + 1, LABEL_BACKGROUND_COLOR);
        guiGraphics.drawString(font, readout, x, y, 0xFFFFFFFF);
    }

    /** Marks the pinned spot with a ring, so the spot itself stays visible. */
    private static void renderPin(GuiGraphics guiGraphics, float x, float y) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        guiGraphics.fill(ix - 5, iy - 5, ix + 5, iy + 5, 0xFF000000);
        guiGraphics.fill(ix - 4, iy - 4, ix + 4, iy + 4, INSPECTED_COLOR);
        guiGraphics.fill(ix - 2, iy - 2, ix + 2, iy + 2, 0xFF000000);
    }

    /** The spot to inspect, with the road nearest it, or null if there's no spot. */
    @Nullable
    private Inspection inspect(@Nullable Spot spot, boolean pinned, List<StructureRegion> regions) {
        if (spot == null) {
            return null;
        }
        float spotX = toScreenX(spot.x + 0.5);
        float spotY = toScreenY(spot.z + 0.5);
        Road.DebugNode nearestNode = null;
        Road nearestRoad = null;
        double closestDistSq = HOVER_DISTANCE * HOVER_DISTANCE;
        for (StructureRegion region : regions) {
            for (Road road : region.getRoads()) {
                for (Road.DebugNode node : road.nodes) {
                    double dx = toScreenX(node.jitteredPos.getX()) - spotX;
                    double dy = toScreenY(node.jitteredPos.getZ()) - spotY;
                    double distSq = dx * dx + dy * dy;
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        nearestNode = node;
                        nearestRoad = road;
                    }
                }
            }
        }
        return new Inspection(spot.x, spot.z, pinned, nearestRoad, nearestNode);
    }

    /** Names the dimension's road network in the bottom left corner, above the scale. The inspect page details it. */
    private void renderNetworkLabel(GuiGraphics guiGraphics, Font font, ServerLevel level, boolean hasNetwork) {
        Component label = Component.translatable(hasNetwork ? "yungsroads.map.network" : "yungsroads.map.no_network",
                level.dimension().location().toString());
        int x = getX() + 4;
        int y = getBottom() - 26;
        guiGraphics.fill(x - 2, y - 2, x + font.width(label) + 2, y + font.lineHeight + 1, LABEL_BACKGROUND_COLOR);
        guiGraphics.drawString(font, label, x, y, 0xFFFFFFFF);
    }

    /** Explains, in the middle of the map, why there are no roads, and how to add them. */
    private void renderNoNetworkNote(GuiGraphics guiGraphics, Font font, ServerLevel level) {
        Component note = Component.translatable("yungsroads.map.no_network.text", networkFile(level));
        int maxWidth = Math.min(getWidth() - 16, NETWORK_TEXT_WIDTH);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : note.getString().split("\n")) {
            lines.addAll(font.split(Component.literal(paragraph), maxWidth));
        }
        int width = lines.stream().mapToInt(font::width).max().orElse(0);
        int height = lines.size() * (font.lineHeight + 1);
        int centerX = getX() + getWidth() / 2;
        int y = getY() + (getHeight() - height) / 2;
        guiGraphics.fill(centerX - width / 2 - 6, y - 6, centerX + width / 2 + 6, y + height + 5, LABEL_BACKGROUND_COLOR);
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawCenteredString(font, line, centerX, y, NOTE_COLOR);
            y += font.lineHeight + 1;
        }
    }

    /** The file a dimension's road network is read from, relative to a datapack's root. */
    static String networkFile(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        return "data/" + id.getNamespace() + "/yungsroads/road_network/" + id.getPath() + ".json";
    }

    /** A set's members, after the tag they come from if it's a tag. */
    static <T> String describe(HolderSet<T> set, Function<Holder<T>, String> name) {
        String members = set.size() == 0
                ? Component.translatable("yungsroads.map.network.none").getString()
                : set.stream().map(name).collect(Collectors.joining(", "));
        return set.unwrapKey().map(tag -> "#" + tag.location() + ": " + members).orElse(members);
    }

    static StructureRegionGenerator generatorOf(ServerLevel level) {
        return ((IStructureRegionCacheProvider) level).getStructureRegionCache().getStructureRegionGenerator();
    }

    /**
     * Draws the terrain tiles covering the map.
     *
     * @return Whether terrain is shown at the current zoom level.
     */
    private boolean renderTerrain(GuiGraphics guiGraphics, ServerLevel level) {
        Preview preview = this.preview.get();
        if (RoadDebugClient.terrainLayer == TerrainTiles.Layer.NONE || preview == null) {
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

        RoadSettings settings = preview.settings();
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

    /**
     * Loaded regions with roads that could be on the map. Doesn't generate any, but starts loading those already saved,
     * which are shown once they've loaded.
     */
    private List<StructureRegion> visibleRegions(ServerLevel level) {
        StructureRegionCache cache = ((IStructureRegionCacheProvider) level).getStructureRegionCache();
        List<StructureRegion> regions = new ArrayList<>();
        for (long regionKey : StructureRegionCache.regionKeysNearArea(
                Mth.floor(toWorldX(getX())), Mth.floor(toWorldZ(getY())), Mth.floor(toWorldX(getRight())), Mth.floor(toWorldZ(getBottom())))) {
            StructureRegion region = cache.getRegionIfLoaded(regionKey);
            if (region != null) {
                regions.add(region);
            } else {
                cache.loadSavedRegionAsync(regionKey);
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
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.moved = false;
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
            if (!this.moved) {
                pinOrUnpin(mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    /** Pins the clicked spot, or unpins it if the pin was clicked. */
    private void pinOrUnpin(double mouseX, double mouseY) {
        if (this.pinned != null) {
            double dx = toScreenX(this.pinned.x + 0.5) - mouseX;
            double dy = toScreenY(this.pinned.z + 0.5) - mouseY;
            if (dx * dx + dy * dy <= HOVER_DISTANCE * HOVER_DISTANCE) {
                this.pinned = null;
                return;
            }
        }
        this.pinned = new Spot(Mth.floor(toWorldX(mouseX)), Mth.floor(toWorldZ(mouseY)));
        this.onPin.run();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging) {
            return false;
        }
        if (!this.moved && Math.hypot(mouseX - this.pressX, mouseY - this.pressY) > CLICK_DISTANCE) {
            this.moved = true;
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

    /** Ignores the cursor, such as while a widget drawn over the map is hovered, so the map doesn't inspect under it. */
    public void setHoverInfoHidden(boolean hidden) {
        this.hoverInfoHidden = hidden;
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
