package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * The Road Type tab walks through tuning a road type: pick an existing road type and variant as the baseline, edit its
 * routing and shaping settings, apply them to preview the roads, then export the road type as a datapack file. Global
 * settings have their own tab, since they apply to every road type and are saved to the config file instead. View
 * options only change what's drawn, so they take effect right away. Every road type's edits are kept until they're
 * applied, so several can be changed at once.
 */
public class RoadDebugScreen extends Screen {
    /** The lang entry of the global tab's Place roads option. Also listed in the help page. */
    static final String PLACE_ROADS_KEY = "yungsroads.screen.global.place_roads";
    /** The view options with a checkbox, by the id of their lang entries. Also listed in the help page. */
    static final List<String> VIEW_OPTIONS = List.of("world_overlay", "previous_roads", "nodes", "region_borders", "f3_info");

    private static final int PANEL_WIDTH = 200;
    private static final int MARGIN = 6;
    private static final int TAB_HEIGHT = 20;
    /** The bottom of the tabs, where each tab's content starts. */
    private static final int TABS_BOTTOM = MARGIN + TAB_HEIGHT;
    private static final int SUB_TAB_HEIGHT = 14;
    private static final int BUTTON_HEIGHT = 16;
    private static final int STEP_HEIGHT = 12;
    private static final int ROW_HEIGHT = 18;
    private static final int CHECKBOX_ROW_HEIGHT = 20;
    private static final int VALUE_BOX_WIDTH = 40;
    private static final int TOOLTIP_WIDTH = 220;
    private static final int DRAWER_TAB_GAP = 4;
    /** The least space kept between a tooltip and the screen's top and bottom. */
    private static final int TOOLTIP_MARGIN = 4;
    private static final int PANEL_COLOR = 0xE0101010;
    private static final int DIVIDER_COLOR = 0xFF505050;
    private static final int NOTE_COLOR = 0xFFA0A0A0;
    private static final int STEP_COLOR = 0xFFC0C0C0;
    private static final int STEP_BADGE_COLOR = 0xFF3A6EA5;
    private static final int INVALID_TEXT_COLOR = 0xFFFF5050;
    private static final int VALID_TEXT_COLOR = 0xFFE0E0E0;
    /** Marks values and road types that differ from what Reset restores. */
    private static final int EDITED_COLOR = 0xFFFFD050;

    private enum Tab {
        ROAD_TYPE("road_type"),
        GLOBAL("global"),
        VIEW("view");

        final Component displayName;

        Tab(String id) {
            this.displayName = Component.translatable("yungsroads.screen.tab." + id);
        }
    }

    /** A page of settings or options. The road type tab has a page for each group of settings, the others one each. */
    private enum Page {
        ROUTING(Tab.ROAD_TYPE, "routing", HelpPage.Section.ROUTING),
        SHAPING(Tab.ROAD_TYPE, "shaping", HelpPage.Section.SHAPING),
        GLOBAL(Tab.GLOBAL, "global", HelpPage.Section.GLOBAL),
        VIEW(Tab.VIEW, "view", HelpPage.Section.VIEW);

        final Tab tab;
        final Component displayName;
        /** The help page's section about the page. */
        final HelpPage.Section helpSection;

        Page(Tab tab, String id, HelpPage.Section helpSection) {
            this.tab = tab;
            this.displayName = Component.translatable("yungsroads.screen.tab." + id);
            this.helpSection = helpSection;
        }

        /** Whether the page's content scrolls, since its settings and formulas can be taller than the panel. */
        boolean scrolls() {
            return this != VIEW;
        }

        /** The page a setting is edited on. */
        static Page of(ITunableSetting setting) {
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

    /** A numbered step of a tab's workflow, drawn as a label above its widgets. A number of 0 draws no number. */
    private record Step(Tab tab, int number, Component text, int y) {
    }

    /** The road type variant last edited, so it's still selected when the screen is opened again. */
    @Nullable
    private static VariantKey lastSelected;

    private Tab tab = Tab.ROAD_TYPE;
    /** The road type tab's page, kept while other tabs are shown. */
    private Page roadTypePage = Page.ROUTING;
    /** Widgets shown whenever their tab is, such as the road type pickers and action buttons. */
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);
    /** Widgets shown on their page, scrolling with it if it scrolls. */
    private final Map<Page, List<AbstractWidget>> pageWidgets = new EnumMap<>(Page.class);
    private final List<Step> steps = new ArrayList<>();

    /**
     * The road types whose settings are being edited, as they were when the fields were loaded. The edited numeric
     * settings are applied to copies of them.
     */
    private SortedMap<ResourceLocation, RoadType> baseTypes = new TreeMap<>();

    /**
     * Values as edited on the settings pages, for every road type variant and for the global settings. Kept outside the
     * widgets so they survive the widgets being rebuilt, such as when the window is resized or another road type is
     * picked. Not applied until the Apply button is pressed.
     */
    private final Map<VariantKey, Map<RoadSetting, String>> pendingTypeText = new LinkedHashMap<>();
    private final Map<GlobalSetting, String> pendingGlobalText = new EnumMap<>(GlobalSetting.class);
    private ConfigModule.Debug pendingDebug = new ConfigModule.Debug();

    /** The road types of the level the fields were loaded from: as loaded, as applied, and which its roads can get. */
    @Nullable
    private RoadTypes levelTypes;

    /** The road type variant whose settings are shown, or null if the dimension has no road types to tune. */
    @Nullable
    private VariantKey selected;

    /** Each setting's slider and text box, for showing its tooltip when either is hovered. */
    private final Map<ITunableSetting, List<AbstractWidget>> settingRows = new HashMap<>();

    /** The top of each page's content, below its tab's header. */
    private final Map<Page, Integer> pageTop = new EnumMap<>(Page.class);
    /** Where each settings page's formulas start, below its settings. */
    private final Map<Page, Integer> formulaY = new EnumMap<>(Page.class);
    /** How far each scrolling page is scrolled down, in pixels. Kept across rebuilds. */
    private final Map<Page, Integer> scroll = new EnumMap<>(Page.class);
    /** The furthest each scrolling page could scroll when it was last rendered. */
    private final Map<Page, Integer> maxScroll = new EnumMap<>(Page.class);
    /** The shown page's scroll bar, laid out for it each frame. Scrolls by whole rows, like the mouse wheel. */
    private final ScrollBar scrollBar = new ScrollBar(ROW_HEIGHT);
    /** The y of each widget on a scrolling page when the page isn't scrolled. */
    private final Map<AbstractWidget, Integer> unscrolledY = new HashMap<>();
    /** The top of the action buttons at the bottom of the panel, with their step labels. The status is drawn above. */
    private int footerTop;

    /** The Apply and Undo buttons, which the road type and global tabs each have. */
    private final List<Button> applyButtons = new ArrayList<>();
    private final List<Button> revertButtons = new ArrayList<>();
    /** The buttons that export the applied road type, which are disabled while it has unapplied changes. */
    private final List<Button> exportButtons = new ArrayList<>();
    private Button saveConfigButton;
    /** The tabs that open the side drawer's pages, stacked at its left edge. */
    private DrawerTab helpTab;
    private DrawerTab inspectTab;
    @Nullable
    private Dropdown<ResourceLocation> roadTypeDropdown;
    @Nullable
    private Dropdown<VariantKey> variantDropdown;
    /** Whether the export buttons were last given the tooltip saying to apply first, so it's only replaced when that changes. */
    @Nullable
    private Boolean exportBlocked;
    @Nullable
    private Boolean saveConfigBlocked;
    /** Kept across rebuilds, so the map's position and zoom aren't reset. */
    private final RoadMapWidget map = new RoadMapWidget(0, 0, 0, 0, this::mapPreview, this::showInspectPage);
    /** The drawer over the map's right side, showing the help page or the inspect page. Kept across rebuilds. */
    private SideDrawer drawer;
    private HelpPage helpPage;
    private InspectPage inspectPage;

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
        if (this.drawer == null) {
            this.helpPage = new HelpPage(this.font);
            this.inspectPage = new InspectPage(this.font, this.map, this::mapPreview);
            this.drawer = new SideDrawer(this.font, this.helpPage);
        }
        this.tabWidgets.clear();
        this.pageWidgets.clear();
        this.steps.clear();
        this.settingRows.clear();
        this.unscrolledY.clear();
        this.applyButtons.clear();
        this.revertButtons.clear();
        this.exportButtons.clear();
        this.exportBlocked = null;
        this.saveConfigBlocked = null;
        this.roadTypeDropdown = null;
        this.variantDropdown = null;
        int x = MARGIN;
        int contentWidth = PANEL_WIDTH - MARGIN * 2;

        // Tabs, sharing the panel's width, with the last taking any width left over from rounding
        int tabWidth = contentWidth / Tab.values().length;
        for (Tab t : Tab.values()) {
            int width = t.ordinal() == Tab.values().length - 1 ? contentWidth - tabWidth * t.ordinal() : tabWidth;
            addRenderableWidget(new PanelTab(x + tabWidth * t.ordinal(), MARGIN, width, TAB_HEIGHT, t.displayName, false,
                    () -> this.tab == t, () -> selectTab(t)));
        }

        initFooters(x, contentWidth);
        initRoadTypeHeader(x, contentWidth);
        int otherTop = TABS_BOTTOM + 6;
        this.pageTop.put(Page.GLOBAL, otherTop);
        this.pageTop.put(Page.VIEW, otherTop);

        // Road type and global settings, each a slider for quick changes with a text box for exact values, or a checkbox
        // for a toggle. Routing changes are previewed on the map's terrain right away, but only applied to roads on Apply.
        Map<Page, Integer> rowY = new EnumMap<>(Page.class);
        if (this.selected != null) {
            for (RoadSetting setting : RoadSetting.values()) {
                addSettingRow(setting, x, contentWidth, rowY);
            }
        }
        // The global page says where its settings are saved, since unlike the road type's, they're for every road type
        MultiLineTextWidget globalNote = note("yungsroads.screen.global.note", x, otherTop, contentWidth);
        addScrollingWidget(Page.GLOBAL, globalNote, otherTop);
        rowY.put(Page.GLOBAL, otherTop + globalNote.getHeight() + 6);
        for (GlobalSetting setting : GlobalSetting.values()) {
            addSettingRow(setting, x, contentWidth, rowY);
        }
        int placeRoadsY = rowY.get(Page.GLOBAL) + 2;
        addScrollingWidget(Page.GLOBAL, Checkbox.builder(Component.translatable(PLACE_ROADS_KEY), this.font)
                .pos(x, placeRoadsY)
                .maxWidth(contentWidth)
                .selected(this.pendingDebug.placeRoads)
                .tooltip(Tooltip.create(Component.translatable(PLACE_ROADS_KEY + ".description").append("\n")
                        .append(Component.translatable("yungsroads.screen.applied_with_apply"))))
                .onValueChange((box, value) -> this.pendingDebug.placeRoads = value)
                .build(), placeRoadsY);
        rowY.put(Page.GLOBAL, placeRoadsY + CHECKBOX_ROW_HEIGHT);
        rowY.forEach((page, y) -> this.formulaY.put(page, y + 6));

        // View options. These only affect what's drawn, so they take effect immediately, which the page says up front.
        MultiLineTextWidget viewNote = addPageWidget(Page.VIEW, note("yungsroads.screen.view.note", x, otherTop, contentWidth));
        int y = otherTop + viewNote.getHeight() + 6;
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
                .create(x, y, contentWidth, BUTTON_HEIGHT, Component.translatable("yungsroads.screen.view.terrain"),
                        (button, layer) -> RoadDebugClient.terrainLayer = layer);
        addPageWidget(Page.VIEW, terrainButton);
        y += ROW_HEIGHT;
        addPageWidget(Page.VIEW, Button.builder(Component.translatable("yungsroads.screen.view.center_map"), button -> this.map.recenter())
                .bounds(x, y, contentWidth, BUTTON_HEIGHT)
                .build());

        // The drawer's tabs sit over the map, so they take clicks before the map does, and are drawn after the map and
        // the drawer. Moved with the drawer's edge as it slides out.
        this.helpTab = addWidget(new DrawerTab(this.helpPage.tabName(), () -> this.drawer.isShowing(this.helpPage), true, this::toggleHelp));
        this.helpTab.setTooltip(Tooltip.create(Component.translatable("yungsroads.screen.help.tooltip")));
        this.inspectTab = addWidget(new DrawerTab(this.inspectPage.tabName(), () -> this.drawer.isShowing(this.inspectPage), false,
                () -> this.drawer.toggle(this.inspectPage, mapWidth())));
        this.inspectTab.setTooltip(Tooltip.create(Component.translatable("yungsroads.screen.inspect.tooltip")));
        positionDrawerTabs();
        this.map.setRectangle(mapWidth(), this.height - MARGIN * 2, PANEL_WIDTH + MARGIN, MARGIN);
        addRenderableWidget(this.map);

        updateVisibility();
    }

    /**
     * The road type tab's header: step 1 picks the road type and variant to start from, and step 2 picks which of its
     * settings are shown. Without a road network, a note says there's nothing to tune instead.
     */
    private void initRoadTypeHeader(int x, int contentWidth) {
        int y = TABS_BOTTOM + 4;
        if (this.selected == null || this.levelTypes == null) {
            int noteBottom = y + addTabWidget(Tab.ROAD_TYPE, note("yungsroads.screen.no_network", x, y, contentWidth)).getHeight();
            this.pageTop.put(Page.ROUTING, noteBottom);
            this.pageTop.put(Page.SHAPING, noteBottom);
            return;
        }

        this.steps.add(new Step(Tab.ROAD_TYPE, 1, Component.translatable("yungsroads.screen.step.start"), y));
        y += STEP_HEIGHT;
        ResourceLocation defaultId = this.levelTypes.defaultId();
        Component defaultName = Component.literal(defaultId == null ? "-" : RoadTypeNames.name(defaultId));
        List<ResourceLocation> typeIds = usableKeys().stream().map(VariantKey::typeId).distinct().toList();
        this.roadTypeDropdown = new Dropdown<>(x, y, contentWidth, BUTTON_HEIGHT, Component.translatable("yungsroads.glossary.road_type"),
                typeIds, this.selected.typeId, this::roadTypeLabel, typeId -> select(new VariantKey(typeId, 0)));
        this.roadTypeDropdown.setTooltip(Tooltip.create(Component.translatable("yungsroads.screen.road_type.tooltip", defaultName)));
        addTabWidget(Tab.ROAD_TYPE, this.roadTypeDropdown);
        y += BUTTON_HEIGHT + 2;

        RoadType type = this.baseTypes.get(this.selected.typeId);
        List<VariantKey> variants = new ArrayList<>();
        for (int variant = 0; variant < type.variants().size(); variant++) {
            variants.add(new VariantKey(this.selected.typeId, variant));
        }
        this.variantDropdown = new Dropdown<>(x, y, contentWidth, BUTTON_HEIGHT, Component.translatable("yungsroads.screen.variant.name"),
                variants, this.selected, this::variantLabel, this::select);
        this.variantDropdown.active = variants.size() > 1;
        this.variantDropdown.setTooltip(Tooltip.create(Component.translatable(variants.size() > 1
                ? "yungsroads.screen.variant.tooltip" : "yungsroads.screen.variant.only.tooltip")));
        addTabWidget(Tab.ROAD_TYPE, this.variantDropdown);
        y += BUTTON_HEIGHT + 6;

        this.steps.add(new Step(Tab.ROAD_TYPE, 2, Component.translatable("yungsroads.screen.step.edit"), y));
        y += STEP_HEIGHT;
        int subTabWidth = contentWidth / 2;
        for (Page page : List.of(Page.ROUTING, Page.SHAPING)) {
            int subTabX = x + (page == Page.ROUTING ? 0 : subTabWidth);
            addTabWidget(Tab.ROAD_TYPE, new PanelTab(subTabX, y, page == Page.ROUTING ? subTabWidth : contentWidth - subTabWidth,
                    SUB_TAB_HEIGHT, page.displayName, true, () -> this.roadTypePage == page, () -> selectPage(page)));
        }
        y += SUB_TAB_HEIGHT + 4;
        this.pageTop.put(Page.ROUTING, y);
        this.pageTop.put(Page.SHAPING, y);
    }

    /**
     * The action buttons at the bottom of the road type and global tabs: a row to preview changes in this world, and a
     * row to save them, each with its step label. The view tab has none, since its options take effect immediately.
     */
    private void initFooters(int x, int contentWidth) {
        int saveRowY = this.height - MARGIN - BUTTON_HEIGHT;
        int saveStepY = saveRowY - STEP_HEIGHT;
        int previewRowY = saveStepY - 4 - BUTTON_HEIGHT;
        int previewStepY = previewRowY - STEP_HEIGHT;
        this.footerTop = previewStepY;

        int thirdWidth = (contentWidth - 8) / 3;
        int halfWidth = (contentWidth - 4) / 2;
        for (Tab footerTab : List.of(Tab.ROAD_TYPE, Tab.GLOBAL)) {
            boolean roadType = footerTab == Tab.ROAD_TYPE;
            this.steps.add(new Step(footerTab, roadType ? 3 : 0, Component.translatable("yungsroads.screen.step.preview"), previewStepY));
            this.applyButtons.add(addTabWidget(footerTab, Button.builder(Component.translatable("yungsroads.screen.apply"), button -> apply())
                    .bounds(x, previewRowY, thirdWidth, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.apply.tooltip", RoadTuning.REGENERATE_RADIUS)))
                    .build()));
            this.revertButtons.add(addTabWidget(footerTab, Button.builder(Component.translatable("yungsroads.screen.revert"), button -> revert())
                    .bounds(x + thirdWidth + 4, previewRowY, thirdWidth, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("yungsroads.screen.revert.tooltip")))
                    .build()));
            Button reset = addTabWidget(footerTab, Button.builder(Component.translatable("yungsroads.screen.reset"),
                            button -> {
                                if (roadType) {
                                    resetVariant();
                                } else {
                                    resetGlobal();
                                }
                            })
                    .bounds(x + (thirdWidth + 4) * 2, previewRowY, contentWidth - (thirdWidth + 4) * 2, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(roadType ? "yungsroads.screen.reset.road_type.tooltip" : "yungsroads.screen.reset.global.tooltip")))
                    .build());
            reset.active = !roadType || this.selected != null;
        }

        this.steps.add(new Step(Tab.ROAD_TYPE, 4, Component.translatable("yungsroads.screen.step.export"), saveStepY));
        this.exportButtons.add(addTabWidget(Tab.ROAD_TYPE, Button.builder(Component.translatable("yungsroads.screen.copy_json"), button -> copyJson())
                .bounds(x, saveRowY, halfWidth, BUTTON_HEIGHT)
                .build()));
        this.exportButtons.add(addTabWidget(Tab.ROAD_TYPE, Button.builder(Component.translatable("yungsroads.screen.save_datapack"), button -> saveRoadType())
                .bounds(x + halfWidth + 4, saveRowY, contentWidth - halfWidth - 4, BUTTON_HEIGHT)
                .build()));

        this.steps.add(new Step(Tab.GLOBAL, 0, Component.translatable("yungsroads.screen.step.save_global"), saveStepY));
        this.saveConfigButton = addTabWidget(Tab.GLOBAL, Button.builder(Component.translatable("yungsroads.screen.save_config"), button -> saveGlobal())
                .bounds(x, saveRowY, contentWidth, BUTTON_HEIGHT)
                .build());
    }

    /** The road type picker's label for a road type, marking the road network's default and road types with edits. */
    private Component roadTypeLabel(ResourceLocation typeId) {
        MutableComponent label = Component.literal(RoadTypeNames.name(typeId));
        if (this.levelTypes != null && typeId.equals(this.levelTypes.defaultId())) {
            label.append(Component.literal(" ").append(Component.translatable("yungsroads.screen.road_type.default_marker")).withColor(NOTE_COLOR));
        }
        if (isTypeEdited(typeId)) {
            label.append(Component.literal(" ").append(Component.translatable("yungsroads.screen.edited_marker")).withColor(EDITED_COLOR));
        }
        return label;
    }

    /** The variant picker's label for a variant, with its weight, marking variants with edits. */
    private Component variantLabel(VariantKey key) {
        RoadType type = this.baseTypes.get(key.typeId);
        if (type.variants().size() == 1) {
            return Component.translatable("yungsroads.screen.variant.only");
        }
        MutableComponent label = Component.translatable("yungsroads.screen.variant", key.variant + 1, type.variants().size(),
                type.variants().get(key.variant).weight());
        if (isEdited(key)) {
            label.append(Component.literal(" ").append(Component.translatable("yungsroads.screen.edited_marker")).withColor(EDITED_COLOR));
        }
        return label;
    }

    private void select(VariantKey key) {
        this.selected = key;
        lastSelected = key;
        this.rebuildPending = true;
    }

    private void addSettingRow(ITunableSetting setting, int x, int contentWidth, Map<Page, Integer> rowY) {
        Page page = Page.of(setting);
        int y = rowY.getOrDefault(page, this.pageTop.get(page));
        rowY.put(page, y + ROW_HEIGHT);
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
            addScrollingWidget(page, checkbox, y);
            return;
        }
        int sliderWidth = contentWidth - VALUE_BOX_WIDTH - 4;
        EditBox box = new EditBox(this.font, x + sliderWidth + 4, y + 1, VALUE_BOX_WIDTH, 14, name);
        SettingSlider slider = new SettingSlider(x, y, sliderWidth, BUTTON_HEIGHT, setting, name, box);
        box.setMaxLength(12);
        box.setResponder(value -> {
            setPendingText(setting, value);
            Double parsed = parse(setting, value);
            box.setTextColor(parsed == null ? INVALID_TEXT_COLOR
                    : setting.format(parsed).equals(setting.format(resetValue(setting))) ? VALID_TEXT_COLOR : EDITED_COLOR);
            if (parsed != null) {
                slider.show(parsed);
            }
        });
        box.setValue(pendingText(setting));
        this.settingRows.put(setting, List.of(slider, box));
        addScrollingWidget(page, slider, y);
        addScrollingWidget(page, box, y + 1);
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
        addPageWidget(Page.VIEW, checkbox);
        return y + CHECKBOX_ROW_HEIGHT;
    }

    /** A note explaining a tab or page. */
    private MultiLineTextWidget note(String key, int x, int y, int width) {
        return new MultiLineTextWidget(x, y, Component.translatable(key), this.font)
                .setMaxWidth(width)
                .setColor(NOTE_COLOR);
    }

    private <T extends AbstractWidget> T addTabWidget(Tab tab, T widget) {
        addRenderableWidget(widget);
        this.tabWidgets.computeIfAbsent(tab, t -> new ArrayList<>()).add(widget);
        return widget;
    }

    private <T extends AbstractWidget> T addPageWidget(Page page, T widget) {
        addRenderableWidget(widget);
        this.pageWidgets.computeIfAbsent(page, p -> new ArrayList<>()).add(widget);
        return widget;
    }

    /**
     * Adds a widget to a scrolling page.
     *
     * @param y The widget's y when the page isn't scrolled.
     */
    private void addScrollingWidget(Page page, AbstractWidget widget, int y) {
        addPageWidget(page, widget);
        this.unscrolledY.put(widget, y);
    }

    /** The page shown: the road type tab's selected page, or the other tabs' only page. */
    private Page page() {
        return switch (this.tab) {
            case ROAD_TYPE -> this.roadTypePage;
            case GLOBAL -> Page.GLOBAL;
            case VIEW -> Page.VIEW;
        };
    }

    /**
     * The help page's section for the shown page. The road type tab of a dimension without a road network has nothing
     * to tune, so it opens at the section on road networks instead.
     */
    private HelpPage.Section helpSection() {
        return this.tab == Tab.ROAD_TYPE && this.selected == null ? HelpPage.Section.ROAD_NETWORKS : page().helpSection;
    }

    private void selectTab(Tab tab) {
        this.tab = tab;
        updateVisibility();
    }

    private void selectPage(Page page) {
        this.roadTypePage = page;
        updateVisibility();
    }

    /** Shows only the widgets of the selected tab and page. */
    private void updateVisibility() {
        Page page = page();
        this.tabWidgets.forEach((t, widgets) -> widgets.forEach(widget -> widget.visible = t == this.tab));
        this.pageWidgets.forEach((p, widgets) -> widgets.forEach(widget -> widget.visible = p == page));
        closeDropdowns();
        // A hidden text box must not keep receiving key presses
        setFocused(null);
        if (this.drawer.isShowing(this.helpPage)) {
            this.helpPage.showSection(helpSection());
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
        boolean busy = RoadTuning.isBusy();
        this.applyButtons.forEach(button -> button.active = !busy);
        boolean canRevert = RoadTuning.canRevert(level);
        this.revertButtons.forEach(button -> button.active = canRevert);

        // Exports are of the applied settings, so they match what's been previewed in the world
        boolean exportBlocked = this.selected != null && isUnapplied(this.selected.typeId);
        for (Button button : this.exportButtons) {
            button.active = this.selected != null && !exportBlocked && !busy;
        }
        if (this.selected != null && !Boolean.valueOf(exportBlocked).equals(this.exportBlocked)) {
            this.exportBlocked = exportBlocked;
            Component name = Component.literal(RoadTypeNames.name(this.selected.typeId));
            this.exportButtons.get(0).setTooltip(Tooltip.create(exportBlocked
                    ? Component.translatable("yungsroads.screen.apply_first.road_type")
                    : Component.translatable("yungsroads.screen.copy_json.tooltip", name)));
            this.exportButtons.get(1).setTooltip(Tooltip.create(exportBlocked
                    ? Component.translatable("yungsroads.screen.apply_first.road_type")
                    : Component.translatable("yungsroads.screen.save_datapack.tooltip", name, RoadTypeExport.DATAPACK_NAME)));
        }
        boolean saveConfigBlocked = isGlobalUnapplied();
        this.saveConfigButton.active = !saveConfigBlocked && !busy;
        if (!Boolean.valueOf(saveConfigBlocked).equals(this.saveConfigBlocked)) {
            this.saveConfigBlocked = saveConfigBlocked;
            this.saveConfigButton.setTooltip(Tooltip.create(Component.translatable(saveConfigBlocked
                    ? "yungsroads.screen.apply_first.global" : "yungsroads.screen.save_config.tooltip")));
        }
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
        if (this.tab != Tab.VIEW && hasUnappliedChanges()) {
            statusLines.add(Component.translatable("yungsroads.screen.unapplied").withColor(EDITED_COLOR));
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : statusLines) {
            if (!line.getString().isEmpty()) {
                wrapped.addAll(this.font.split(line, PANEL_WIDTH - MARGIN * 2));
            }
        }
        int footerTop = this.tab == Tab.VIEW ? this.height - MARGIN : this.footerTop;
        int statusTop = footerTop - 6 - wrapped.size() * 10;

        Page page = page();
        List<Component> formulas = formulas(page);
        if (page.scrolls()) {
            layOutScrollingPage(page, statusTop, formulas);
        }

        // Nothing under the drawer or an open dropdown list reacts to the mouse
        boolean overHelp = this.drawer.isMouseOver(mouseX, mouseY, this.width, mapWidth());
        Dropdown<?> openDropdown = openDropdown();
        boolean covered = overHelp || openDropdown != null && openDropdown.isMouseOverList(mouseX, mouseY);
        int widgetMouseX = covered ? -1 : mouseX;
        int widgetMouseY = covered ? -1 : mouseY;
        positionDrawerTabs();
        // The map doesn't inspect what's under the drawer's tabs
        this.map.setHoverInfoHidden(this.helpTab.isMouseOver(widgetMouseX, widgetMouseY) || this.inspectTab.isMouseOver(widgetMouseX, widgetMouseY));
        super.render(guiGraphics, widgetMouseX, widgetMouseY, partialTick);

        int y = statusTop;
        for (FormattedCharSequence line : wrapped) {
            guiGraphics.drawString(this.font, line, MARGIN, y, 0xFFC0C0C0);
            y += 10;
        }

        Component tooltip = null;
        if (page.scrolls()) {
            int formulaTop = this.formulaY.get(page) - this.scroll.getOrDefault(page, 0);
            tooltip = renderFormula(guiGraphics, widgetMouseX, widgetMouseY, formulaTop, statusTop, formulas);
            renderScrollbar(guiGraphics, statusTop, widgetMouseX, widgetMouseY);
        }
        if (tooltip == null) {
            tooltip = settingTooltip();
        }
        this.drawer.render(guiGraphics, this.width, this.height, mapWidth(), mouseX, mouseY);
        guiGraphics.pose().pushPose();
        // Above the drawer, which is drawn raised
        guiGraphics.pose().translate(0, 0, 300);
        this.helpTab.render(guiGraphics, openDropdown != null ? -1 : mouseX, mouseY, partialTick);
        this.inspectTab.render(guiGraphics, openDropdown != null ? -1 : mouseX, mouseY, partialTick);
        guiGraphics.pose().popPose();
        // Tooltips are drawn over the drawer, which is too wide to leave room beside it at large GUI scales.
        // They only show while hovering, so covering the page for a moment is better than hiding them.
        if (openDropdown != null) {
            openDropdown.renderList(guiGraphics, mouseX, mouseY);
        } else if (tooltip != null) {
            renderTooltipBesidePanel(guiGraphics, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Leave the world visible around the panel and map, rather than blurring it like most screens
        guiGraphics.fill(0, 0, PANEL_WIDTH, this.height, PANEL_COLOR);

        // As under vanilla's tabs, a line runs along the bottom of the tabs, except under the selected one
        int selectedLeft = MARGIN + this.tab.ordinal() * ((PANEL_WIDTH - MARGIN * 2) / Tab.values().length);
        int selectedRight = this.tab.ordinal() == Tab.values().length - 1
                ? PANEL_WIDTH - MARGIN
                : selectedLeft + (PANEL_WIDTH - MARGIN * 2) / Tab.values().length;
        RenderSystem.enableBlend();
        guiGraphics.blit(Screen.HEADER_SEPARATOR, 0, TABS_BOTTOM - 2, 0.0F, 0.0F, selectedLeft, 2, 32, 2);
        guiGraphics.blit(Screen.HEADER_SEPARATOR, selectedRight, TABS_BOTTOM - 2, 0.0F, 0.0F, PANEL_WIDTH - selectedRight, 2, 32, 2);
        RenderSystem.disableBlend();

        for (Step step : this.steps) {
            if (step.tab == this.tab) {
                renderStep(guiGraphics, step);
            }
        }
        if (this.tab == Tab.ROAD_TYPE && this.selected != null) {
            // Underlines the sub-tabs, so the selected one's underline reads as part of a row
            int y = this.pageTop.get(Page.ROUTING) - 5;
            guiGraphics.fill(MARGIN, y, PANEL_WIDTH - MARGIN, y + 1, DIVIDER_COLOR);
        }
        if (this.tab != Tab.VIEW) {
            guiGraphics.fill(MARGIN, this.footerTop - 3, PANEL_WIDTH - MARGIN, this.footerTop - 2, DIVIDER_COLOR);
        }
    }

    /** Draws a step's label, after a badge with its number if it has one. */
    private void renderStep(GuiGraphics guiGraphics, Step step) {
        int x = MARGIN;
        if (step.number > 0) {
            guiGraphics.fill(x, step.y, x + 9, step.y + 9, STEP_BADGE_COLOR);
            String number = String.valueOf(step.number);
            guiGraphics.drawString(this.font, number, x + 5 - this.font.width(number) / 2, step.y + 1, 0xFFFFFFFF, false);
            x += 13;
        }
        guiGraphics.drawString(this.font, step.text, x, step.y + 1, STEP_COLOR, false);
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

    /** The width of the map, which fills the screen right of the settings panel. The drawer slides over it. */
    private int mapWidth() {
        return this.width - PANEL_WIDTH - MARGIN * 2;
    }

    /**
     * Stacks the drawer's tabs at its left edge, which is the screen's right edge while it's closed, centered vertically
     * where they're seen whatever the map shows at its corners.
     */
    private void positionDrawerTabs() {
        int x = this.drawer.left(this.width, mapWidth()) - DrawerTab.WIDTH;
        int y = (this.height - this.helpTab.getHeight() - DRAWER_TAB_GAP - this.inspectTab.getHeight()) / 2;
        this.helpTab.setPosition(x, y);
        this.inspectTab.setPosition(x, y + this.helpTab.getHeight() + DRAWER_TAB_GAP);
    }

    private void toggleHelp() {
        boolean opening = !this.drawer.isShowing(this.helpPage);
        this.drawer.toggle(this.helpPage, mapWidth());
        if (opening) {
            // The first time, help starts at the top, introducing the screen, rather than at the shown tab's section
            this.helpPage.showSection(DrawerTab.hasHelpBeenOpened() ? helpSection() : HelpPage.Section.OVERVIEW);
            DrawerTab.markHelpOpened();
        }
    }

    /** Shows the inspect page, such as when a spot is pinned on the map. */
    private void showInspectPage() {
        this.drawer.open(this.inspectPage, mapWidth());
    }

    /**
     * The formulas explaining how a page's settings are used, with the entered values filled in. The road type pages
     * have none without a road type to tune.
     */
    private List<Component> formulas(Page page) {
        if (page == Page.VIEW || page.tab == Tab.ROAD_TYPE && this.selected == null) {
            return List.of();
        }
        RoadSettings settings = page.tab == Tab.ROAD_TYPE ? previewSettings() : null;
        ConfigModule.Advanced global = previewGlobal();
        ToDoubleFunction<ITunableSetting> valueOf = setting -> setting instanceof RoadSetting roadSetting
                ? roadSetting.get(settings)
                : ((GlobalSetting) setting).get(global);
        return switch (page) {
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
     * Keeps a page's scroll within its settings and formulas, moves its widgets to match, and shows only the widgets
     * that fit between the page's top and the given bottom, so none are shown cut off.
     */
    private void layOutScrollingPage(Page page, int bottom, List<Component> formulas) {
        int top = this.pageTop.get(page);
        int contentBottom = this.formulaY.getOrDefault(page, top) + formulasHeight(formulas);
        // Scrolling is by whole rows, so the top row always sits right at the page's top instead of being hidden
        int max = Mth.positiveCeilDiv(Math.max(0, contentBottom - bottom), ROW_HEIGHT) * ROW_HEIGHT;
        int offset = Mth.clamp(this.scroll.getOrDefault(page, 0), 0, max);
        this.maxScroll.put(page, max);
        this.scroll.put(page, offset);
        for (AbstractWidget widget : this.pageWidgets.getOrDefault(page, List.of())) {
            int y = this.unscrolledY.get(widget) - offset;
            widget.setY(y);
            widget.visible = y >= top && y + widget.getHeight() <= bottom;
            // A hidden text box must not keep receiving key presses
            if (!widget.visible && getFocused() == widget) {
                setFocused(null);
            }
        }
    }

    /** Lays out and draws the scroll bar at the panel's right edge, shown if the shown page's content doesn't fit. */
    private void renderScrollbar(GuiGraphics guiGraphics, int bottom, int mouseX, int mouseY) {
        Page page = page();
        int top = this.pageTop.get(page);
        this.scrollBar.layOut(PANEL_WIDTH - 3, top, bottom, bottom - top, bottom - top + this.maxScroll.getOrDefault(page, 0));
        this.scrollBar.render(guiGraphics, this.scroll.getOrDefault(page, 0), mouseX, mouseY);
    }

    @Nullable
    private Dropdown<?> openDropdown() {
        if (this.roadTypeDropdown != null && this.roadTypeDropdown.isOpen()) {
            return this.roadTypeDropdown;
        }
        if (this.variantDropdown != null && this.variantDropdown.isOpen()) {
            return this.variantDropdown;
        }
        return null;
    }

    private void closeDropdowns() {
        if (this.roadTypeDropdown != null) {
            this.roadTypeDropdown.close();
        }
        if (this.variantDropdown != null) {
            this.variantDropdown.close();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // An open dropdown list covers whatever is under it, so it takes the click, even one outside it to close it
        Dropdown<?> openDropdown = openDropdown();
        if (openDropdown != null) {
            return openDropdown.listClicked(mouseX, mouseY);
        }
        // The drawer's tabs are drawn over everything, so they take the click first
        if (this.helpTab.mouseClicked(mouseX, mouseY, button) || this.inspectTab.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.drawer.isMouseOver(mouseX, mouseY, this.width, mapWidth())) {
            // Nothing under the drawer may be clicked
            setFocused(null);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.drawer.mouseClicked(mouseX, mouseY);
            }
            return true;
        }
        Page page = page();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && page.scrolls()) {
            int offset = this.scrollBar.mouseClicked(mouseX, mouseY, this.scroll.getOrDefault(page, 0));
            if (offset >= 0) {
                this.scroll.put(page, offset);
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        Dropdown<?> openDropdown = openDropdown();
        if (openDropdown != null && openDropdown.listDragged(mouseY)) {
            return true;
        }
        if (this.drawer.mouseDragged(mouseY)) {
            return true;
        }
        if (this.scrollBar.isDragging()) {
            this.scroll.put(page(), this.scrollBar.mouseDragged(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Dropdown<?> openDropdown = openDropdown();
        if (openDropdown != null) {
            openDropdown.listReleased();
        }
        this.drawer.mouseReleased();
        this.scrollBar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Dropdown<?> openDropdown = openDropdown();
        if (openDropdown != null) {
            return openDropdown.listScrolled(mouseX, mouseY, scrollY) || mouseX < PANEL_WIDTH;
        }
        if (this.drawer.isMouseOver(mouseX, mouseY, this.width, mapWidth())) {
            this.drawer.mouseScrolled(scrollY);
            return true;
        }
        Page page = page();
        if (mouseX < PANEL_WIDTH && page.scrolls()) {
            int offset = this.scroll.getOrDefault(page, 0) - (int) Math.round(scrollY * ROW_HEIGHT);
            this.scroll.put(page, Mth.clamp(offset, 0, this.maxScroll.getOrDefault(page, 0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * The hovered setting's description, its baseline value, and the definitions of its terms, or null if no setting
     * is hovered. Hidden while dragging, so the map's preview stays visible.
     */
    @Nullable
    private Component settingTooltip() {
        if (this.minecraft == null || this.minecraft.mouseHandler.isLeftPressed()) {
            return null;
        }
        for (Map.Entry<ITunableSetting, List<AbstractWidget>> row : this.settingRows.entrySet()) {
            // Hidden widgets keep the hover state they had when last rendered
            if (row.getValue().stream().noneMatch(widget -> widget.visible && widget.isHovered())) {
                continue;
            }
            ITunableSetting setting = row.getKey();
            Language language = Language.getInstance();
            String details = setting.isToggle()
                    ? "\n" + language.getOrDefault("yungsroads.screen.default").formatted(onOff(language, setting.defaultValue()))
                    : "\n" + language.getOrDefault("yungsroads.screen.range").formatted(setting.format(setting.min()), setting.format(setting.max()))
                    + "\n" + language.getOrDefault("yungsroads.screen.default").formatted(setting.format(setting.defaultValue()));
            if (setting instanceof RoadSetting && this.selected != null) {
                double baseline = resetValue(setting);
                details += "\n" + language.getOrDefault("yungsroads.screen.baseline").formatted(
                        RoadTypeNames.name(this.selected.typeId), setting.isToggle() ? onOff(language, baseline) : setting.format(baseline));
            }
            return RoutingGlossary.withDefinitions(language.getOrDefault(setting.descriptionKey()) + details, this::tooltipFits);
        }
        return null;
    }

    private static String onOff(Language language, double value) {
        return language.getOrDefault(value != 0 ? "yungsroads.screen.on" : "yungsroads.screen.off");
    }

    /**
     * Renders a tooltip just right of the panel at the mouse's height, kept on screen. Unlike vanilla tooltip
     * placement, this never covers the panel, so the settings and formulas stay readable.
     */
    private void renderTooltipBesidePanel(GuiGraphics guiGraphics, Component text, int mouseX, int mouseY) {
        guiGraphics.renderTooltip(this.font, this.font.split(text, TOOLTIP_WIDTH),
                (screenWidth, screenHeight, x, y, width, height) ->
                        new Vector2i(PANEL_WIDTH + 12, Mth.clamp(y - 12, TOOLTIP_MARGIN, screenHeight - height - TOOLTIP_MARGIN)),
                mouseX, mouseY);
    }

    /**
     * Whether a tooltip of the given text fits on screen. Vanilla draws each line 10 pixels tall, with 2 fewer for the
     * last, inside a 4 pixel frame.
     */
    private boolean tooltipFits(Component text) {
        int height = this.font.split(text, TOOLTIP_WIDTH).size() * 10 - 2 + 8;
        return height <= this.height - TOOLTIP_MARGIN * 2;
    }

    /**
     * Explains how a page's settings are used, with the entered values filled in. Highlighted words and values show
     * their definitions when hovered. Formulas scrolled above the page's top or that don't fit above the status text
     * are left out whole, so none are shown cut off.
     *
     * @param top Where the first formula starts, which is above the page's top when the page is scrolled.
     * @return The definition of the highlighted word or value under the mouse, or null if there isn't one.
     */
    @Nullable
    private Component renderFormula(GuiGraphics guiGraphics, int mouseX, int mouseY, int top, int bottom, List<Component> paragraphs) {
        int pageTop = this.pageTop.get(page());
        int x = MARGIN;
        int y = top;
        Style hovered = null;
        for (Component paragraph : paragraphs) {
            List<FormattedCharSequence> lines = this.font.split(paragraph, PANEL_WIDTH - MARGIN * 2);
            if (y + lines.size() * (this.font.lineHeight + 1) - 1 > bottom) {
                break;
            }
            if (y < pageTop) {
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
        return hovered == null || hovered.getHoverEvent() == null ? null : hovered.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape closes an open dropdown list or the drawer before the screen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && openDropdown() != null) {
            closeDropdowns();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.drawer.isOpen()) {
            this.drawer.close();
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

    /** Resets the shown variant's settings to the road type as the level loaded it. */
    private void resetVariant() {
        if (this.selected == null || this.levelTypes == null) {
            return;
        }
        RoadSettings baseline = baselineSettings(this.selected);
        Map<RoadSetting, String> text = this.pendingTypeText.get(this.selected);
        for (RoadSetting setting : RoadSetting.values()) {
            text.put(setting, setting.format(setting.get(baseline)));
        }
        rebuildWidgets();
    }

    /** Resets the global settings, including whether roads are placed, to their defaults. */
    private void resetGlobal() {
        ConfigModule.Advanced defaults = new ConfigModule.Advanced();
        for (GlobalSetting setting : GlobalSetting.values()) {
            this.pendingGlobalText.put(setting, setting.format(setting.get(defaults)));
        }
        boolean f3Info = this.pendingDebug.enableExtraDebugF3Info;
        this.pendingDebug = new ConfigModule.Debug();
        this.pendingDebug.enableExtraDebugF3Info = f3Info;
        rebuildWidgets();
    }

    /** Copies the shown road type, as applied, to the clipboard as a datapack file. */
    private void copyJson() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level == null || this.selected == null || this.levelTypes == null || this.minecraft == null) {
            return;
        }
        RoadType type = this.levelTypes.current().get(this.selected.typeId);
        this.minecraft.keyboardHandler.setClipboard(RoadTypeExport.toJson(type, level.registryAccess()));
        showLocalStatus(Component.translatable("yungsroads.screen.copied", this.selected.typeId.toString()));
    }

    /** Saves the shown road type, as applied, to the world's tuned road type datapack. */
    private void saveRoadType() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level != null && this.selected != null) {
            ResourceLocation typeId = this.selected.typeId;
            level.getServer().execute(() -> RoadTuning.saveRoadType(level, typeId));
        }
    }

    /** Saves the applied global settings to the config file. */
    private void saveGlobal() {
        ServerLevel level = RoadDebugClient.serverLevel();
        if (level != null) {
            level.getServer().execute(RoadTuning::saveGlobal);
        }
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

    /** The road type variants this dimension's roads can get, in the pickers' order. */
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

    /** The variant's settings as the level loaded them, which Reset restores. */
    private RoadSettings baselineSettings(VariantKey key) {
        return this.levelTypes.loaded().get(key.typeId).variants().get(key.variant).settings();
    }

    /** The value Reset restores a setting to: the shown variant's baseline for road type settings, or the default. */
    private double resetValue(ITunableSetting setting) {
        return setting instanceof RoadSetting roadSetting && this.selected != null
                ? roadSetting.get(baselineSettings(this.selected))
                : setting.defaultValue();
    }

    /** Whether the entered values are all valid and the same as the given settings, as each setting shows them. */
    private boolean matches(Map<RoadSetting, String> text, RoadSettings settings) {
        for (RoadSetting setting : RoadSetting.values()) {
            Double value = parse(setting, text.get(setting));
            if (value == null || !setting.format(value).equals(setting.format(setting.get(settings)))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the variant's entered values differ from the road type as the level loaded it. */
    private boolean isEdited(VariantKey key) {
        return this.levelTypes != null && !matches(this.pendingTypeText.get(key), baselineSettings(key));
    }

    /** Whether any of the road type's variants differ from the road type as the level loaded it. */
    private boolean isTypeEdited(ResourceLocation typeId) {
        RoadType type = this.baseTypes.get(typeId);
        for (int variant = 0; variant < type.variants().size(); variant++) {
            if (isEdited(new VariantKey(typeId, variant))) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the road type's variants have entered values that haven't been applied. */
    private boolean isUnapplied(ResourceLocation typeId) {
        RoadType applied = this.levelTypes == null ? null : this.levelTypes.current().get(typeId);
        if (applied == null) {
            return false;
        }
        for (int variant = 0; variant < applied.variants().size(); variant++) {
            Map<RoadSetting, String> text = this.pendingTypeText.get(new VariantKey(typeId, variant));
            if (text != null && !matches(text, applied.variants().get(variant).settings())) {
                return true;
            }
        }
        return false;
    }

    /** Whether any global setting, or whether roads are placed, has been changed without being applied. */
    private boolean isGlobalUnapplied() {
        for (GlobalSetting setting : GlobalSetting.values()) {
            Double value = parse(setting, this.pendingGlobalText.get(setting));
            if (value == null || !setting.format(value).equals(setting.format(setting.get(YungsRoadsCommon.CONFIG.advanced)))) {
                return true;
            }
        }
        return this.pendingDebug.placeRoads != YungsRoadsCommon.CONFIG.debug.placeRoads;
    }

    /** Whether anything on the road type or global tabs has been changed without being applied. */
    private boolean hasUnappliedChanges() {
        return isGlobalUnapplied() || this.baseTypes.keySet().stream().anyMatch(this::isUnapplied);
    }

    /** The name of a road type variant, as shown on the map and in messages. */
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
