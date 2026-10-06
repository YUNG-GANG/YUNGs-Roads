package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.AdvancedSetting;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.world.road.placement.LiveRoadPlacer;
import com.yungnickyoung.minecraft.yungsroads.world.structureregion.IStructureRegionCacheProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Vector2i;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Screen for tuning road generation in a running world. Settings are edited in a side panel and applied to the roads
 * around the player, and a map shows the result next to the terrain that routing sees.
 */
public class RoadDebugScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int MARGIN = 6;
    /** The top of each tab's content, below the tab buttons. */
    private static final int CONTENT_Y = MARGIN + 22;
    private static final int ROW_HEIGHT = 18;
    private static final int CHECKBOX_ROW_HEIGHT = 20;
    private static final int VALUE_BOX_WIDTH = 40;
    private static final int PANEL_COLOR = 0xE0101010;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5050;
    private static final int VALID_TEXT_COLOR = 0xFFE0E0E0;

    private enum Tab {
        ROUTING("Routing"),
        SHAPING("Shaping"),
        PLACEMENT("Placement"),
        VIEW("View");

        final String displayName;

        Tab(String displayName) {
            this.displayName = displayName;
        }

        /** Whether the tab's content scrolls, since its settings and formulas can be taller than the panel. */
        boolean scrolls() {
            return this == ROUTING || this == SHAPING;
        }

        /** The tab a setting is edited on. */
        static Tab of(AdvancedSetting setting) {
            return switch (setting.group) {
                case ROUTING -> ROUTING;
                case SHAPING -> SHAPING;
            };
        }
    }

    private Tab tab = Tab.ROUTING;
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);

    /**
     * Values as edited on the settings tabs. Kept outside the widgets so they survive the widgets being rebuilt, such
     * as when the window is resized. Not applied until the Apply button is pressed.
     */
    private final Map<AdvancedSetting, String> pendingText = new EnumMap<>(AdvancedSetting.class);
    /** Each setting's slider and text box, for showing its tooltip when either is hovered. */
    private final Map<AdvancedSetting, List<AbstractWidget>> settingRows = new EnumMap<>(AdvancedSetting.class);
    private ConfigModule.Debug pendingDebug;

    /** Where each settings tab's formulas start, below its settings. */
    private final Map<Tab, Integer> formulaY = new EnumMap<>(Tab.class);
    /** How far each scrolling tab is scrolled down, in pixels. Kept across rebuilds. */
    private final Map<Tab, Integer> scroll = new EnumMap<>(Tab.class);
    /** The furthest each scrolling tab could scroll when it was last rendered. */
    private final Map<Tab, Integer> maxScroll = new EnumMap<>(Tab.class);
    /** The y of each widget on a scrolling tab when the tab isn't scrolled. */
    private final Map<AbstractWidget, Integer> unscrolledY = new HashMap<>();
    private Button applyButton;
    private Button revertButton;
    /** Kept across rebuilds, so the map's position and zoom aren't reset. */
    private final RoadMapWidget map = new RoadMapWidget(0, 0, 0, 0, this::previewSettings);

    public RoadDebugScreen() {
        super(Component.literal("Road Debug"));
        loadPendingFromConfig();
    }

    @Override
    protected void init() {
        this.tabWidgets.clear();
        this.settingRows.clear();
        this.unscrolledY.clear();
        int x = MARGIN;
        int contentWidth = PANEL_WIDTH - MARGIN * 2;

        // Tabs, each fitting its label, with the remaining width shared between them
        int labelsWidth = 0;
        for (Tab t : Tab.values()) {
            labelsWidth += this.font.width(t.displayName);
        }
        int spareWidth = (contentWidth - labelsWidth) / Tab.values().length;
        int tabX = x;
        for (Tab t : Tab.values()) {
            // The last tab takes any width left over from rounding
            int tabWidth = t.ordinal() == Tab.values().length - 1 ? x + contentWidth - tabX : this.font.width(t.displayName) + spareWidth;
            addRenderableWidget(Button.builder(Component.literal(t.displayName), button -> selectTab(t))
                    .bounds(tabX, MARGIN, tabWidth - 2, 16)
                    .build());
            tabX += tabWidth;
        }
        int contentY = CONTENT_Y;

        // Routing and shaping settings, each a slider for quick changes with a text box for exact values, or a checkbox
        // for a toggle. Routing changes are previewed on the map's terrain right away, but only applied to roads on Apply.
        Map<Tab, Integer> rowY = new EnumMap<>(Tab.class);
        int sliderWidth = contentWidth - VALUE_BOX_WIDTH - 4;
        for (AdvancedSetting setting : AdvancedSetting.values()) {
            Tab settingTab = Tab.of(setting);
            int y = rowY.getOrDefault(settingTab, contentY);
            rowY.put(settingTab, y + ROW_HEIGHT);
            this.formulaY.put(settingTab, y + ROW_HEIGHT + 6);
            if (setting.isToggle) {
                Double value = parse(setting, this.pendingText.get(setting));
                Checkbox checkbox = Checkbox.builder(Component.literal(setting.displayName), this.font)
                        .pos(x, y)
                        .maxWidth(contentWidth)
                        .selected(value != null && value != 0)
                        .onValueChange((box, selected) -> this.pendingText.put(setting, selected ? "1" : "0"))
                        .build();
                this.settingRows.put(setting, List.of(checkbox));
                addScrollingWidget(settingTab, checkbox, y);
                continue;
            }
            EditBox box = new EditBox(this.font, x + sliderWidth + 4, y + 1, VALUE_BOX_WIDTH, 14, Component.literal(setting.displayName));
            SettingSlider slider = new SettingSlider(x, y, sliderWidth, 16, setting, box);
            box.setMaxLength(12);
            box.setResponder(value -> {
                this.pendingText.put(setting, value);
                Double parsed = parse(setting, value);
                box.setTextColor(parsed == null ? INVALID_TEXT_COLOR : VALID_TEXT_COLOR);
                if (parsed != null) {
                    slider.show(parsed);
                }
            });
            box.setValue(this.pendingText.get(setting));
            this.settingRows.put(setting, List.of(slider, box));
            addScrollingWidget(settingTab, slider, y);
            addScrollingWidget(settingTab, box, y + 1);
        }

        // Placement options
        int y = contentY;
        y = addDebugCheckbox(x, y, contentWidth, "Place roads", "Place road blocks. Turn off to see routes on the overlay without changing terrain.",
                debug -> debug.placeRoads, (debug, value) -> debug.placeRoads = value);
        y = addDebugCheckbox(x, y, contentWidth, "Debug paths", "Place single-block diamond paths along each road's center line instead of normal roads.",
                debug -> debug.placeDebugPaths, (debug, value) -> debug.placeDebugPaths = value);
        y = addDebugCheckbox(x, y, contentWidth, "Raw node markers", "Place purple wool towers at each node's position before jitter.",
                debug -> debug.placeUnjitteredPosDebugMarkers, (debug, value) -> debug.placeUnjitteredPosDebugMarkers = value);
        y = addDebugCheckbox(x, y, contentWidth, "Jittered markers", "Place redstone block towers at each node's final position.",
                debug -> debug.placeJitteredPosDebugMarkers, (debug, value) -> debug.placeJitteredPosDebugMarkers = value);
        y = addDebugCheckbox(x, y, contentWidth, "Endpoint markers", "Place emerald block towers at each road's endpoints.",
                debug -> debug.placeRoadEndpointDebugMarkers, (debug, value) -> debug.placeRoadEndpointDebugMarkers = value);
        addDebugCheckbox(x, y, contentWidth, "Straight lines", "Place gold block lines straight between each road's endpoints.",
                debug -> debug.placeStraightDebugLine, (debug, value) -> debug.placeStraightDebugLine = value);

        // View options. These only affect what's drawn, so they take effect immediately.
        y = contentY;
        y = addViewCheckbox(x, y, contentWidth, "World overlay", "Draw routes as lines in the world. Hidden parts are drawn faintly through terrain.",
                RoadDebugClient.showWorldOverlay, value -> RoadDebugClient.showWorldOverlay = value);
        y = addViewCheckbox(x, y, contentWidth, "Previous roads", "Draw the roads replaced by the last change in red, on the map and overlay.",
                RoadDebugClient.showPreviousRoads, value -> RoadDebugClient.showPreviousRoads = value);
        y = addViewCheckbox(x, y, contentWidth, "Nodes", "Mark each pathfinding node, on the map and overlay.",
                RoadDebugClient.showNodes, value -> RoadDebugClient.showNodes = value);
        y = addViewCheckbox(x, y, contentWidth, "Region borders", "Draw structure region borders on the map.",
                RoadDebugClient.showRegionBorders, value -> RoadDebugClient.showRegionBorders = value);
        y = addViewCheckbox(x, y, contentWidth, "Extra F3 info", "Show the structure region and road node at your position on the F3 screen.",
                YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info, value -> {
                    YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info = value;
                    this.pendingDebug.enableExtraDebugF3Info = value;
                });
        CycleButton<TerrainTiles.Layer> terrainButton = CycleButton.<TerrainTiles.Layer>builder(layer -> Component.literal(layer.displayName))
                .withValues(TerrainTiles.Layer.values())
                .withInitialValue(RoadDebugClient.terrainLayer)
                .withTooltip(layer -> Tooltip.create(Component.literal(
                        "Terrain shown on the map, one pixel per pathfinding node.\n"
                                + "Height: surface height, shaded.\n"
                                + "Slope: grade relative to Max Grade.\n"
                                + "Cost: routing's cost multiplier, log scale up to 50x.\n"
                                + "Purple can't be crossed. Slope and Cost preview unapplied settings.")))
                .create(x, y, contentWidth, 16, Component.literal("Terrain"), (button, layer) -> RoadDebugClient.terrainLayer = layer);
        addTabWidget(Tab.VIEW, terrainButton);
        y += ROW_HEIGHT;
        addTabWidget(Tab.VIEW, Button.builder(Component.literal("Center map on player"), button -> this.map.recenter())
                .bounds(x, y, contentWidth, 16)
                .build());

        // Actions
        int buttonWidth = (contentWidth - 4) / 2;
        int actionsY = this.height - MARGIN - 36;
        this.applyButton = addRenderableWidget(Button.builder(Component.literal("Apply"), button -> apply())
                .bounds(x, actionsY, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.literal("Regenerate roads within " + RoadTuning.REGENERATE_RADIUS
                        + " blocks with these settings, then place them in loaded chunks. Only re-places roads if only placement options changed.")))
                .build());
        this.revertButton = addRenderableWidget(Button.builder(Component.literal("Revert"), button -> revert())
                .bounds(x + buttonWidth + 4, actionsY, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.literal("Restore the settings and roads from before the last change. Press again to redo it.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Defaults"), button -> resetToDefaults())
                .bounds(x, actionsY + 20, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.literal("Reset the fields to their default values. Press Apply to use them.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(x + buttonWidth + 4, actionsY + 20, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.literal("Save the applied settings to the config file.")))
                .build());

        this.map.setRectangle(this.width - PANEL_WIDTH - MARGIN * 2, this.height - MARGIN * 2, PANEL_WIDTH + MARGIN, MARGIN);
        addRenderableWidget(this.map);

        selectTab(this.tab);
    }

    private int addDebugCheckbox(int x, int y, int width, String label, String description,
                                 Predicate<ConfigModule.Debug> getter, BiConsumer<ConfigModule.Debug, Boolean> setter) {
        Checkbox checkbox = Checkbox.builder(Component.literal(label), this.font)
                .pos(x, y)
                .maxWidth(width)
                .selected(getter.test(this.pendingDebug))
                .tooltip(Tooltip.create(Component.literal(description + "\nApplied with the Apply button.")))
                .onValueChange((box, value) -> setter.accept(this.pendingDebug, value))
                .build();
        addTabWidget(Tab.PLACEMENT, checkbox);
        return y + CHECKBOX_ROW_HEIGHT;
    }

    private int addViewCheckbox(int x, int y, int width, String label, String description, boolean selected,
                                Consumer<Boolean> onChange) {
        Checkbox checkbox = Checkbox.builder(Component.literal(label), this.font)
                .pos(x, y)
                .maxWidth(width)
                .selected(selected)
                .tooltip(Tooltip.create(Component.literal(description)))
                .onValueChange((box, value) -> onChange.accept(value))
                .build();
        addTabWidget(Tab.VIEW, checkbox);
        return y + CHECKBOX_ROW_HEIGHT;
    }

    private void addTabWidget(Tab tab, AbstractWidget widget) {
        addRenderableWidget(widget);
        this.tabWidgets.computeIfAbsent(tab, t -> new ArrayList<>()).add(widget);
    }

    /**
     * Adds a widget to a scrolling tab.
     *
     * @param y The widget's y when the tab isn't scrolled.
     */
    private void addScrollingWidget(Tab tab, AbstractWidget widget, int y) {
        addTabWidget(tab, widget);
        this.unscrolledY.put(widget, y);
    }

    private void selectTab(Tab tab) {
        this.tab = tab;
        this.tabWidgets.forEach((t, widgets) -> widgets.forEach(widget -> widget.visible = t == tab));
        // A hidden text box must not keep receiving key presses
        setFocused(null);
    }

    @Override
    public void tick() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null) {
            onClose();
            return;
        }
        this.applyButton.active = !RoadTuning.isBusy();
        this.revertButton.active = RoadTuning.canRevert(level);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Status, wrapped above the action buttons
        List<String> statusLines = new ArrayList<>();
        statusLines.add(RoadTuning.status());
        ServerLevel level = RoadDebugClient.serverLevel();
        LiveRoadPlacer placer = level == null ? null : ((IStructureRegionCacheProvider) level).getLiveRoadPlacer();
        if (placer != null && placer.pendingChunkCount() > 0) {
            statusLines.add("Refreshing " + placer.pendingChunkCount() + " chunks...");
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : statusLines) {
            if (!line.isEmpty()) {
                wrapped.addAll(this.font.split(Component.literal(line), PANEL_WIDTH - MARGIN * 2));
            }
        }
        int statusTop = this.applyButton.getY() - 4 - wrapped.size() * 10;

        List<Component> formulas = formulas(this.tab, previewSettings());
        if (this.tab.scrolls()) {
            layOutScrollingTab(statusTop, formulas);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int y = statusTop;
        for (FormattedCharSequence line : wrapped) {
            guiGraphics.drawString(this.font, line, MARGIN, y, 0xFFC0C0C0);
            y += 10;
        }

        if (this.tab.scrolls()) {
            int formulaTop = this.formulaY.get(this.tab) - this.scroll.getOrDefault(this.tab, 0);
            renderFormula(guiGraphics, mouseX, mouseY, formulaTop, statusTop, formulas);
            renderScrollbar(guiGraphics, statusTop);
        }
        renderSettingTooltip(guiGraphics, mouseX, mouseY);
    }

    /** The formulas explaining how a tab's settings are used, with the given settings' values filled in. */
    private static List<Component> formulas(Tab tab, ConfigModule.Advanced settings) {
        return switch (tab) {
            case ROUTING -> List.of(
                    formulaTitle("Routing formulas"),
                    formulaLine(settings, "Step cost = run × (1 + ", AdvancedSetting.SLOPE_WEIGHT, " × max(0, grade² - ", AdvancedSetting.FREE_GRADE, "²))"),
                    formulaLine(settings, "Steps above grade ", AdvancedSetting.MAX_GRADE, " are never taken"),
                    formulaLine(settings, "Bridge cost = step cost + ", AdvancedSetting.WATER_WEIGHT, " × run"),
                    formulaLine(settings, "Bridges are at most ", AdvancedSetting.MAX_BRIDGE_LENGTH, " blocks long"),
                    formulaLine(settings, "Priority = cost so far + ", AdvancedSetting.HEURISTIC_WEIGHT, " × distance left"));
            case SHAPING -> List.of(
                    formulaTitle("Shaping rules"),
                    formulaLine(settings, "Road height = ground averaged over ", AdvancedSetting.SMOOTHING_RADIUS, " blocks each way"),
                    formulaLine(settings, "Ground up to ", AdvancedSetting.MAX_CUT_DEPTH, " blocks above the road is cut away"),
                    formulaLine(settings, "Gaps up to ", AdvancedSetting.MAX_FILL_DEPTH, " blocks below the road are filled, and deeper holes get a land bridge"),
                    formulaLine(settings, "Dips up to ", AdvancedSetting.MAX_LAND_BRIDGE_LENGTH, " blocks long get a land bridge"),
                    formulaLine(settings, "Land bridge edges with drops of ", AdvancedSetting.LAND_BRIDGE_RAILING_DROP, "+ blocks get railings"));
            case PLACEMENT, VIEW -> List.of();
        };
    }

    /**
     * Keeps the current tab's scroll within its settings and formulas, moves its widgets to match, and shows only the
     * widgets that fit between the tab buttons and the given bottom, so none are shown cut off.
     */
    private void layOutScrollingTab(int bottom, List<Component> formulas) {
        int contentBottom = this.formulaY.get(this.tab) + formulasHeight(formulas);
        // Scrolling is by whole rows, so the top row always sits right below the tab buttons instead of being hidden
        int max = Mth.positiveCeilDiv(Math.max(0, contentBottom - bottom), ROW_HEIGHT) * ROW_HEIGHT;
        int offset = Mth.clamp(this.scroll.getOrDefault(this.tab, 0), 0, max);
        this.maxScroll.put(this.tab, max);
        this.scroll.put(this.tab, offset);
        for (AbstractWidget widget : this.tabWidgets.getOrDefault(this.tab, List.of())) {
            int y = this.unscrolledY.get(widget) - offset;
            widget.setY(y);
            widget.visible = y >= CONTENT_Y && y + widget.getHeight() <= bottom;
            // A hidden text box must not keep receiving key presses
            if (!widget.visible && getFocused() == widget) {
                setFocused(null);
            }
        }
    }

    /** Draws a scrollbar at the panel's right edge, if the current tab's content doesn't fit. */
    private void renderScrollbar(GuiGraphics guiGraphics, int bottom) {
        int max = this.maxScroll.getOrDefault(this.tab, 0);
        if (max <= 0) {
            return;
        }
        int x = PANEL_WIDTH - 3;
        int trackHeight = bottom - CONTENT_Y;
        int thumbHeight = Math.max(8, trackHeight * trackHeight / (trackHeight + max));
        int thumbY = CONTENT_Y + (trackHeight - thumbHeight) * this.scroll.getOrDefault(this.tab, 0) / max;
        guiGraphics.fill(x, CONTENT_Y, x + 2, bottom, 0x40FFFFFF);
        guiGraphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xC0FFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < PANEL_WIDTH && this.tab.scrolls()) {
            int offset = this.scroll.getOrDefault(this.tab, 0) - (int) Math.round(scrollY * ROW_HEIGHT);
            this.scroll.put(this.tab, Mth.clamp(offset, 0, this.maxScroll.getOrDefault(this.tab, 0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Shows the hovered setting's description and the definitions of its terms. Hidden while dragging, so the map's
     * preview stays visible.
     */
    private void renderSettingTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.mouseHandler.isLeftPressed()) {
            return;
        }
        for (Map.Entry<AdvancedSetting, List<AbstractWidget>> row : this.settingRows.entrySet()) {
            // Hidden widgets keep the hover state they had when last rendered
            if (row.getValue().stream().noneMatch(widget -> widget.visible && widget.isHovered())) {
                continue;
            }
            AdvancedSetting setting = row.getKey();
            String details = setting.isToggle
                    ? "\nDefault: " + (setting.defaultValue() != 0 ? "on" : "off")
                    : "\nRange: " + setting.format(setting.min) + " to " + setting.format(setting.max)
                    + "\nDefault: " + setting.format(setting.defaultValue());
            Component text = RoutingGlossary.withDefinitions(setting.description + details);
            renderTooltipBesidePanel(guiGraphics, text, mouseX, mouseY);
            return;
        }
    }

    /**
     * Renders a tooltip just right of the panel at the mouse's height, kept on screen. Unlike vanilla tooltip
     * placement, this never covers the panel, so the settings and formulas stay readable.
     */
    private void renderTooltipBesidePanel(GuiGraphics guiGraphics, Component text, int mouseX, int mouseY) {
        guiGraphics.renderTooltip(this.font, this.font.split(text, 220),
                (screenWidth, screenHeight, x, y, width, height) ->
                        new Vector2i(PANEL_WIDTH + 12, Mth.clamp(y - 12, 4, screenHeight - height - 4)),
                mouseX, mouseY);
    }

    /**
     * Explains how a tab's settings are used, with the entered values filled in. Highlighted words and values show
     * their definitions when hovered. Formulas scrolled above the content area or that don't fit above the status text
     * are left out whole, so none are shown cut off.
     *
     * @param top Where the first formula starts, which is above the content area when the tab is scrolled.
     */
    private void renderFormula(GuiGraphics guiGraphics, int mouseX, int mouseY, int top, int bottom, List<Component> paragraphs) {
        int x = MARGIN;
        int y = top;
        Style hovered = null;
        for (Component paragraph : paragraphs) {
            List<FormattedCharSequence> lines = this.font.split(paragraph, PANEL_WIDTH - MARGIN * 2);
            if (y + lines.size() * (this.font.lineHeight + 1) - 1 > bottom) {
                break;
            }
            if (y < CONTENT_Y) {
                y += lines.size() * (this.font.lineHeight + 1) + 2;
                continue;
            }
            for (FormattedCharSequence line : lines) {
                guiGraphics.drawString(this.font, line, x, y, 0xFFE0E0E0);
                if (mouseY >= y && mouseY < y + this.font.lineHeight && mouseX >= x) {
                    hovered = this.font.getSplitter().componentStyleAtWidth(line, mouseX - x);
                }
                y += this.font.lineHeight + 1;
            }
            y += 2;
        }
        if (hovered != null && hovered.getHoverEvent() != null) {
            Component text = hovered.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
            if (text != null) {
                renderTooltipBesidePanel(guiGraphics, text, mouseX, mouseY);
            }
        }
    }

    /** The height of the given formulas, as drawn by {@link #renderFormula}. */
    private int formulasHeight(List<Component> paragraphs) {
        int height = 0;
        for (Component paragraph : paragraphs) {
            height += this.font.split(paragraph, PANEL_WIDTH - MARGIN * 2).size() * (this.font.lineHeight + 1) + 2;
        }
        return height;
    }

    private static Component formulaTitle(String title) {
        return Component.literal(title).withStyle(ChatFormatting.UNDERLINE)
                .append(Component.literal(" (hover for details)").withStyle(style -> style.withUnderlined(false).withColor(0xA0A0A0)));
    }

    /**
     * Builds a line of a formula from strings, with their glossary words highlighted, and settings, shown as their
     * value in the given instance.
     */
    private static Component formulaLine(ConfigModule.Advanced settings, Object... parts) {
        MutableComponent line = Component.empty();
        for (Object part : parts) {
            if (part instanceof AdvancedSetting setting) {
                line.append(RoutingGlossary.value(setting, setting.get(settings)));
            } else {
                line.append(RoutingGlossary.highlight((String) part));
            }
        }
        return line;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Leave the world visible around the panel and map, rather than blurring it like most screens
        guiGraphics.fill(0, 0, PANEL_WIDTH, this.height, PANEL_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The open key also closes the screen, unless it's being typed into a text box
        if (RoadDebugClient.OPEN_SCREEN_KEY.matches(keyCode, scanCode) && !(getFocused() instanceof EditBox)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        // The integrated server must keep running to regenerate and re-place roads
        return false;
    }

    private void apply() {
        ConfigModule.Advanced advanced = parseSettings();
        ServerLevel level = RoadDebugClient.serverLevel();
        if (advanced == null || level == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        RoadTuning.Settings settings = new RoadTuning.Settings(advanced, this.pendingDebug.copy());
        BlockPos center = this.minecraft.player.blockPosition();
        level.getServer().execute(() -> RoadTuning.apply(level, settings, center));
    }

    private void revert() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null) {
            return;
        }
        // Show the restored settings once the server has reverted
        level.getServer().submit(() -> RoadTuning.revert(level))
                .thenRun(() -> this.minecraft.execute(this::loadFieldsFromConfig));
    }

    private void save() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level != null) {
            level.getServer().execute(RoadTuning::saveToConfig);
        }
    }

    private void resetToDefaults() {
        ConfigModule.Advanced defaults = new ConfigModule.Advanced();
        for (AdvancedSetting setting : AdvancedSetting.values()) {
            this.pendingText.put(setting, setting.format(setting.get(defaults)));
        }
        boolean f3Info = this.pendingDebug.enableExtraDebugF3Info;
        this.pendingDebug = new ConfigModule.Debug();
        this.pendingDebug.enableExtraDebugF3Info = f3Info;
        rebuildWidgets();
    }

    private void loadFieldsFromConfig() {
        loadPendingFromConfig();
        rebuildWidgets();
    }

    private void loadPendingFromConfig() {
        for (AdvancedSetting setting : AdvancedSetting.values()) {
            this.pendingText.put(setting, setting.format(setting.get(YungsRoadsCommon.CONFIG.advanced)));
        }
        this.pendingDebug = YungsRoadsCommon.CONFIG.debug.copy();
    }

    /**
     * Parses the settings entered on the settings tabs.
     *
     * @return The settings, or null if any value is invalid.
     */
    @Nullable
    private ConfigModule.Advanced parseSettings() {
        ConfigModule.Advanced advanced = new ConfigModule.Advanced();
        for (AdvancedSetting setting : AdvancedSetting.values()) {
            Double value = parse(setting, this.pendingText.get(setting));
            if (value == null) {
                return null;
            }
            setting.set(advanced, value);
        }
        return advanced;
    }

    /** The entered settings if they're all valid, otherwise the applied ones. Used to preview terrain costs. */
    private ConfigModule.Advanced previewSettings() {
        ConfigModule.Advanced parsed = parseSettings();
        return parsed != null ? parsed : YungsRoadsCommon.CONFIG.advanced;
    }

    @Nullable
    private static Double parse(AdvancedSetting setting, String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return setting.isValid(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Slider for one setting. Dragging it writes the value, rounded to two significant figures, into the
     * setting's text box, which in turn records it as pending.
     */
    private static class SettingSlider extends AbstractSliderButton {
        private final AdvancedSetting setting;
        private final EditBox box;

        SettingSlider(int x, int y, int width, int height, AdvancedSetting setting, EditBox box) {
            super(x, y, width, height, Component.literal(setting.displayName), 0);
            this.setting = setting;
            this.box = box;
        }

        /** Moves the slider to the given value, unless its position already rounds to that value. */
        void show(double value) {
            if (sliderValue() != value) {
                double t = (value - this.setting.min) / (this.setting.max - this.setting.min);
                this.value = Math.pow(Math.max(0, Math.min(1, t)), 1 / this.setting.sliderExponent);
            }
        }

        /** The value at the slider's position, rounded so it's easy to read and type. */
        private double sliderValue() {
            double raw = this.setting.min + (this.setting.max - this.setting.min) * Math.pow(this.value, this.setting.sliderExponent);
            double rounded = this.setting.isInteger || raw == 0 ? Math.rint(raw) : new BigDecimal(raw).round(new MathContext(2)).doubleValue();
            return this.setting.clamp(rounded);
        }

        @Override
        protected void updateMessage() {
            // The value is shown in the text box, so the label never changes
        }

        @Override
        protected void applyValue() {
            this.box.setValue(this.setting.format(sliderValue()));
        }
    }
}
