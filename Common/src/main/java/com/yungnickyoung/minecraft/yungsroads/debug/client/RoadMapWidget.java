package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadNetwork;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegion;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionCache;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionPos;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private static final int LABEL_BACKGROUND_COLOR = 0xA0000000;
    private static final int NOTE_COLOR = 0xFFC0C0C0;
    /** The widest the road network's tooltip and note get before wrapping, in pixels. Fits most file paths. */
    private static final int NETWORK_TEXT_WIDTH = 340;

    /**
     * The road type whose costs color the terrain, so pending changes can be previewed before they're applied.
     *
     * @param typeName The road type's name, for labeling its costs.
     */
    public record Preview(Component typeName, RoadSettings settings) {
    }

    /** The road type to preview, or null if there's none, as in a dimension without a road network. */
    private final Supplier<Preview> preview;

    /** The right and top edges of the road network label in the bottom left corner, as last drawn. */
    private int networkLabelRight, networkLabelTop;

    /**
     * The road type each road would be chosen now, with the votes that chose it, for explaining a hovered road's type.
     * Found when a road is first hovered.
     */
    private final Map<Road, RoadTypes.Choice> roadTypeChoices = new WeakHashMap<>();

    private double centerX, centerZ;
    private double blocksPerPixel = 4;
    private boolean followPlayer = true;
    private boolean dragging = false;
    /** Whether the hover info is hidden, such as while a widget drawn over the map is hovered. */
    private boolean hoverInfoHidden = false;

    public RoadMapWidget(int x, int y, int width, int height, Supplier<Preview> preview) {
        super(x, y, width, height, Component.translatable("yungsroads.screen.title"));
        this.preview = preview;
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
        guiGraphics.disableScissor();

        if (isMouseOver(mouseX, mouseY) && !this.dragging && !this.hoverInfoHidden) {
            if (mouseX < this.networkLabelRight && mouseY >= this.networkLabelTop) {
                renderNetworkInfo(guiGraphics, font, level, network.orElse(null), mouseX, mouseY);
            } else {
                renderHoverInfo(guiGraphics, font, level, regions, mouseX, mouseY);
            }
        }
    }

    /**
     * Names the dimension's road network in the bottom left corner, above the scale, clear of the help button in the
     * top right. Hovering it shows the network's details.
     */
    private void renderNetworkLabel(GuiGraphics guiGraphics, Font font, ServerLevel level, boolean hasNetwork) {
        Component label = Component.translatable(hasNetwork ? "yungsroads.map.network" : "yungsroads.map.no_network",
                level.dimension().location().toString());
        int x = getX() + 4;
        int y = getBottom() - 26;
        this.networkLabelRight = x + font.width(label) + 2;
        this.networkLabelTop = y - 2;
        guiGraphics.fill(x - 2, this.networkLabelTop, this.networkLabelRight, y + font.lineHeight + 1, LABEL_BACKGROUND_COLOR);
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

    /**
     * Describes the dimension's road network: the file it's from, the structures its roads connect, and the road types
     * they can get. Read from the network as the level loaded it, since networks only load with the world.
     */
    private void renderNetworkInfo(GuiGraphics guiGraphics, Font font, ServerLevel level, @Nullable RoadNetwork network, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("yungsroads.map.network.title", level.dimension().location().toString()));
        lines.add(Component.translatable("yungsroads.map.network.file", networkFile(level)));
        if (network != null) {
            lines.add(Component.translatable("yungsroads.map.network.structures",
                    describe(network.structures(), holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse("?"))));
            lines.add(Component.translatable("yungsroads.map.network.road_types",
                    describe(network.roadTypes(), holder -> holder.unwrapKey().map(key -> RoadTypeNames.name(key.location())).orElse("?"))));
            lines.add(Component.translatable("yungsroads.map.network.default",
                    network.defaultRoadType().unwrapKey().map(key -> RoadTypeNames.name(key.location())).orElse("?")));
        }
        lines.add(Component.translatable("yungsroads.map.network.reload").withStyle(style -> style.withColor(0xA0A0A0)));

        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, NETWORK_TEXT_WIDTH));
        }
        guiGraphics.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    /** The file a dimension's road network is read from, relative to a datapack's root. */
    private static String networkFile(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        return "data/" + id.getNamespace() + "/yungsroads/road_network/" + id.getPath() + ".json";
    }

    /** A set's members, after the tag they come from if it's a tag. */
    private static <T> String describe(HolderSet<T> set, Function<Holder<T>, String> name) {
        String members = set.size() == 0
                ? Component.translatable("yungsroads.map.network.none").getString()
                : set.stream().map(name).collect(Collectors.joining(", "));
        return set.unwrapKey().map(tag -> "#" + tag.location() + ": " + members).orElse(members);
    }

    private static StructureRegionGenerator generatorOf(ServerLevel level) {
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

    private void renderHoverInfo(GuiGraphics guiGraphics, Font font, ServerLevel level, List<StructureRegion> regions, int mouseX, int mouseY) {
        int x = Mth.floor(toWorldX(mouseX));
        int z = Mth.floor(toWorldZ(mouseY));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("yungsroads.map.coordinates", x, z));

        TerrainTiles tiles = RoadDebugClient.terrainTiles(level, YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
        Double height = tiles.heightAt(x, z);
        Double grade = tiles.gradeAt(x, z);
        Preview preview = this.preview.get();
        if (height != null && grade != null && preview != null) {
            if (height.isNaN()) {
                lines.add(Component.translatable("yungsroads.map.ocean"));
            } else {
                RoadSettings settings = preview.settings();
                lines.add(Component.translatable(tiles.isWater(height) ? "yungsroads.map.height_water" : "yungsroads.map.height",
                        String.format("%.1f", height), String.format("%.2f", grade)));
                lines.add(Component.translatable("yungsroads.map.costs_for", preview.typeName()).withStyle(style -> style.withColor(0xA0A0A0)));
                if (tiles.isWater(height)) {
                    lines.add(Component.translatable("yungsroads.map.bridge_only",
                            RoadSetting.MAX_BRIDGE_LENGTH.format(settings.maxBridgeLength)));
                    lines.add(Component.translatable("yungsroads.map.bridge_cost",
                            RoadSetting.WATER_WEIGHT.format(settings.waterWeight), RoadSetting.WATER_WEIGHT.format(1 + settings.waterWeight)));
                } else if (grade > settings.maxGrade) {
                    lines.add(Component.translatable("yungsroads.map.too_steep", RoadSetting.MAX_GRADE.format(settings.maxGrade)));
                } else {
                    lines.add(Component.translatable("yungsroads.map.step_cost",
                            RoadSetting.SLOPE_WEIGHT.format(settings.slopeWeight), String.format("%.2f", grade),
                            RoadSetting.FREE_GRADE.format(settings.freeGrade), String.format("%.1f", tiles.stepCost(grade, settings))));
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
            lines.add(Component.translatable("yungsroads.map.node", String.format("%.0f", hoveredNode.g),
                    String.format("%.0f", hoveredNode.h), String.format("%.0f", hoveredNode.g + hoveredNode.h)));
            lines.add(Component.translatable("yungsroads.map.road",
                    hoveredRoad.getStartPos().toShortString(), hoveredRoad.getEndPos().toShortString(), hoveredRoad.nodes.size()));
            addRoadTypeLines(lines, level, hoveredRoad);
        }
        lines.add(Component.translatable("yungsroads.map.teleport").withStyle(style -> style.withColor(0xA0A0A0)));
        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * Describes the road's type and variant, and the biome votes that choose its type, as counted with the road types
     * in use now.
     */
    private void addRoadTypeLines(List<Component> lines, ServerLevel level, Road road) {
        RoadTypes roadTypes = RoadTuning.roadTypesOf(level);
        RoadType type = roadTypes.current().get(road.roadType);
        int variantCount = type == null ? 1 : type.variants().size();
        lines.add(Component.translatable("yungsroads.map.road_type", RoadTypeNames.name(road.roadType, road.variant, variantCount)));

        RoadTypes.Choice choice = this.roadTypeChoices.computeIfAbsent(road, r -> {
            StructureRegionGenerator generator = generatorOf(level);
            TerrainCache terrain = new TerrainCache(generator.getTerrainSampler(), YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
            return generator.chooseRoadType(r.getStartPos(), r.getEndPos(), terrain);
        });
        String votes = choice.votes().stream()
                .map(vote -> RoadTypeNames.name(vote.typeId()) + " " + vote.votes())
                .collect(Collectors.joining(", "));
        lines.add(Component.translatable("yungsroads.map.votes", votes));
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

    /** Hides the hover info, such as while a widget drawn over the map is hovered, so their tooltips don't overlap. */
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
