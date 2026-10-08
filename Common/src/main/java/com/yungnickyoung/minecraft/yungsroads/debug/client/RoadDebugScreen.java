package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTypeExport;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSettings;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadType;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadTypes;
import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
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
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Screen for tuning road generation in a running world. Settings are edited in a side panel and applied to the roads
 * around the player, and a map shows the result next to the terrain that routing sees.
 * <p>
 * Routing and shaping settings belong to a road type, picked at the top of their tabs, and are saved to a datapack.
 * Global settings are saved to the config file, and apply to every road type. Every road type's edits are kept until
 * they're applied, so several can be changed at once.
 */
public class RoadDebugScreen extends Screen {
    /** The lang entry of the global tab's Place roads option. Also listed in the help page. */
    static final String PLACE_ROADS_KEY = "yungsroads.screen.global.place_roads";
    /** The view options with a checkbox, by the id of their lang entries. Also listed in the help page. */
    static final List<String> VIEW_OPTIONS = List.of("world_overlay", "previous_roads", "nodes", "region_borders", "f3_info");

    private static final int PANEL_WIDTH = 200;
    private static final int MARGIN = 6;
    /** The top of each tab's content, below the tab buttons. */
    private static final int CONTENT_Y = MARGIN + 22;
    private static final int ROW_HEIGHT = 18;
    private static final int CHECKBOX_ROW_HEIGHT = 20;
    private static final int VALUE_BOX_WIDTH = 40;
    private static final int HELP_BUTTON_SIZE = 16;
    private static final int PANEL_COLOR = 0xE0101010;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5050;
    private static final int VALID_TEXT_COLOR = 0xFFE0E0E0;

    private enum Tab {
        ROUTING("routing", HelpDrawer.Section.ROUTING),
        SHAPING("shaping", HelpDrawer.Section.SHAPING),
        GLOBAL("global", HelpDrawer.Section.GLOBAL),
        VIEW("view", HelpDrawer.Section.VIEW);

        final Component displayName;
        /** The help page's section about the tab. */
        final HelpDrawer.Section helpSection;

        Tab(String id, HelpDrawer.Section helpSection) {
            this.displayName = Component.translatable("yungsroads.screen.tab." + id);
            this.helpSection = helpSection;
        }

        /** Whether the tab's content scrolls, since its settings and formulas can be taller than the panel. */
        boolean scrolls() {
            return this != VIEW;
        }

        /** The tab a setting is edited on. */
        static Tab of(ITunableSetting setting) {
            if (setting instanceof GlobalSetting) {
                return GLOBAL;
            }
            return switch (setting.group()) {
                case ROUTING -> ROUTING;
                case SHAPING -> SHAPING;
            };
        }
    }

    /** One variant of a road type, whose settings are edited together. */
    private record VariantKey(ResourceLocation typeId, int variant) {
    }

    /** The road type variant last edited, so it's still selected when the screen is opened again. */
    @Nullable
    private static VariantKey lastSelected;

    private Tab tab = Tab.ROUTING;
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);

    /**
     * The road types whose settings are being edited, as they were when the fields were loaded. The edited numeric
     * settings are applied to copies of them.
     */
    private SortedMap<ResourceLocation, RoadType> baseTypes = new TreeMap<>();

    /**
     * Values as edited on the settings tabs, for every road type variant and for the global settings. Kept outside the
     * widgets so they survive the widgets being rebuilt, such as when the window is resized or another road type is
     * picked. Not applied until the Apply button is pressed.
     */
    private final Map<VariantKey, Map<RoadSetting, String>> pendingTypeText = new LinkedHashMap<>();
    private final Map<GlobalSetting, String> pendingGlobalText = new EnumMap<>(GlobalSetting.class);
    private ConfigModule.Debug pendingDebug = new ConfigModule.Debug();

    /** The road types of the level the fields were loaded from, for which of them its roads can get. */
    @Nullable
    private RoadTypes levelTypes;

    /** The road type variant whose settings are shown, or null if the dimension has no road types to tune. */
    @Nullable
    private VariantKey selected;

    /** Each setting's slider and text box, for showing its tooltip when either is hovered. */
    private final Map<ITunableSetting, List<AbstractWidget>> settingRows = new HashMap<>();

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
    private Button helpButton;
    /** Kept across rebuilds, so the map's position and zoom aren't reset. */
    private final RoadMapWidget map = new RoadMapWidget(0, 0, 0, 0, this::mapPreview);
    private HelpDrawer help;

    /** A message about something done on this screen, shown until the server's status next changes. */
    @Nullable
    private Component localStatus;
    private Component serverStatusAtLocal;

    /** Set when the widgets need rebuilding, which is done on the next tick rather than inside a widget's callback. */
    private boolean rebuildPending = false;

    public RoadDebugScreen() {
        super(Component.translatable("yungsroads.screen.title"));
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level != null) {
            loadPendingFromLevel(level);
        }
    }

    @Override
    protected void init() {
        if (this.help == null) {
            this.help = new HelpDrawer(this.font);
        }
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
            addRenderableWidget(Button.builder(t.displayName, button -> selectTab(t))
                    .bounds(tabX, MARGIN, tabWidth - 2, 16)
                    .build());
            tabX += tabWidth;
        }

        // Road type and global settings, each a slider for quick changes with a text box for exact values, or a checkbox
        // for a toggle. Routing changes are previewed on the map's terrain right away, but only applied to roads on Apply.
        Map<Tab, Integer> rowY = new EnumMap<>(Tab.class);
        if (this.selected != null) {
            for (Tab settingsTab : List.of(Tab.ROUTING, Tab.SHAPING)) {
                addScrollingWidget(settingsTab, roadTypeButton(x, CONTENT_Y, contentWidth), CONTENT_Y);
                rowY.put(settingsTab, CONTENT_Y + ROW_HEIGHT + 4);
            }
            for (RoadSetting setting : RoadSetting.values()) {
                addSettingRow(setting, x, contentWidth, rowY);
            }
        } else {
            for (Tab settingsTab : List.of(Tab.ROUTING, Tab.SHAPING)) {
                MultiLineTextWidget noNetworkNote = new MultiLineTextWidget(x, CONTENT_Y, Component.translatable("yungsroads.screen.no_network"), this.font)
                        .setMaxWidth(contentWidth)
                        .setColor(0xFFA0A0A0);
                addScrollingWidget(settingsTab, noNetworkNote, CONTENT_Y);
                rowY.put(settingsTab, CONTENT_Y + noNetworkNote.getHeight());
            }
        }
        // The global tab says where its settings are saved, since unlike the other settings tabs, it isn't for a road type
        MultiLineTextWidget globalNote = new MultiLineTextWidget(x, CONTENT_Y, Component.translatable("yungsroads.screen.global.note"), this.font)
                .setMaxWidth(contentWidth)
                .setColor(0xFFA0A0A0);
        addScrollingWidget(Tab.GLOBAL, globalNote, CONTENT_Y);
        rowY.put(Tab.GLOBAL, CONTENT_Y + globalNote.getHeight() + 6);
        for (GlobalSetting setting : GlobalSetting.values()) {
            addSettingRow(setting, x, contentWidth, rowY);
        }
        int placeRoadsY = rowY.get(Tab.GLOBAL) + 2;
        addScrollingWidget(Tab.GLOBAL, Checkbox.builder(Component.translatable(PLACE_ROADS_KEY), this.font)
                .pos(x, placeRoadsY)
                .maxWidth(contentWidth)
                .selected(this.pendingDebug.placeRoads)
                .tooltip(Tooltip.create(Component.translatable(PLACE_ROADS_KEY + ".description").append("\n")
                        .append(Component.translatable("yungsroads.screen.applied_with_apply"))))
                .onValueChange((box, value) -> this.pendingDebug.placeRoads = value)
                .build(), placeRoadsY);
        rowY.put(Tab.GLOBAL, placeRoadsY + CHECKBOX_ROW_HEIGHT);
        rowY.forEach((settingsTab, y) -> this.formulaY.put(settingsTab, y + 6));

        // View options. These only affect what's drawn, so they take effect immediately.
        int y = CONTENT_Y;
        y = addViewCheckbox(x, y, contentWidth, VIEW_OPTIONS.get(0), RoadDebugClient.showWorldOverlay, value -> RoadDebugClient.showWorldOverlay = value);
        y = addViewCheckbox(x, y, contentWidth, VIEW_OPTIONS.get(1), RoadDebugClient.showPreviousRoads, value -> RoadDebugClient.showPreviousRoads = value);
        y = addViewCheckbox(x, y, contentWidth, VIEW_OPTIONS.get(2), RoadDebugClient.showNodes, value -> RoadDebugClient.showNodes = value);
        y = addViewCheckbox(x, y, contentWidth, VIEW_OPTIONS.get(3), RoadDebugClient.showRegionBorders, value -> RoadDebugClient.showRegionBorders = value);
        y = addViewCheckbox(x, y, contentWidth, VIEW_OPTIONS.get(4), YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info, value -> {
            YungsRoadsCommon.CONFIG.debug.enableExtraDebugF3Info = value;
            this.pendingDebug.enableExtraDebugF3Info = value;
        });
        CycleButton<TerrainTiles.Layer> terrainButton = CycleButton.<TerrainTiles.Layer>builder(layer -> layer.displayName)
                .withValues(TerrainTiles.Layer.values())
                .withInitialValue(RoadDebugClient.terrainLayer)
                .withTooltip(layer -> Tooltip.create(Component.translatable("yungsroads.screen.view.terrain.tooltip")))
                .create(x, y, contentWidth, 16, Component.translatable("yungsroads.screen.view.terrain"),
                        (button, layer) -> RoadDebugClient.terrainLayer = layer);
        addTabWidget(Tab.VIEW, terrainButton);
        y += ROW_HEIGHT;
        addTabWidget(Tab.VIEW, Button.builder(Component.translatable("yungsroads.screen.view.center_map"), button -> this.map.recenter())
                .bounds(x, y, contentWidth, 16)
                .build());

        // Actions
        int buttonWidth = (contentWidth - 4) / 2;
        int actionsY = this.height - MARGIN - 56;
        this.applyButton = addRenderableWidget(Button.builder(Component.translatable("yungsroads.screen.apply"), button -> apply())
                .bounds(x, actionsY, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.apply.tooltip", RoadTuning.REGENERATE_RADIUS)))
                .build());
        this.revertButton = addRenderableWidget(Button.builder(Component.translatable("yungsroads.screen.revert"), button -> revert())
                .bounds(x + buttonWidth + 4, actionsY, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.revert.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("yungsroads.screen.reset"), button -> reset())
                .bounds(x, actionsY + 20, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.reset.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("yungsroads.screen.save"), button -> save())
                .bounds(x + buttonWidth + 4, actionsY + 20, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.save.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("yungsroads.screen.copy_json"), button -> copyJson())
                .bounds(x, actionsY + 40, contentWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.copy_json.tooltip")))
                .build());

        // The help button sits over the map, so it takes clicks before the map does, but is drawn after it. Moved to the
        // help page's edge as the page slides out.
        this.helpButton = addWidget(Button.builder(Component.literal("?"), button -> this.help.toggle(helpSection()))
                .bounds(helpButtonX(), MARGIN + 2, HELP_BUTTON_SIZE, HELP_BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.help.tooltip")))
                .build());
        this.map.setRectangle(mapWidth(), this.height - MARGIN * 2, PANEL_WIDTH + MARGIN, MARGIN);
        addRenderableWidget(this.map);
        addRenderableOnly(this.helpButton);

        selectTab(this.tab);
    }

    /**
     * The picker for which road type variant's settings are shown. Lists only the road types this dimension's roads
     * can get, since changes to the others wouldn't show here.
     */
    private CycleButton<VariantKey> roadTypeButton(int x, int y, int width) {
        Component defaultName = Component.literal(this.levelTypes == null || this.levelTypes.defaultId() == null
                ? "-" : RoadTypeNames.name(this.levelTypes.defaultId()));
        return CycleButton.<VariantKey>builder(key -> {
                    RoadType type = this.baseTypes.get(key.typeId);
                    Component name = RoadTypeNames.shortName(key.typeId, key.variant, type == null ? 1 : type.variants().size());
                    return Component.translatable("yungsroads.screen.road_type", name);
                })
                .withValues(usableKeys())
                .withInitialValue(this.selected)
                .displayOnlyValue()
                .withTooltip(key -> Tooltip.create(Component.translatable("yungsroads.screen.road_type.tooltip", defaultName)))
                .create(x, y, width, 16, Component.translatable("yungsroads.glossary.road_type"), (button, key) -> {
                    this.selected = key;
                    lastSelected = key;
                    this.rebuildPending = true;
                });
    }

    private void addSettingRow(ITunableSetting setting, int x, int contentWidth, Map<Tab, Integer> rowY) {
        Tab settingTab = Tab.of(setting);
        int y = rowY.getOrDefault(settingTab, CONTENT_Y);
        rowY.put(settingTab, y + ROW_HEIGHT);
        Component name = Component.translatable(setting.nameKey());
        if (setting.isToggle()) {
            Double value = parse(setting, pendingText(setting));
            Checkbox checkbox = Checkbox.builder(name, this.font)
                    .pos(x, y)
                    .maxWidth(contentWidth)
                    .selected(value != null && value != 0)
                    .onValueChange((box, selected) -> setPendingText(setting, selected ? "1" : "0"))
                    .build();
            this.settingRows.put(setting, List.of(checkbox));
            addScrollingWidget(settingTab, checkbox, y);
            return;
        }
        int sliderWidth = contentWidth - VALUE_BOX_WIDTH - 4;
        EditBox box = new EditBox(this.font, x + sliderWidth + 4, y + 1, VALUE_BOX_WIDTH, 14, name);
        SettingSlider slider = new SettingSlider(x, y, sliderWidth, 16, setting, name, box);
        box.setMaxLength(12);
        box.setResponder(value -> {
            setPendingText(setting, value);
            Double parsed = parse(setting, value);
            box.setTextColor(parsed == null ? INVALID_TEXT_COLOR : VALID_TEXT_COLOR);
            if (parsed != null) {
                slider.show(parsed);
            }
        });
        box.setValue(pendingText(setting));
        this.settingRows.put(setting, List.of(slider, box));
        addScrollingWidget(settingTab, slider, y);
        addScrollingWidget(settingTab, box, y + 1);
    }

    private int addViewCheckbox(int x, int y, int width, String option, boolean selected, Consumer<Boolean> onChange) {
        String key = "yungsroads.screen.view." + option;
        Checkbox checkbox = Checkbox.builder(Component.translatable(key), this.font)
                .pos(x, y)
                .maxWidth(width)
                .selected(selected)
                .tooltip(Tooltip.create(Component.translatable(key + ".description")))
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

    /**
     * The help page's section for the current tab. The road type tabs of a dimension without a road network have
     * nothing to tune, so they open at the section on road networks instead.
     */
    private HelpDrawer.Section helpSection() {
        boolean roadTypeTab = this.tab == Tab.ROUTING || this.tab == Tab.SHAPING;
        return roadTypeTab && this.selected == null ? HelpDrawer.Section.ROAD_NETWORKS : this.tab.helpSection;
    }

    private void selectTab(Tab tab) {
        this.tab = tab;
        this.tabWidgets.forEach((t, widgets) -> widgets.forEach(widget -> widget.visible = t == tab));
        // A hidden text box must not keep receiving key presses
        setFocused(null);
        if (this.help.isOpen()) {
            this.help.showSection(helpSection());
        }
    }

    @Override
    public void tick() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null) {
            onClose();
            return;
        }
        if (this.rebuildPending) {
            this.rebuildPending = false;
            rebuildWidgets();
        }
        this.applyButton.active = !RoadTuning.isBusy();
        this.revertButton.active = RoadTuning.canRevert(level);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Status, wrapped above the action buttons
        List<Component> statusLines = new ArrayList<>();
        statusLines.add(status());
        ServerLevel level = RoadDebugClient.serverLevel();
        LiveRoadPlacer placer = level == null ? null : ((IStructureRegionCacheProvider) level).getLiveRoadPlacer();
        if (placer != null && placer.pendingChunkCount() > 0) {
            statusLines.add(Component.translatable("yungsroads.screen.refreshing", placer.pendingChunkCount()));
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : statusLines) {
            if (!line.getString().isEmpty()) {
                wrapped.addAll(this.font.split(line, PANEL_WIDTH - MARGIN * 2));
            }
        }
        int statusTop = this.applyButton.getY() - 4 - wrapped.size() * 10;

        List<Component> formulas = this.selected == null ? List.of() : formulas(this.tab, previewSettings(), previewGlobal());
        if (this.tab.scrolls()) {
            layOutScrollingTab(statusTop, formulas);
        }

        // Nothing under the help page reacts to the mouse
        boolean overHelp = this.help.isMouseOver(mouseX, mouseY, this.width, mapWidth());
        int widgetMouseX = overHelp ? -1 : mouseX;
        int widgetMouseY = overHelp ? -1 : mouseY;
        this.helpButton.setX(helpButtonX());
        // The help button's tooltip shows instead of the map's
        this.map.setHoverInfoHidden(this.helpButton.isMouseOver(widgetMouseX, widgetMouseY));
        super.render(guiGraphics, widgetMouseX, widgetMouseY, partialTick);

        int y = statusTop;
        for (FormattedCharSequence line : wrapped) {
            guiGraphics.drawString(this.font, line, MARGIN, y, 0xFFC0C0C0);
            y += 10;
        }

        if (this.tab.scrolls()) {
            int formulaTop = this.formulaY.get(this.tab) - this.scroll.getOrDefault(this.tab, 0);
            renderFormula(guiGraphics, widgetMouseX, widgetMouseY, formulaTop, statusTop, formulas);
            renderScrollbar(guiGraphics, statusTop);
        }
        renderSettingTooltip(guiGraphics, widgetMouseX, widgetMouseY);
        this.help.render(guiGraphics, this.width, this.height, mapWidth(), mouseX, mouseY);
    }

    /** The message shown above the action buttons: the latest of this screen's and the server's. */
    private Component status() {
        Component serverStatus = RoadTuning.status();
        if (this.localStatus != null && serverStatus == this.serverStatusAtLocal) {
            return this.localStatus;
        }
        this.localStatus = null;
        return serverStatus;
    }

    private void showLocalStatus(Component status) {
        this.localStatus = status;
        this.serverStatusAtLocal = RoadTuning.status();
    }

    /** The width of the map, which fills the screen right of the settings panel. The help page slides over it. */
    private int mapWidth() {
        return this.width - PANEL_WIDTH - MARGIN * 2;
    }

    /** The help button sits in the map's top right corner, or beside the help page's edge while it's out. */
    private int helpButtonX() {
        int closedX = this.width - MARGIN - HELP_BUTTON_SIZE - 2;
        if (this.help == null || !this.help.isVisible()) {
            return closedX;
        }
        return Math.min(closedX, this.help.left(this.width, mapWidth()) - HELP_BUTTON_SIZE - 4);
    }

    /**
     * The formulas explaining how a tab's settings are used, with the given settings' values filled in.
     */
    private static List<Component> formulas(Tab tab, RoadSettings settings, ConfigModule.Advanced global) {
        ToDoubleFunction<ITunableSetting> valueOf = setting -> setting instanceof RoadSetting roadSetting
                ? roadSetting.get(settings)
                : ((GlobalSetting) setting).get(global);
        return switch (tab) {
            case ROUTING -> List.of(
                    formulaTitle("yungsroads.formula.routing.title"),
                    formulaLine(valueOf, "yungsroads.formula.step_cost", RoadSetting.SLOPE_WEIGHT, RoadSetting.FREE_GRADE),
                    formulaLine(valueOf, "yungsroads.formula.max_grade", RoadSetting.MAX_GRADE),
                    formulaLine(valueOf, "yungsroads.formula.bridge_cost", RoadSetting.WATER_WEIGHT),
                    formulaLine(valueOf, "yungsroads.formula.max_bridge_length", RoadSetting.MAX_BRIDGE_LENGTH));
            case SHAPING -> List.of(
                    formulaTitle("yungsroads.formula.shaping.title"),
                    formulaLine(valueOf, "yungsroads.formula.smoothing", RoadSetting.SMOOTHING_RADIUS),
                    formulaLine(valueOf, "yungsroads.formula.cut", RoadSetting.MAX_CUT_DEPTH),
                    formulaLine(valueOf, "yungsroads.formula.fill", RoadSetting.MAX_FILL_DEPTH),
                    formulaLine(valueOf, "yungsroads.formula.land_bridge", RoadSetting.MAX_LAND_BRIDGE_LENGTH),
                    formulaLine(valueOf, "yungsroads.formula.railing", RoadSetting.LAND_BRIDGE_RAILING_DROP));
            case GLOBAL -> List.of(
                    formulaTitle("yungsroads.formula.global.title"),
                    formulaLine(valueOf, "yungsroads.formula.search_priority", GlobalSetting.HEURISTIC_WEIGHT));
            case VIEW -> List.of();
        };
    }

    /**
     * Keeps the current tab's scroll within its settings and formulas, moves its widgets to match, and shows only the
     * widgets that fit between the tab buttons and the given bottom, so none are shown cut off.
     */
    private void layOutScrollingTab(int bottom, List<Component> formulas) {
        int contentBottom = this.formulaY.getOrDefault(this.tab, CONTENT_Y) + formulasHeight(formulas);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.help.isMouseOver(mouseX, mouseY, this.width, mapWidth())) {
            // The help page has nothing to click, but nothing under it may be clicked either
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.help.isMouseOver(mouseX, mouseY, this.width, mapWidth())) {
            return this.help.mouseScrolled(scrollY);
        }
        if (mouseX < PANEL_WIDTH && this.tab.scrolls()) {
            int offset = this.scroll.getOrDefault(this.tab, 0) - (int) Math.round(scrollY * ROW_HEIGHT);
            this.scroll.put(this.tab, Mth.clamp(offset, 0, this.maxScroll.getOrDefault(this.tab, 0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Shows the hovered setting's description and the definitions of its terms. Hidden while dragging, so the map's
     * preview stays visible, and while the help page is open, since it describes every setting.
     */
    private void renderSettingTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.mouseHandler.isLeftPressed() || this.help.isOpen()) {
            return;
        }
        for (Map.Entry<ITunableSetting, List<AbstractWidget>> row : this.settingRows.entrySet()) {
            // Hidden widgets keep the hover state they had when last rendered
            if (row.getValue().stream().noneMatch(widget -> widget.visible && widget.isHovered())) {
                continue;
            }
            ITunableSetting setting = row.getKey();
            Language language = Language.getInstance();
            String details = setting.isToggle()
                    ? "\n" + language.getOrDefault("yungsroads.screen.default").formatted(
                            language.getOrDefault(setting.defaultValue() != 0 ? "yungsroads.screen.on" : "yungsroads.screen.off"))
                    : "\n" + language.getOrDefault("yungsroads.screen.range").formatted(setting.format(setting.min()), setting.format(setting.max()))
                    + "\n" + language.getOrDefault("yungsroads.screen.default").formatted(setting.format(setting.defaultValue()));
            Component text = RoutingGlossary.withDefinitions(language.getOrDefault(setting.descriptionKey()) + details);
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
     * their definitions when hovered, unless the help page is open. Formulas scrolled above the content area or that
     * don't fit above the status text are left out whole, so none are shown cut off.
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
        if (hovered != null && hovered.getHoverEvent() != null && !this.help.isOpen()) {
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

    private static Component formulaTitle(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.UNDERLINE)
                .append(Component.translatable("yungsroads.formula.hover").withStyle(style -> style.withUnderlined(false).withColor(0xA0A0A0)));
    }

    /**
     * Builds a line of a formula from its lang entry, with its glossary words highlighted, and each {@code %s} replaced
     * by the next setting's value.
     */
    private static Component formulaLine(ToDoubleFunction<ITunableSetting> valueOf, String key, ITunableSetting... settings) {
        String[] parts = Language.getInstance().getOrDefault(key).split("%s", -1);
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            line.append(RoutingGlossary.highlight(parts[i]));
            if (i < parts.length - 1 && i < settings.length) {
                line.append(RoutingGlossary.value(settings[i], valueOf.applyAsDouble(settings[i])));
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
        // Escape closes the help page before the screen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.help.isOpen()) {
            this.help.close();
            return true;
        }
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
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        ConfigModule.Advanced global = parseGlobal();
        SortedMap<ResourceLocation, RoadType> types = parseTypes();
        if (global == null || types == null) {
            return;
        }
        RoadTuning.Settings settings = new RoadTuning.Settings(global, types, this.pendingDebug.copy());
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
                .thenRun(() -> this.minecraft.execute(() -> {
                    loadPendingFromLevel(level);
                    rebuildWidgets();
                }));
    }

    private void save() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level != null) {
            level.getServer().execute(() -> RoadTuning.save(level));
        }
    }

    /**
     * Resets the road types to the settings the level loaded with, and the global settings to their defaults.
     */
    private void reset() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null) {
            return;
        }
        this.levelTypes = RoadTuning.roadTypesOf(level);
        loadTypes(this.levelTypes.loaded());
        ConfigModule.Advanced defaults = new ConfigModule.Advanced();
        for (GlobalSetting setting : GlobalSetting.values()) {
            this.pendingGlobalText.put(setting, setting.format(setting.get(defaults)));
        }
        boolean f3Info = this.pendingDebug.enableExtraDebugF3Info;
        this.pendingDebug = new ConfigModule.Debug();
        this.pendingDebug.enableExtraDebugF3Info = f3Info;
        rebuildWidgets();
    }

    /** Copies the selected road type, with any unapplied changes, to the clipboard as a datapack file. */
    private void copyJson() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (this.selected == null) {
            showLocalStatus(Component.translatable("yungsroads.screen.no_network"));
            return;
        }
        SortedMap<ResourceLocation, RoadType> types = parseTypes();
        if (level == null || types == null || this.minecraft == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(RoadTypeExport.toJson(types.get(this.selected.typeId), level.registryAccess()));
        showLocalStatus(Component.translatable("yungsroads.screen.copied", this.selected.typeId.toString()));
    }

    private void loadPendingFromLevel(ServerLevel level) {
        this.levelTypes = RoadTuning.roadTypesOf(level);
        loadTypes(this.levelTypes.current());
        for (GlobalSetting setting : GlobalSetting.values()) {
            this.pendingGlobalText.put(setting, setting.format(setting.get(YungsRoadsCommon.CONFIG.advanced)));
        }
        this.pendingDebug = YungsRoadsCommon.CONFIG.debug.copy();
    }

    /**
     * Loads the fields of every road type variant from the given types, keeping the selected variant if this dimension
     * can still use it.
     */
    private void loadTypes(Map<ResourceLocation, RoadType> types) {
        this.baseTypes = new TreeMap<>();
        types.forEach((id, type) -> this.baseTypes.put(id, type.copy()));
        this.pendingTypeText.clear();
        this.baseTypes.forEach((id, type) -> {
            for (int variant = 0; variant < type.variants().size(); variant++) {
                RoadSettings settings = type.variants().get(variant).settings();
                Map<RoadSetting, String> text = new EnumMap<>(RoadSetting.class);
                for (RoadSetting setting : RoadSetting.values()) {
                    text.put(setting, setting.format(setting.get(settings)));
                }
                this.pendingTypeText.put(new VariantKey(id, variant), text);
            }
        });
        VariantKey preferred = this.selected != null ? this.selected : lastSelected;
        List<VariantKey> usable = usableKeys();
        this.selected = usable.contains(preferred) ? preferred : usable.stream().findFirst().orElse(null);
    }

    /** The road type variants this dimension's roads can get, in the picker's order. */
    private List<VariantKey> usableKeys() {
        return this.pendingTypeText.keySet().stream()
                .filter(key -> this.levelTypes != null && this.levelTypes.isUsable(key.typeId))
                .toList();
    }

    private String pendingText(ITunableSetting setting) {
        return setting instanceof RoadSetting roadSetting
                ? this.pendingTypeText.get(this.selected).get(roadSetting)
                : this.pendingGlobalText.get((GlobalSetting) setting);
    }

    private void setPendingText(ITunableSetting setting, String text) {
        if (setting instanceof RoadSetting roadSetting) {
            this.pendingTypeText.get(this.selected).put(roadSetting, text);
        } else {
            this.pendingGlobalText.put((GlobalSetting) setting, text);
        }
    }

    /** The name of a road type variant, as shown in the picker and on the map. */
    private Component variantName(VariantKey key) {
        RoadType type = this.baseTypes.get(key.typeId);
        return RoadTypeNames.name(key.typeId, key.variant, type == null ? 1 : type.variants().size());
    }

    /**
     * Parses the global settings entered on the global tab.
     *
     * @return The settings, or null if any value is invalid, which is reported in the status.
     */
    @Nullable
    private ConfigModule.Advanced parseGlobal() {
        ConfigModule.Advanced advanced = new ConfigModule.Advanced();
        for (GlobalSetting setting : GlobalSetting.values()) {
            Double value = parse(setting, this.pendingGlobalText.get(setting));
            if (value == null) {
                reportInvalid(Component.translatable(setting.nameKey()));
                return null;
            }
            setting.set(advanced, value);
        }
        return advanced;
    }

    /**
     * Parses the settings entered for every road type variant, applied to copies of the road types.
     *
     * @return The road types, or null if any value is invalid, which is reported in the status.
     */
    @Nullable
    private SortedMap<ResourceLocation, RoadType> parseTypes() {
        SortedMap<ResourceLocation, RoadType> types = new TreeMap<>();
        this.baseTypes.forEach((id, type) -> types.put(id, type.copy()));
        for (Map.Entry<VariantKey, Map<RoadSetting, String>> entry : this.pendingTypeText.entrySet()) {
            VariantKey key = entry.getKey();
            RoadSettings settings = types.get(key.typeId).variants().get(key.variant).settings();
            for (RoadSetting setting : RoadSetting.values()) {
                Double value = parse(setting, entry.getValue().get(setting));
                if (value == null) {
                    reportInvalid(Component.empty().append(variantName(key)).append(": ").append(Component.translatable(setting.nameKey())));
                    return null;
                }
                setting.set(settings, value);
            }
        }
        return types;
    }

    private void reportInvalid(Component setting) {
        showLocalStatus(Component.translatable("yungsroads.screen.invalid", setting).withStyle(style -> style.withColor(INVALID_TEXT_COLOR)));
    }

    /**
     * The selected variant's settings as entered, or as applied if any entered value is invalid. Used to preview
     * terrain costs and to fill in the formulas.
     */
    private RoadSettings previewSettings() {
        RoadType type = this.baseTypes.get(this.selected.typeId);
        RoadSettings settings = type.variants().get(this.selected.variant).settings().copy();
        Map<RoadSetting, String> text = this.pendingTypeText.get(this.selected);
        for (RoadSetting setting : RoadSetting.values()) {
            Double value = parse(setting, text.get(setting));
            if (value == null) {
                return type.variants().get(this.selected.variant).settings();
            }
            setting.set(settings, value);
        }
        return settings;
    }

    /** The global settings as entered, or as applied if any entered value is invalid. */
    private ConfigModule.Advanced previewGlobal() {
        ConfigModule.Advanced advanced = new ConfigModule.Advanced();
        for (GlobalSetting setting : GlobalSetting.values()) {
            Double value = parse(setting, this.pendingGlobalText.get(setting));
            if (value == null) {
                return YungsRoadsCommon.CONFIG.advanced;
            }
            setting.set(advanced, value);
        }
        return advanced;
    }

    @Nullable
    private RoadMapWidget.Preview mapPreview() {
        if (this.selected == null) {
            return null;
        }
        return new RoadMapWidget.Preview(variantName(this.selected), previewSettings());
    }

    @Nullable
    private static Double parse(ITunableSetting setting, String text) {
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
        private final ITunableSetting setting;
        private final EditBox box;

        SettingSlider(int x, int y, int width, int height, ITunableSetting setting, Component name, EditBox box) {
            super(x, y, width, height, name, 0);
            this.setting = setting;
            this.box = box;
        }

        /** Moves the slider to the given value, unless its position already rounds to that value. */
        void show(double value) {
            if (sliderValue() != value) {
                double t = (value - this.setting.min()) / (this.setting.max() - this.setting.min());
                this.value = Math.pow(Math.max(0, Math.min(1, t)), 1 / this.setting.sliderExponent());
            }
        }

        /** The value at the slider's position, rounded so it's easy to read and type. */
        private double sliderValue() {
            double raw = this.setting.min() + (this.setting.max() - this.setting.min()) * Math.pow(this.value, this.setting.sliderExponent());
            double rounded = this.setting.isInteger() || raw == 0 ? Math.rint(raw) : new BigDecimal(raw).round(new MathContext(2)).doubleValue();
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
