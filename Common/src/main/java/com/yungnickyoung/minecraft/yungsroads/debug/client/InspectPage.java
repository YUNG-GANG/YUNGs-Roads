package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadNetwork;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.road.Road;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.StructureRegionGenerator;
import com.yungnickyoung.minecraft.yungsroads.world.terrain.TerrainCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The side drawer's inspect page, describing a spot on the map: the spot under the cursor, or one pinned by clicking
 * the map. Its details come in three groups, each marked by a colored bar and explained by a line under its name, since
 * they differ in where they come from:
 * <ul>
 *     <li>Terrain: the ground at the spot, whatever the settings.</li>
 *     <li>Your edits: what routing would charge to cross the spot with the road type being edited, including changes
 *     that haven't been applied.</li>
 *     <li>In this world: the road nearest the spot as it's generated now, with how its road type was chosen, and the
 *     dimension's road network.</li>
 * </ul>
 * It's narrower than the help page, so the map stays in view beside it.
 */
final class InspectPage implements DrawerPage {
    private static final int MIN_WIDTH = 170;
    private static final int MAX_WIDTH = 260;
    private static final int SECTION_GAP = 8;
    private static final int LINE_GAP = 1;
    private static final int UNPIN_PADDING = 4;
    private static final int UNPIN_HEIGHT = 12;
    private static final int TERRAIN_COLOR = 0xFF80D080;
    /** The color edited values are marked with on the settings panel, so the group reads as about the edits. */
    private static final int EDITS_COLOR = 0xFFFFD050;
    private static final int WORLD_COLOR = 0xFF8CC4FF;
    private static final int SUBTITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DETAIL_COLOR = 0xFFA0A0A0;
    private static final int PINNED_COLOR = 0xFFFFD030;
    private static final int LINK_COLOR = 0xFF2A2A2A;
    private static final int HOVERED_LINK_COLOR = 0xFF404040;
    private static final int EDGE_COLOR = 0xFF505050;

    private final Font font;
    private final ScrollingText text;
    private final RoadMapWidget map;
    /** The road type whose costs are shown, as on the map's terrain, or null if there's none. */
    private final Supplier<RoadMapWidget.Preview> preview;

    /**
     * The road type each road would be chosen now, with the votes that chose it, for explaining an inspected road's
     * type. Found when a road is first inspected, since it samples biomes along the road.
     */
    private final Map<Road, RoadTypes.Choice> roadTypeChoices = new WeakHashMap<>();

    /** The unpin button's bounds when last drawn, or a zero width if it wasn't drawn. */
    private int unpinX, unpinY, unpinWidth;

    InspectPage(Font font, RoadMapWidget map, Supplier<RoadMapWidget.Preview> preview) {
        this.font = font;
        this.text = new ScrollingText(font);
        this.map = map;
        this.preview = preview;
    }

    @Override
    public Component title() {
        return Component.translatable("yungsroads.inspect.title");
    }

    @Override
    public Component tabName() {
        return Component.translatable("yungsroads.screen.inspect.tab");
    }

    /** About two fifths of the area, so most of the map stays in view, leaving room for its tab in a narrow area. */
    @Override
    public int width(int areaWidth) {
        return Math.min(Mth.clamp(areaWidth * 2 / 5, MIN_WIDTH, MAX_WIDTH), areaWidth - DrawerTab.WIDTH);
    }

    @Override
    public void mouseScrolled(double scrollY) {
        this.text.mouseScrolled(scrollY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY) {
        if (this.unpinWidth > 0 && mouseX >= this.unpinX && mouseX < this.unpinX + this.unpinWidth
                && mouseY >= this.unpinY && mouseY < this.unpinY + UNPIN_HEIGHT) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.map.unpin();
            return;
        }
        this.text.mouseClicked(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseY) {
        return this.text.mouseDragged(mouseY);
    }

    @Override
    public void mouseReleased() {
        this.text.mouseReleased();
    }

    @Nullable
    @Override
    public Component render(GuiGraphics guiGraphics, int left, int right, int top, int bottom, int mouseX, int mouseY) {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null) {
            return null;
        }
        RoadMapWidget.Inspection inspection = this.map.inspection();

        // Which spot is shown, with a button to unpin it if it's pinned
        this.unpinWidth = 0;
        Component status;
        if (inspection == null) {
            status = Component.translatable("yungsroads.inspect.nothing").withColor(DETAIL_COLOR);
        } else if (inspection.pinned()) {
            status = Component.translatable("yungsroads.inspect.pinned").withColor(PINNED_COLOR);
            Component unpin = Component.translatable("yungsroads.inspect.unpin");
            this.unpinWidth = this.font.width(unpin) + UNPIN_PADDING * 2;
            this.unpinX = right - this.unpinWidth;
            this.unpinY = top - 1;
            boolean hovered = mouseX >= this.unpinX && mouseX < right && mouseY >= this.unpinY && mouseY < this.unpinY + UNPIN_HEIGHT;
            guiGraphics.fill(this.unpinX, this.unpinY, right, this.unpinY + UNPIN_HEIGHT, hovered ? HOVERED_LINK_COLOR : LINK_COLOR);
            guiGraphics.drawString(this.font, unpin, this.unpinX + UNPIN_PADDING, this.unpinY + 2, TEXT_COLOR, false);
        } else {
            status = Component.translatable("yungsroads.inspect.hovered").withColor(DETAIL_COLOR);
        }
        guiGraphics.drawString(this.font, status, left, top + 1, TEXT_COLOR, false);
        int contentTop = top + UNPIN_HEIGHT + 4;
        guiGraphics.fill(left, contentTop - 3, right, contentTop - 2, EDGE_COLOR);

        // Laid out every frame, since the spot changes as the cursor moves
        this.text.clear(right - left - 4 - ScrollingText.BAR_INDENT);
        if (inspection == null) {
            this.text.addParagraph(Component.translatable("yungsroads.inspect.how"), DETAIL_COLOR);
        } else {
            TerrainTiles tiles = RoadDebugClient.terrainTiles(level, YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
            Double height = tiles.heightAt(inspection.x(), inspection.z());
            Double grade = tiles.gradeAt(inspection.x(), inspection.z());
            addTerrain(inspection, tiles, height, grade);
            RoadMapWidget.Preview preview = this.preview.get();
            // The ocean is never crossed, and unsampled terrain has no costs to give
            if (preview != null && height != null && grade != null && !height.isNaN()) {
                addEdits(preview, tiles, height, grade);
            }
        }
        addWorld(level, inspection);
        this.text.addParagraph(Component.translatable("yungsroads.map.teleport"), DETAIL_COLOR);
        return this.text.render(guiGraphics, left + ScrollingText.BAR_INDENT, contentTop + 2, bottom, right + SideDrawer.PADDING - 4, mouseX, mouseY);
    }

    /** The ground at the spot, whatever the settings. */
    private void addTerrain(RoadMapWidget.Inspection inspection, TerrainTiles tiles, @Nullable Double height, @Nullable Double grade) {
        beginGroup("terrain", TERRAIN_COLOR);
        addLine(Component.translatable("yungsroads.map.coordinates", inspection.x(), inspection.z()));
        if (height == null || grade == null) {
            this.text.addParagraph(Component.translatable("yungsroads.inspect.not_sampled"), DETAIL_COLOR);
        } else if (height.isNaN()) {
            addLine(Component.translatable("yungsroads.map.ocean"));
        } else {
            addLine(Component.translatable(tiles.isWater(height) ? "yungsroads.map.height_water" : "yungsroads.map.height",
                    String.format("%.1f", height), String.format("%.2f", grade)));
        }
        endGroup();
    }

    /**
     * What routing would charge to cross the spot with the road type being edited, as entered, so it follows the
     * settings panel before they're applied.
     */
    private void addEdits(RoadMapWidget.Preview preview, TerrainTiles tiles, double height, double grade) {
        RoadSettings settings = preview.settings();
        beginGroup("edits", EDITS_COLOR);
        addLine(Component.translatable("yungsroads.map.costs_for", preview.typeName()));
        if (tiles.isWater(height)) {
            addLine(Component.translatable("yungsroads.map.bridge_only", RoadSetting.MAX_BRIDGE_LENGTH.format(settings.maxBridgeLength)));
            addLine(Component.translatable("yungsroads.map.bridge_cost",
                    RoadSetting.WATER_WEIGHT.format(settings.waterWeight), RoadSetting.WATER_WEIGHT.format(1 + settings.waterWeight)));
        } else if (grade > settings.maxGrade) {
            addLine(Component.translatable("yungsroads.map.too_steep", RoadSetting.MAX_GRADE.format(settings.maxGrade)));
        } else {
            addLine(Component.translatable("yungsroads.map.step_cost",
                    RoadSetting.SLOPE_WEIGHT.format(settings.slopeWeight), String.format("%.2f", grade),
                    RoadSetting.FREE_GRADE.format(settings.freeGrade), String.format("%.1f", tiles.stepCost(grade, settings))));
        }
        endGroup();
    }

    /** The road nearest the spot as it's generated now, if any, and the dimension's road network. */
    private void addWorld(ServerLevel level, @Nullable RoadMapWidget.Inspection inspection) {
        beginGroup("world", WORLD_COLOR);
        if (inspection != null) {
            if (inspection.road() != null) {
                addRoad(level, inspection.road(), inspection.node());
            } else {
                addSubtitle(Component.translatable("yungsroads.inspect.road"));
                this.text.addParagraph(Component.translatable("yungsroads.inspect.no_road"), DETAIL_COLOR);
            }
            this.text.addGap(SECTION_GAP / 2);
        }
        addNetwork(level);
        endGroup();
    }

    /**
     * The road nearest the spot: its ends, its road type and variant, the biome votes that choose its type as counted
     * with the road types in use now, and routing's costs at its node nearest the spot.
     */
    private void addRoad(ServerLevel level, Road road, Road.DebugNode node) {
        addSubtitle(Component.translatable("yungsroads.inspect.road"));
        addLine(Component.translatable("yungsroads.map.road", horizontal(road.getStartPos()), horizontal(road.getEndPos()), road.nodes.size()));
        RoadType type = RoadTuning.roadTypesOf(level).current().get(road.roadType);
        int variantCount = type == null ? 1 : type.variants().size();
        addLine(Component.translatable("yungsroads.map.road_type", RoadTypeNames.name(road.roadType, road.variant, variantCount)));

        RoadTypes.Choice choice = this.roadTypeChoices.computeIfAbsent(road, r -> {
            StructureRegionGenerator generator = RoadMapWidget.generatorOf(level);
            TerrainCache terrain = new TerrainCache(generator.getTerrainSampler(), YungsRoadsCommon.CONFIG.advanced.nodeStepDistance);
            return generator.chooseRoadType(r.getStartPos(), r.getEndPos(), terrain);
        });
        addLine(Component.translatable("yungsroads.inspect.votes"));
        choice.votes().stream()
                .sorted(Comparator.comparingInt(RoadTypes.Vote::votes).reversed())
                .forEach(vote -> addLine(Component.translatable("yungsroads.inspect.vote", RoadTypeNames.name(vote.typeId()), vote.votes())));
        addLine(Component.translatable("yungsroads.map.node", String.format("%.0f", node.g), String.format("%.0f", node.h),
                String.format("%.0f", node.g + node.h)));
    }

    /** The dimension's road network as the level loaded it, since networks only load with the world. */
    private void addNetwork(ServerLevel level) {
        addSubtitle(Component.translatable("yungsroads.inspect.network"));
        Optional<RoadNetwork> network = RoadMapWidget.generatorOf(level).getRoadNetwork();
        addLine(Component.translatable(network.isPresent() ? "yungsroads.inspect.dimension" : "yungsroads.map.no_network",
                level.dimension().location().toString()));
        addLine(Component.translatable("yungsroads.map.network.file", RoadMapWidget.networkFile(level)));
        network.ifPresent(roadNetwork -> {
            addLine(Component.translatable("yungsroads.map.network.structures", RoadMapWidget.describe(roadNetwork.structures(),
                    holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse("?"))));
            addLine(Component.translatable("yungsroads.map.network.road_types", RoadMapWidget.describe(roadNetwork.roadTypes(),
                    holder -> holder.unwrapKey().map(key -> RoadTypeNames.name(key.location())).orElse("?"))));
            addLine(Component.translatable("yungsroads.map.network.default",
                    roadNetwork.defaultRoadType().unwrapKey().map(key -> RoadTypeNames.name(key.location())).orElse("?")));
        });
        this.text.addParagraph(Component.translatable("yungsroads.map.network.reload"), DETAIL_COLOR);
    }

    /** A road end's x and z, as on the map. */
    private static String horizontal(BlockPos pos) {
        return pos.getX() + ", " + pos.getZ();
    }

    /** Starts a group of details, with its name and a line explaining where its details come from. */
    private void beginGroup(String id, int color) {
        this.text.beginBar(color);
        this.text.addParagraph(Component.translatable("yungsroads.inspect.group." + id).withStyle(ChatFormatting.BOLD), color, 1);
        this.text.addParagraph(Component.translatable("yungsroads.inspect.group." + id + ".description"), DETAIL_COLOR);
    }

    private void endGroup() {
        this.text.endBar();
        this.text.addGap(SECTION_GAP);
    }

    /** A heading within a group. */
    private void addSubtitle(Component subtitle) {
        this.text.addParagraph(subtitle.copy().withStyle(ChatFormatting.BOLD), SUBTITLE_COLOR, 1);
    }

    /** A line of details, with its glossary words and setting names highlighted. Lines sit closer than paragraphs. */
    private void addLine(Component line) {
        this.text.addParagraph(RoutingGlossary.highlight(line.getString()), TEXT_COLOR, LINE_GAP);
    }
}
