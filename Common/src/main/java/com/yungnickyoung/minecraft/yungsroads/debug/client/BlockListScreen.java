package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A dialog over the tuning screen for editing one of a road type's lists of blocks: either a weighted mix, such as a
 * surface's path blocks, where each block has a chance and a default block takes the rest, or the ground blocks a
 * surface replaces.
 * <p>
 * Blocks are typed as ids, with suggestions from the block registry. A mix's blocks may give block state properties as
 * commands do, such as {@code oak_slab[type=top]}. Ground blocks match any state of a block, and may instead be block
 * tags, such as {@code #minecraft:dirt}. Nothing changes until Done is pressed, which is only allowed while every row
 * is valid. The edits are kept in this screen, not its widgets, so they survive the window being resized.
 */
final class BlockListScreen extends Screen {
    private static final int MAX_WIDTH = 300;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int BOX_HEIGHT = 16;
    private static final int ICON_WIDTH = 20;
    private static final int CHANCE_BOX_WIDTH = 40;
    private static final int REMOVE_WIDTH = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BAR_HEIGHT = 8;
    /** The most suggestions shown at once. The list scrolls through the rest. */
    private static final int VISIBLE_SUGGESTIONS = 8;
    /** The most of a block tag's blocks listed when its icon is hovered. */
    private static final int MAX_TAG_BLOCKS_SHOWN = 12;
    private static final int SUGGESTION_HEIGHT = 11;
    /** The fewest rows the list shows, scrolling if there are more than fit. */
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int OVERLAY_COLOR = 0xB0000000;
    private static final int BACKGROUND_COLOR = 0xF0181818;
    private static final int BORDER_COLOR = 0xFF505050;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int NOTE_COLOR = 0xFFA0A0A0;
    private static final int INVALID_COLOR = 0xFFFF5050;
    /** Lighter than the block boxes the suggestions cover, so the two can't be mistaken for each other. */
    private static final int SUGGESTION_BACKGROUND_COLOR = 0xFF2C2C2C;
    private static final int SUGGESTION_BORDER_COLOR = 0xFF808080;
    private static final int SUGGESTION_SELECTED_COLOR = 0xFF3A6EA5;
    /** A block without a map color, such as glass, is shown in the proportion bar in this color. */
    private static final int NO_MAP_COLOR = 0xFF808080;

    /** One row of the list, as typed. */
    private static final class Row {
        String block;
        String chance;
        /** The entry the row was loaded from, whose condition is kept, since conditions can only be set in files. */
        @Nullable
        final BlockStateRandomizer.Entry source;

        Row(String block, String chance, @Nullable BlockStateRandomizer.Entry source) {
            this.block = block;
            this.chance = chance;
            this.source = source;
        }
    }

    /** A row's widgets, laid out as the list scrolls. */
    private record RowWidgets(Row row, EditBox block, @Nullable EditBox chance, Button remove) {
    }

    private final Screen parent;
    private final Component description;
    /** Whether each block has a chance, with a default block taking the rest, rather than being ground blocks. */
    private final boolean weighted;
    /** For a list that can be left out, the label of the option to leave it out, or null if it's required. */
    @Nullable
    private final Component noneLabel;
    @Nullable
    private final Consumer<Optional<BlockStateRandomizer>> onWeightedDone;
    @Nullable
    private final Consumer<List<ExtraCodecs.TagOrElementLocation>> onGroundDone;

    private final List<Row> rows = new ArrayList<>();
    private String defaultBlock = "";
    private boolean none;
    private int scroll = 0;

    private final List<RowWidgets> rowWidgets = new ArrayList<>();
    @Nullable
    private EditBox defaultBox;
    private Button doneButton;
    private Button addButton;
    @Nullable
    private Checkbox noneCheckbox;
    private final ScrollBar scrollBar = new ScrollBar(ROW_HEIGHT);
    private int left;
    private int top;
    private int dialogWidth;
    private int dialogHeight;
    private int rowsTop;
    private int rowsHeight;
    private List<FormattedCharSequence> descriptionLines = List.of();

    /** The block box suggestions are shown for, the text they were found for, and the suggestions. */
    @Nullable
    private EditBox suggestionBox;
    private String suggestionText = "";
    private List<String> suggestions = List.of();
    private int suggestionIndex = 0;
    /** The first suggestion shown, as the list scrolls. */
    private int suggestionScroll = 0;
    private final ScrollBar suggestionScrollBar = new ScrollBar(1);
    /**
     * Whether the suggestions are hidden until the text changes: after one is picked, after a click outside them or
     * Escape, and when a box is first focused, so they only open while typing.
     */
    private boolean suggestionsDismissed = false;

    private BlockListScreen(Screen parent, Component title, Component description, boolean weighted, @Nullable Component noneLabel,
                            @Nullable Consumer<Optional<BlockStateRandomizer>> onWeightedDone,
                            @Nullable Consumer<List<ExtraCodecs.TagOrElementLocation>> onGroundDone) {
        super(title);
        this.parent = parent;
        this.description = description;
        this.weighted = weighted;
        this.noneLabel = noneLabel;
        this.onWeightedDone = onWeightedDone;
        this.onGroundDone = onGroundDone;
    }

    /**
     * A dialog for a weighted mix of blocks.
     *
     * @param noneLabel The label of the option to leave the mix out, or null if it's required.
     * @param fallback  The default block offered if the mix is left out and then added.
     */
    static BlockListScreen weighted(Screen parent, Component title, Component description, @Nullable Component noneLabel,
                                    Optional<BlockStateRandomizer> value, BlockState fallback, Consumer<Optional<BlockStateRandomizer>> onDone) {
        BlockListScreen screen = new BlockListScreen(parent, title, description, true, noneLabel, onDone, null);
        screen.none = value.isEmpty();
        screen.defaultBlock = text(value.map(BlockStateRandomizer::getDefaultBlockState).orElse(fallback));
        value.ifPresent(randomizer -> randomizer.getEntries().forEach(entry ->
                screen.rows.add(new Row(text(entry.blockState), percent(entry.probability), entry))));
        return screen;
    }

    /** A dialog for the ground blocks a surface replaces, as block ids and block tags. */
    static BlockListScreen ground(Screen parent, Component title, Component description, List<ExtraCodecs.TagOrElementLocation> value,
                                  Consumer<List<ExtraCodecs.TagOrElementLocation>> onDone) {
        BlockListScreen screen = new BlockListScreen(parent, title, description, false, null, null, onDone);
        value.forEach(target -> screen.rows.add(new Row(text(target), "", null)));
        return screen;
    }

    /** How a ground block is typed: its id, or a tag's id after a {@code #}, without the namespace if it's vanilla's. */
    static String text(ExtraCodecs.TagOrElementLocation target) {
        return (target.tag() ? "#" : "") + shortId(target.id());
    }

    private static String shortId(ResourceLocation id) {
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    /** The ground block or block tag typed, or null if there's no such block or tag. */
    @Nullable
    static ExtraCodecs.TagOrElementLocation parseTarget(String text) {
        String trimmed = text.trim();
        boolean tag = trimmed.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? trimmed.substring(1) : trimmed);
        if (id == null || id.getPath().isEmpty()) {
            return null;
        }
        boolean exists = tag
                ? BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).isPresent()
                : BuiltInRegistries.BLOCK.containsKey(id);
        return exists ? new ExtraCodecs.TagOrElementLocation(id, tag) : null;
    }

    /** The blocks a ground block or block tag matches, in the registry's order for a tag. */
    static List<Block> blocks(ExtraCodecs.TagOrElementLocation target) {
        if (!target.tag()) {
            return BuiltInRegistries.BLOCK.getOptional(target.id()).map(List::of).orElse(List.of());
        }
        return BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, target.id()))
                .map(set -> set.stream().map(Holder::value).toList())
                .orElse(List.of());
    }

    /** How a block state is typed: its id, without the namespace if it's vanilla's, and any properties changed from the block's default. */
    static String text(BlockState state) {
        String name = shortId(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        BlockState defaultState = state.getBlock().defaultBlockState();
        String properties = state.getProperties().stream()
                .filter(property -> !state.getValue(property).equals(defaultState.getValue(property)))
                .map(property -> property.getName() + "=" + valueName(property, state))
                .collect(Collectors.joining(","));
        return properties.isEmpty() ? name : name + "[" + properties + "]";
    }

    private static <T extends Comparable<T>> String valueName(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    /** A chance as a percentage, without trailing zeros. */
    static String percent(double probability) {
        return new BigDecimal(probability * 100).round(new MathContext(4)).stripTrailingZeros().toPlainString();
    }

    /** The block state typed, or null if it isn't a block. */
    @Nullable
    static BlockState parse(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text.trim(), false).blockState();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    @Override
    protected void init() {
        if (this.parent.width != this.width || this.parent.height != this.height) {
            // The screen behind is drawn too, so it's laid out for the same size
            this.parent.init(this.minecraft, this.width, this.height);
        }
        this.rowWidgets.clear();
        this.dialogWidth = Math.min(MAX_WIDTH, this.width - 20);
        int innerWidth = this.dialogWidth - PADDING * 2;
        this.descriptionLines = this.font.split(this.description, innerWidth);

        // Everything but the rows has a fixed height, and the rows get what's left, scrolling if they don't all fit
        int fixedHeight = PADDING + 10 + 4 + this.descriptionLines.size() * 10 + 6
                + (this.noneLabel != null ? 22 : 0)
                + 12 // column labels
                + 4 + BUTTON_HEIGHT // add button
                + (this.weighted ? 4 + 10 + ROW_HEIGHT + 4 + BAR_HEIGHT : 0)
                + 6 + 10 // error
                + 6 + BUTTON_HEIGHT + PADDING;
        int maxRowsHeight = Math.max(MIN_VISIBLE_ROWS * ROW_HEIGHT, this.height - 20 - fixedHeight);
        this.rowsHeight = Mth.clamp(this.rows.size() * ROW_HEIGHT, MIN_VISIBLE_ROWS * ROW_HEIGHT, maxRowsHeight);
        this.dialogHeight = fixedHeight + this.rowsHeight;
        this.left = (this.width - this.dialogWidth) / 2;
        this.top = Math.max(10, (this.height - this.dialogHeight) / 2);
        int x = this.left + PADDING;
        int y = this.top + PADDING + 10 + 4 + this.descriptionLines.size() * 10 + 6;

        if (this.noneLabel != null) {
            this.noneCheckbox = addRenderableWidget(Checkbox.builder(this.noneLabel, this.font)
                    .pos(x, y)
                    .maxWidth(innerWidth)
                    .selected(this.none)
                    .onValueChange((box, selected) -> {
                        this.none = selected;
                        updateState();
                    })
                    .build());
            y += 22;
        }
        y += 12;
        this.rowsTop = y;
        for (Row row : this.rows) {
            addRow(row, x, innerWidth);
        }
        y += this.rowsHeight + 4;
        this.addButton = addRenderableWidget(Button.builder(Component.translatable("yungsroads.blocks.add"), button -> addBlock())
                .bounds(x, y, Math.min(100, innerWidth), BUTTON_HEIGHT)
                .build());
        y += BUTTON_HEIGHT;
        if (this.weighted) {
            y += 4 + 10;
            this.defaultBox = blockBox(x + ICON_WIDTH, y + 2, innerWidth - ICON_WIDTH - REMOVE_WIDTH - 4, this.defaultBlock, text -> this.defaultBlock = text);
        }

        int buttonWidth = (innerWidth - 4) / 2;
        int buttonY = this.top + this.dialogHeight - PADDING - BUTTON_HEIGHT;
        this.doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> done())
                .bounds(x, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + buttonWidth + 4, buttonY, innerWidth - buttonWidth - 4, BUTTON_HEIGHT)
                .build());
        updateState();
    }

    private void addRow(Row row, int x, int innerWidth) {
        int removeX = x + innerWidth - REMOVE_WIDTH;
        int chanceX = removeX - 4 - CHANCE_BOX_WIDTH;
        int blockWidth = (this.weighted ? chanceX : removeX) - 4 - (x + ICON_WIDTH);
        EditBox block = blockBox(x + ICON_WIDTH, 0, blockWidth, row.block, text -> row.block = text);
        EditBox chance = null;
        if (this.weighted) {
            chance = addRenderableWidget(new EditBox(this.font, chanceX, 0, CHANCE_BOX_WIDTH, BOX_HEIGHT, Component.translatable("yungsroads.blocks.chance")));
            chance.setMaxLength(8);
            chance.setValue(row.chance);
            chance.setResponder(text -> {
                row.chance = text;
                updateState();
            });
        }
        Button remove = addRenderableWidget(Button.builder(Component.literal("✕"), button -> {
                    this.rows.remove(row);
                    rebuildWidgets();
                })
                .bounds(removeX, 0, REMOVE_WIDTH, BOX_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("yungsroads.blocks.remove")))
                .build());
        this.rowWidgets.add(new RowWidgets(row, block, chance, remove));
    }

    private EditBox blockBox(int x, int y, int width, String value, Consumer<String> onChange) {
        EditBox box = addRenderableWidget(new EditBox(this.font, x, y, width, BOX_HEIGHT, Component.translatable("yungsroads.blocks.block")));
        box.setMaxLength(256);
        box.setValue(value);
        box.setResponder(text -> {
            onChange.accept(text);
            updateState();
        });
        return box;
    }

    private void addBlock() {
        this.rows.add(new Row("", this.weighted ? "10" : "", null));
        this.scroll = Integer.MAX_VALUE;
        rebuildWidgets();
        // Ready to type the new block's id
        RowWidgets added = this.rowWidgets.get(this.rowWidgets.size() - 1);
        setFocused(added.block);
    }

    /** Colors the boxes by whether they're valid, and allows Done only while everything is. */
    private void updateState() {
        if (this.doneButton == null) {
            return;
        }
        for (RowWidgets widgets : this.rowWidgets) {
            widgets.block.setTextColor(isValidBlock(widgets.row.block) ? TEXT_COLOR : INVALID_COLOR);
            if (widgets.chance != null) {
                widgets.chance.setTextColor(parseChance(widgets.row.chance) == null ? INVALID_COLOR : TEXT_COLOR);
            }
            widgets.block.active = !this.none;
            if (widgets.chance != null) {
                widgets.chance.active = !this.none;
            }
            widgets.remove.active = !this.none;
        }
        if (this.defaultBox != null) {
            this.defaultBox.setTextColor(parse(this.defaultBlock) == null ? INVALID_COLOR : TEXT_COLOR);
            this.defaultBox.active = !this.none;
        }
        this.addButton.active = !this.none;
        this.doneButton.active = error() == null;
    }

    /** Whether a row's text is a block, or for ground blocks, a block or block tag. */
    private boolean isValidBlock(String text) {
        return this.weighted ? parse(text) != null : parseTarget(text) != null;
    }

    /** The chance typed, as a probability, or null if it isn't a number from 0 to 100. */
    @Nullable
    private static Double parseChance(String text) {
        try {
            double percent = Double.parseDouble(text.trim());
            return percent >= 0 && percent <= 100 ? percent / 100 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The total chance of the listed blocks, ignoring any that aren't valid. */
    private double totalChance() {
        double total = 0;
        for (Row row : this.rows) {
            Double chance = parseChance(row.chance);
            total += chance == null ? 0 : chance;
        }
        return total;
    }

    /** What's wrong with the list as typed, or null if it can be saved. */
    @Nullable
    private Component error() {
        if (this.none) {
            return null;
        }
        if (!this.weighted && this.rows.isEmpty()) {
            return Component.translatable("yungsroads.blocks.error.empty");
        }
        Set<String> seen = new HashSet<>();
        for (Row row : this.rows) {
            String typed = row.block.isBlank() ? "?" : row.block.trim();
            String shown;
            if (this.weighted) {
                BlockState state = parse(row.block);
                if (state == null) {
                    return Component.translatable("yungsroads.blocks.error.unknown", typed);
                }
                shown = text(state);
            } else {
                ExtraCodecs.TagOrElementLocation target = parseTarget(row.block);
                if (target == null) {
                    return Component.translatable(typed.startsWith("#") ? "yungsroads.blocks.error.unknown_tag" : "yungsroads.blocks.error.unknown", typed);
                }
                shown = text(target);
            }
            if (!seen.add(shown)) {
                return Component.translatable("yungsroads.blocks.error.duplicate", shown);
            }
            if (this.weighted && parseChance(row.chance) == null) {
                return Component.translatable("yungsroads.blocks.error.chance", shown);
            }
        }
        if (this.weighted) {
            if (parse(this.defaultBlock) == null) {
                return Component.translatable("yungsroads.blocks.error.unknown", this.defaultBlock.isBlank() ? "?" : this.defaultBlock.trim());
            }
            if (totalChance() > 1 + 1e-6) {
                return Component.translatable("yungsroads.blocks.error.total", percent(totalChance()));
            }
        }
        return null;
    }

    private void done() {
        if (error() != null) {
            return;
        }
        if (this.weighted) {
            Optional<BlockStateRandomizer> result = Optional.empty();
            if (!this.none) {
                List<BlockStateRandomizer.Entry> entries = new ArrayList<>();
                for (Row row : this.rows) {
                    float probability = (float) (double) parseChance(row.chance);
                    // The two-argument constructor leaves the condition null, which can't be encoded
                    entries.add(new BlockStateRandomizer.Entry(parse(row.block), probability,
                            row.source == null ? Optional.empty() : row.source.condition));
                }
                result = Optional.of(new BlockStateRandomizer(entries, parse(this.defaultBlock)));
            }
            this.onWeightedDone.accept(result);
        } else {
            this.onGroundDone.accept(this.rows.stream().map(row -> parseTarget(row.block)).toList());
        }
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        // The tuning screen behind keeps the server running, which re-places roads
        return false;
    }

    /** Moves the rows' widgets to the list's scroll, showing only those that fit in it. */
    private void layOutRows() {
        int maxScroll = Math.max(0, this.rows.size() * ROW_HEIGHT - this.rowsHeight);
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll);
        for (int i = 0; i < this.rowWidgets.size(); i++) {
            RowWidgets widgets = this.rowWidgets.get(i);
            int y = this.rowsTop + i * ROW_HEIGHT - this.scroll;
            boolean visible = y >= this.rowsTop && y + ROW_HEIGHT <= this.rowsTop + this.rowsHeight;
            widgets.block.setY(y + 2);
            widgets.block.visible = visible;
            if (widgets.chance != null) {
                widgets.chance.setY(y + 2);
                widgets.chance.visible = visible;
            }
            widgets.remove.setY(y + 2);
            widgets.remove.visible = visible;
            if (!visible && (getFocused() == widgets.block || getFocused() == widgets.chance)) {
                setFocused(null);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Drawn in render, over the screen behind
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.parent.render(guiGraphics, -1, -1, partialTick);
        guiGraphics.pose().pushPose();
        // Over everything the screen behind draws raised, such as its drawer's tabs
        guiGraphics.pose().translate(0, 0, 400);
        guiGraphics.fill(0, 0, this.width, this.height, OVERLAY_COLOR);
        int right = this.left + this.dialogWidth;
        int bottom = this.top + this.dialogHeight;
        guiGraphics.fill(this.left - 1, this.top - 1, right + 1, bottom + 1, BORDER_COLOR);
        guiGraphics.fill(this.left, this.top, right, bottom, BACKGROUND_COLOR);

        layOutRows();
        int x = this.left + PADDING;
        int y = this.top + PADDING;
        guiGraphics.drawString(this.font, this.title, x, y, 0xFFFFFFFF);
        y += 14;
        for (FormattedCharSequence line : this.descriptionLines) {
            guiGraphics.drawString(this.font, line, x, y, NOTE_COLOR);
            y += 10;
        }
        int labelColor = this.none ? 0xFF606060 : NOTE_COLOR;
        guiGraphics.drawString(this.font, Component.translatable(this.weighted ? "yungsroads.blocks.block" : "yungsroads.blocks.block_or_tag"),
                x + ICON_WIDTH, this.rowsTop - 11, labelColor, false);
        if (this.weighted && !this.rowWidgets.isEmpty()) {
            RowWidgets first = this.rowWidgets.get(0);
            guiGraphics.drawString(this.font, Component.translatable("yungsroads.blocks.chance"), first.chance.getX(), this.rowsTop - 11, labelColor, false);
        }
        if (this.rows.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable(this.weighted ? "yungsroads.blocks.no_rows.weighted" : "yungsroads.blocks.no_rows"),
                    x + ICON_WIDTH, this.rowsTop + 6, labelColor, false);
        }

        // Each row's block as an item, where it has one, so a mistyped id stands out. A tag shows its first block.
        List<Component> tooltip = List.of();
        guiGraphics.enableScissor(this.left, this.rowsTop, right, this.rowsTop + this.rowsHeight);
        for (RowWidgets widgets : this.rowWidgets) {
            if (!widgets.block.visible) {
                continue;
            }
            renderIcon(guiGraphics, widgets.row.block, x, widgets.block.getY());
            if (mouseX >= x && mouseX < x + 16 && mouseY >= widgets.block.getY() && mouseY < widgets.block.getY() + 16) {
                tooltip = tagContents(widgets.row.block);
            }
        }
        guiGraphics.disableScissor();

        if (this.weighted) {
            int defaultLabelY = this.addButton.getBottom() + 4;
            double rest = Math.max(0, 1 - totalChance());
            guiGraphics.drawString(this.font, Component.translatable("yungsroads.blocks.default", percent(rest)), x, defaultLabelY, labelColor, false);
            renderIcon(guiGraphics, this.defaultBlock, x, this.defaultBox.getY());
            Component barTooltip = renderBar(guiGraphics, x, this.defaultBox.getBottom() + 6, this.dialogWidth - PADDING * 2, mouseX, mouseY);
            if (barTooltip != null) {
                tooltip = List.of(barTooltip);
            }
        }
        Component error = error();
        if (error != null) {
            int errorY = this.doneButton.getY() - 16;
            guiGraphics.drawString(this.font, this.font.substrByWidth(error, this.dialogWidth - PADDING * 2).getString(), x, errorY, INVALID_COLOR, false);
        }

        int rowsBottom = this.rowsTop + this.rowsHeight;
        this.scrollBar.layOut(right - 4, this.rowsTop, rowsBottom, this.rowsHeight, this.rows.size() * ROW_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.scrollBar.render(guiGraphics, this.scroll, mouseX, mouseY);
        renderSuggestions(guiGraphics, mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();
    }

    private void renderIcon(GuiGraphics guiGraphics, String text, int x, int boxY) {
        List<Block> blocks;
        if (this.weighted) {
            BlockState state = parse(text);
            blocks = state == null ? List.of() : List.of(state.getBlock());
        } else {
            ExtraCodecs.TagOrElementLocation target = parseTarget(text);
            blocks = target == null ? List.of() : blocks(target);
        }
        blocks.stream()
                .map(Block::asItem)
                .filter(item -> item != Items.AIR)
                .findFirst()
                .ifPresent(item -> guiGraphics.renderItem(new ItemStack(item), x, boxY));
    }

    /** For a block tag, the lines of a tooltip naming the blocks it matches, or none if the text isn't a block tag. */
    private List<Component> tagContents(String text) {
        ExtraCodecs.TagOrElementLocation target = this.weighted ? null : parseTarget(text);
        if (target == null || !target.tag()) {
            return List.of();
        }
        List<Block> blocks = blocks(target);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("yungsroads.blocks.tag_contents", text(target), blocks.size()));
        int shown = Math.min(MAX_TAG_BLOCKS_SHOWN, blocks.size());
        for (Block block : blocks.subList(0, shown)) {
            lines.add(block.getName().withColor(NOTE_COLOR));
        }
        if (blocks.size() > shown) {
            lines.add(Component.translatable("yungsroads.blocks.tag_more", blocks.size() - shown).withColor(NOTE_COLOR));
        }
        return lines;
    }

    /**
     * Draws a bar split between the blocks in proportion to their chances, the default block taking the rest, each in
     * its map color.
     *
     * @return The hovered block's name and chance, or null if the bar isn't hovered.
     */
    @Nullable
    private Component renderBar(GuiGraphics guiGraphics, int x, int y, int width, int mouseX, int mouseY) {
        if (this.none) {
            return null;
        }
        List<BlockState> states = new ArrayList<>();
        List<Double> chances = new ArrayList<>();
        for (Row row : this.rows) {
            BlockState state = parse(row.block);
            Double chance = parseChance(row.chance);
            if (state != null && chance != null && chance > 0) {
                states.add(state);
                chances.add(chance);
            }
        }
        BlockState defaultState = parse(this.defaultBlock);
        double rest = 1 - totalChance();
        if (defaultState != null && rest > 1e-6) {
            states.add(defaultState);
            chances.add(rest);
        }
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
        Component hovered = null;
        double start = 0;
        for (int i = 0; i < states.size(); i++) {
            int from = x + (int) Math.round(start * width);
            start += chances.get(i);
            int to = x + (int) Math.round(Math.min(1, start) * width);
            int color = states.get(i).getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            guiGraphics.fill(from, y, to, y + BAR_HEIGHT, color == 0 ? NO_MAP_COLOR : 0xFF000000 | color);
            if (mouseX >= from && mouseX < to && mouseY >= y && mouseY < y + BAR_HEIGHT) {
                hovered = Component.translatable("yungsroads.blocks.share", states.get(i).getBlock().getName(), percent(chances.get(i)));
            }
        }
        return hovered;
    }

    // Suggestions

    /** Finds suggestions for the focused block box, if its text changed since they were last found. */
    private void updateSuggestions() {
        EditBox focused = getFocused() instanceof EditBox box && isBlockBox(box) ? box : null;
        if (focused == null) {
            this.suggestionBox = null;
            this.suggestions = List.of();
            return;
        }
        if (focused == this.suggestionBox && focused.getValue().equals(this.suggestionText)) {
            return;
        }
        // Typing in the box opens them, but focusing it doesn't
        this.suggestionsDismissed = focused != this.suggestionBox;
        this.suggestionBox = focused;
        this.suggestionText = focused.getValue();
        this.suggestionIndex = 0;
        this.suggestionScroll = 0;
        this.suggestions = suggest(focused.getValue(), !this.weighted);
    }

    private boolean isBlockBox(EditBox box) {
        return box == this.defaultBox || this.rowWidgets.stream().anyMatch(widgets -> widgets.block == box);
    }

    /**
     * Block ids matching typed text: those starting with it first, then those containing it. With tags, block tags
     * follow, after a {@code #}, and typing a {@code #} suggests only tags. None once properties are being typed, or if
     * the text is already the only match.
     */
    private static List<String> suggest(String text, boolean withTags) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty() || query.contains("[")) {
            return List.of();
        }
        boolean tagsOnly = query.startsWith("#");
        if (tagsOnly && !withTags) {
            return List.of();
        }
        String typed = tagsOnly ? query.substring(1) : query;
        List<String> starting = new ArrayList<>();
        List<String> containing = new ArrayList<>();
        if (!tagsOnly) {
            collectMatches(BuiltInRegistries.BLOCK.keySet().stream(), "", typed, starting, containing);
        }
        if (withTags) {
            collectMatches(BuiltInRegistries.BLOCK.getTagNames().map(TagKey::location), "#", typed, starting, containing);
        }
        List<String> matches = new ArrayList<>(starting);
        matches.addAll(containing);
        if (matches.size() == 1 && matches.get(0).equals(query)) {
            return List.of();
        }
        return matches;
    }

    /** Adds the ids, shown after the prefix, that start with the typed text, or else contain it, each sorted. */
    private static void collectMatches(Stream<ResourceLocation> ids, String prefix, String typed, List<String> starting, List<String> containing) {
        String path = typed.startsWith(ResourceLocation.DEFAULT_NAMESPACE + ":") ? typed.substring(ResourceLocation.DEFAULT_NAMESPACE.length() + 1) : typed;
        List<String> start = new ArrayList<>();
        List<String> contain = new ArrayList<>();
        ids.forEach(id -> {
            String shown = shortId(id);
            if (shown.startsWith(path) || id.toString().startsWith(typed)) {
                start.add(prefix + shown);
            } else if (shown.contains(path)) {
                contain.add(prefix + shown);
            }
        });
        start.sort(Comparator.naturalOrder());
        contain.sort(Comparator.naturalOrder());
        starting.addAll(start);
        containing.addAll(contain);
    }

    private boolean showsSuggestions() {
        return this.suggestionBox != null && this.suggestionBox.visible && !this.suggestions.isEmpty() && !this.suggestionsDismissed;
    }

    /** How many suggestions are shown at once. */
    private int visibleSuggestions() {
        return Math.min(VISIBLE_SUGGESTIONS, this.suggestions.size());
    }

    /** The top of the suggestion list: below its box, or above if there isn't room below. */
    private int suggestionsTop() {
        int height = visibleSuggestions() * SUGGESTION_HEIGHT;
        int below = this.suggestionBox.getBottom() + 1;
        return below + height <= this.height ? below : this.suggestionBox.getY() - 1 - height;
    }

    /** Whether the mouse is over the suggestion list, including its scroll bar. */
    private boolean isOverSuggestions(double mouseX, double mouseY) {
        int x = this.suggestionBox.getX();
        int y = suggestionsTop();
        return mouseX >= x && mouseX < x + this.suggestionBox.getWidth() && mouseY >= y && mouseY < y + visibleSuggestions() * SUGGESTION_HEIGHT;
    }

    /** Scrolls the suggestions, keeping them within the list. */
    private void scrollSuggestionsTo(int first) {
        this.suggestionScroll = Mth.clamp(first, 0, this.suggestions.size() - visibleSuggestions());
    }

    /** Moves the keyboard's suggestion, scrolling the list so it stays shown. */
    private void selectSuggestion(int index) {
        this.suggestionIndex = index;
        if (index < this.suggestionScroll) {
            scrollSuggestionsTo(index);
        } else if (index >= this.suggestionScroll + visibleSuggestions()) {
            scrollSuggestionsTo(index - visibleSuggestions() + 1);
        }
    }

    private void renderSuggestions(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        updateSuggestions();
        if (!showsSuggestions()) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        int x = this.suggestionBox.getX();
        int width = this.suggestionBox.getWidth();
        int y = suggestionsTop();
        int visible = visibleSuggestions();
        int height = visible * SUGGESTION_HEIGHT;
        scrollSuggestionsTo(this.suggestionScroll);
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, SUGGESTION_BORDER_COLOR);
        guiGraphics.fill(x, y, x + width, y + height, SUGGESTION_BACKGROUND_COLOR);
        this.suggestionScrollBar.layOut(x + width - 4, y, y + height, visible, this.suggestions.size());
        boolean scrolls = this.suggestions.size() > visible;
        // The row under the mouse is the one a click picks, so it's marked instead of the keyboard's
        boolean mouseOver = isOverSuggestions(mouseX, mouseY) && !this.suggestionScrollBar.isMouseOver(mouseX, mouseY);
        for (int i = 0; i < visible; i++) {
            int index = this.suggestionScroll + i;
            int rowY = y + i * SUGGESTION_HEIGHT;
            boolean hovered = mouseOver && mouseY >= rowY && mouseY < rowY + SUGGESTION_HEIGHT;
            if (mouseOver ? hovered : index == this.suggestionIndex) {
                guiGraphics.fill(x, rowY, x + width, rowY + SUGGESTION_HEIGHT, SUGGESTION_SELECTED_COLOR);
            }
            int textWidth = width - (scrolls ? 10 : 4);
            guiGraphics.drawString(this.font, this.font.substrByWidth(Component.literal(this.suggestions.get(index)), textWidth).getString(),
                    x + 2, rowY + 2, TEXT_COLOR, false);
        }
        this.suggestionScrollBar.render(guiGraphics, this.suggestionScroll, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    /** Fills the box with the suggestion, closing the suggestions, though it may still start other ids. */
    private void acceptSuggestion(int index) {
        EditBox box = this.suggestionBox;
        box.setValue(this.suggestions.get(index));
        box.moveCursorToEnd(false);
        updateSuggestions();
        this.suggestionsDismissed = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showsSuggestions()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int offset = this.suggestionScrollBar.mouseClicked(mouseX, mouseY, this.suggestionScroll);
                if (offset >= 0) {
                    scrollSuggestionsTo(offset);
                    return true;
                }
            }
            if (isOverSuggestions(mouseX, mouseY)) {
                acceptSuggestion(this.suggestionScroll + (int) ((mouseY - suggestionsTop()) / SUGGESTION_HEIGHT));
                return true;
            }
            // A click anywhere else closes them, and still goes to what was clicked
            this.suggestionsDismissed = true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int offset = this.scrollBar.mouseClicked(mouseX, mouseY, this.scroll);
            if (offset >= 0) {
                this.scroll = offset;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.suggestionScrollBar.isDragging()) {
            scrollSuggestionsTo(this.suggestionScrollBar.mouseDragged(mouseY));
            return true;
        }
        if (this.scrollBar.isDragging()) {
            this.scroll = this.scrollBar.mouseDragged(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollBar.mouseReleased();
        this.suggestionScrollBar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showsSuggestions() && isOverSuggestions(mouseX, mouseY)) {
            scrollSuggestionsTo(this.suggestionScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (mouseY >= this.rowsTop && mouseY < this.rowsTop + this.rowsHeight) {
            this.scroll -= (int) Math.round(scrollY * ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showsSuggestions()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    selectSuggestion((this.suggestionIndex + 1) % this.suggestions.size());
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    selectSuggestion((this.suggestionIndex + this.suggestions.size() - 1) % this.suggestions.size());
                    return true;
                }
                case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    acceptSuggestion(this.suggestionIndex);
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    // Closes the suggestions before the dialog
                    this.suggestionsDismissed = true;
                    return true;
                }
                default -> {
                }
            }
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.doneButton.active) {
            done();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
