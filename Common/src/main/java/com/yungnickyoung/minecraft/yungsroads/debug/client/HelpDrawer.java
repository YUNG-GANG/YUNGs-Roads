package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A help page that slides out over the right side of the debug screen, explaining road types, every setting, and the
 * terms they use. The settings panel stays visible and usable beside it, so settings can be read about while they're
 * changed. The page follows the selected tab, opening at its section.
 * <p>
 * The text comes from the same lang entries as the setting tooltips and the exported datapack's README.
 */
final class HelpDrawer {
    /** The sections of the page, in order. Each tab of the screen opens at its own section. */
    enum Section {
        OVERVIEW("overview"),
        ROAD_TYPES("road_types"),
        ROAD_NETWORKS("road_networks"),
        ROUTING("routing"),
        SHAPING("shaping"),
        GLOBAL("global"),
        VIEW("view"),
        SAVING("saving"),
        GLOSSARY("glossary");

        final String key;

        Section(String id) {
            this.key = "yungsroads.help." + id;
        }
    }

    private static final long SLIDE_MILLIS = 150;
    private static final int MIN_WIDTH = 240;
    private static final int MAX_WIDTH = 360;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 22;
    private static final int PARAGRAPH_GAP = 4;
    private static final int SECTION_GAP = 10;
    private static final int BACKGROUND_COLOR = 0xF0141414;
    private static final int EDGE_COLOR = 0xFF505050;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DETAIL_COLOR = 0xFFA0A0A0;

    /** A paragraph's label, such as "Priority:", which is shown in bold. */
    private static final Pattern LABEL = Pattern.compile("^([^:.]{1,32}):\\s");

    private final Font font;
    private boolean open = false;
    /** The slide's progress from closed (0) to open (1) when it was last toggled, so a toggle mid-slide reverses it. */
    private float progressAtToggle = 0;
    private long toggledAt = 0;
    private int scroll = 0;
    private int maxScroll = 0;
    private final ScrollBar scrollBar = new ScrollBar(1);

    /** The page's lines, laid out for {@link #laidOutWidth}. */
    private final List<Line> lines = new ArrayList<>();
    private final Map<Section, Integer> sectionTops = new EnumMap<>(Section.class);
    private int contentHeight = 0;
    private int laidOutWidth = -1;
    /** The section to scroll to once the page is laid out. */
    private Section pendingSection;

    private record Line(FormattedCharSequence text, int y, int color) {
    }

    HelpDrawer(Font font) {
        this.font = font;
    }

    boolean isOpen() {
        return this.open;
    }

    /** Whether any of the page is on screen, including while it slides closed. */
    boolean isVisible() {
        return progress() > 0;
    }

    /** Opens the page at the given section, or closes it. */
    void toggle(Section section) {
        if (this.open) {
            close();
        } else {
            this.progressAtToggle = linearProgress();
            this.toggledAt = Util.getMillis();
            this.open = true;
            showSection(section);
        }
    }

    void close() {
        if (this.open) {
            this.progressAtToggle = linearProgress();
            this.toggledAt = Util.getMillis();
            this.open = false;
        }
    }

    /** Scrolls to the start of the section. */
    void showSection(Section section) {
        this.pendingSection = section;
    }

    /** The page's width when open, given the width of the area it covers. It covers all of a narrow area. */
    static int width(int areaWidth) {
        return areaWidth < MIN_WIDTH + 40 ? areaWidth : Mth.clamp(areaWidth / 2, MIN_WIDTH, MAX_WIDTH);
    }

    /** The x of the page's left edge, sliding in from the screen's right edge. */
    int left(int screenWidth, int areaWidth) {
        return screenWidth - Math.round(width(areaWidth) * progress());
    }

    boolean isMouseOver(double mouseX, double mouseY, int screenWidth, int areaWidth) {
        return isVisible() && mouseX >= left(screenWidth, areaWidth);
    }

    boolean mouseScrolled(double scrollY) {
        this.scroll = Mth.clamp(this.scroll - (int) Math.round(scrollY * this.font.lineHeight * 3), 0, this.maxScroll);
        return true;
    }

    /** Starts dragging the scroll bar if it was clicked, jumping there if the click was off its thumb. */
    void mouseClicked(double mouseX, double mouseY) {
        int offset = this.scrollBar.mouseClicked(mouseX, mouseY, this.scroll);
        if (offset >= 0) {
            this.scroll = offset;
        }
    }

    /** @return Whether the scroll bar is being dragged, and so took the drag. */
    boolean mouseDragged(double mouseY) {
        if (!this.scrollBar.isDragging()) {
            return false;
        }
        this.scroll = this.scrollBar.mouseDragged(mouseY);
        return true;
    }

    void mouseReleased() {
        this.scrollBar.mouseReleased();
    }

    /**
     * Draws the page, and the definition of any highlighted term under the mouse.
     *
     * @param areaWidth The width of the area the page slides over, which sets its width.
     */
    void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int areaWidth, int mouseX, int mouseY) {
        if (!isVisible()) {
            return;
        }
        int width = width(areaWidth);
        int left = left(screenWidth, areaWidth);
        int textWidth = width - PADDING * 2 - 4;
        if (textWidth != this.laidOutWidth) {
            layOut(textWidth);
        }

        int contentTop = HEADER_HEIGHT;
        int contentBottom = screenHeight - PADDING;
        this.maxScroll = Math.max(0, this.contentHeight - (contentBottom - contentTop));
        if (this.pendingSection != null) {
            this.scroll = this.sectionTops.getOrDefault(this.pendingSection, 0);
            this.pendingSection = null;
        }
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);

        guiGraphics.pose().pushPose();
        // Drawn above the map and the other widgets
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.fill(left, 0, screenWidth, screenHeight, BACKGROUND_COLOR);
        guiGraphics.fill(left, 0, left + 1, screenHeight, EDGE_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("yungsroads.help.title").withStyle(ChatFormatting.BOLD),
                left + PADDING, PADDING, TEXT_COLOR);
        guiGraphics.fill(left + PADDING, HEADER_HEIGHT - 5, screenWidth - PADDING, HEADER_HEIGHT - 4, EDGE_COLOR);

        Style hovered = null;
        int x = left + PADDING;
        guiGraphics.enableScissor(left, contentTop, screenWidth, contentBottom);
        for (Line line : this.lines) {
            int y = contentTop + line.y - this.scroll;
            if (y + this.font.lineHeight < contentTop || y > contentBottom) {
                continue;
            }
            guiGraphics.drawString(this.font, line.text, x, y, line.color);
            if (mouseY >= y && mouseY < y + this.font.lineHeight && mouseY >= contentTop && mouseY < contentBottom && mouseX >= x) {
                hovered = this.font.getSplitter().componentStyleAtWidth(line.text, mouseX - x);
            }
        }
        guiGraphics.disableScissor();
        // Laid out each frame, since the page slides in and out
        this.scrollBar.layOut(screenWidth - 4, contentTop, contentBottom, contentBottom - contentTop, contentBottom - contentTop + this.maxScroll);
        this.scrollBar.render(guiGraphics, this.scroll, mouseX, mouseY);

        if (hovered != null && hovered.getHoverEvent() != null) {
            Component text = hovered.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
            if (text != null) {
                guiGraphics.renderTooltip(this.font, this.font.split(text, 220), mouseX, mouseY);
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int x, int top, int bottom) {
        if (this.maxScroll <= 0) {
            return;
        }
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(8, trackHeight * trackHeight / (trackHeight + this.maxScroll));
        int thumbY = top + (trackHeight - thumbHeight) * this.scroll / this.maxScroll;
        guiGraphics.fill(x, top, x + 2, bottom, 0x40FFFFFF);
        guiGraphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xC0FFFFFF);
    }

    /** The slide's progress from closed (0) to open (1), eased so it starts fast and settles. */
    private float progress() {
        float t = linearProgress();
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private float linearProgress() {
        float elapsed = Math.min(1, (Util.getMillis() - this.toggledAt) / (float) SLIDE_MILLIS);
        float target = this.open ? 1 : 0;
        return this.progressAtToggle + (target - this.progressAtToggle) * elapsed;
    }

    /** Wraps the page's text to the given width, recording where each section starts. */
    private void layOut(int textWidth) {
        this.lines.clear();
        this.sectionTops.clear();
        this.laidOutWidth = textWidth;
        int y = 0;
        for (Section section : Section.values()) {
            this.sectionTops.put(section, y);
            y = addParagraph(Component.translatable(section.key + ".title").withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                    textWidth, y, TEXT_COLOR);
            for (Component paragraph : paragraphs(section)) {
                y = addParagraph(paragraph, textWidth, y, TEXT_COLOR);
            }
            y += SECTION_GAP - PARAGRAPH_GAP;
        }
        this.contentHeight = y;
    }

    private int addParagraph(Component paragraph, int textWidth, int y, int color) {
        for (FormattedCharSequence line : this.font.split(paragraph, textWidth)) {
            this.lines.add(new Line(line, y, color));
            y += this.font.lineHeight + 1;
        }
        return y + PARAGRAPH_GAP;
    }

    /** The paragraphs of a section, below its title. */
    private static List<Component> paragraphs(Section section) {
        List<Component> paragraphs = new ArrayList<>();
        switch (section) {
            case ROUTING -> {
                paragraphs.addAll(text(section.key + ".text"));
                for (RoadSetting setting : RoadSetting.values()) {
                    if (setting.group() == ITunableSetting.Group.ROUTING) {
                        paragraphs.add(setting(setting));
                    }
                }
            }
            case SHAPING -> {
                paragraphs.addAll(text(section.key + ".text"));
                for (RoadSetting setting : RoadSetting.values()) {
                    if (setting.group() == ITunableSetting.Group.SHAPING) {
                        paragraphs.add(setting(setting));
                    }
                }
            }
            case GLOBAL -> {
                paragraphs.addAll(text(section.key + ".text"));
                for (GlobalSetting setting : GlobalSetting.values()) {
                    paragraphs.add(setting(setting));
                }
                paragraphs.add(option(RoadDebugScreen.PLACE_ROADS_KEY));
            }
            case VIEW -> {
                paragraphs.addAll(text(section.key + ".text"));
                for (String option : RoadDebugScreen.VIEW_OPTIONS) {
                    paragraphs.add(option("yungsroads.screen.view." + option));
                }
            }
            case SAVING -> paragraphs.addAll(text(section.key + ".text", RoadTuning.REGENERATE_RADIUS));
            case GLOSSARY -> {
                for (RoutingGlossary.Term term : RoutingGlossary.Term.values()) {
                    paragraphs.add(RoutingGlossary.definition(term));
                }
            }
            default -> paragraphs.addAll(text(section.key + ".text"));
        }
        return paragraphs;
    }

    /**
     * A lang entry's paragraphs, one per line, with glossary terms highlighted and any leading label in bold.
     */
    private static List<Component> text(String key, Object... args) {
        String text = Language.getInstance().getOrDefault(key);
        if (args.length > 0) {
            text = String.format(text, args);
        }
        List<Component> paragraphs = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            Matcher label = LABEL.matcher(paragraph);
            if (label.find()) {
                paragraphs.add(Component.empty()
                        .append(Component.literal(label.group(1) + ": ").withStyle(ChatFormatting.BOLD))
                        .append(RoutingGlossary.highlight(paragraph.substring(label.end()))));
            } else {
                paragraphs.add(RoutingGlossary.highlight(paragraph));
            }
        }
        return paragraphs;
    }

    /** A setting's name, description, range, and default. */
    private static Component setting(ITunableSetting setting) {
        String description = Language.getInstance().getOrDefault(setting.descriptionKey());
        Component details = setting.isToggle()
                ? Component.translatable("yungsroads.screen.default",
                        Component.translatable(setting.defaultValue() != 0 ? "yungsroads.screen.on" : "yungsroads.screen.off"))
                : Component.translatable("yungsroads.screen.range", setting.format(setting.min()), setting.format(setting.max()))
                        .append(". ")
                        .append(Component.translatable("yungsroads.screen.default", setting.format(setting.defaultValue())));
        // Appended to an empty root, since children take their parent's color
        return Component.empty()
                .append(Component.translatable(setting.nameKey()).append(": ").withColor(RoutingGlossary.VALUE_COLOR))
                .append(RoutingGlossary.highlight(description))
                .append(Component.literal(" ").append(details).withColor(DETAIL_COLOR));
    }

    /** An option's label and description. */
    private static Component option(String key) {
        MutableComponent label = Component.translatable(key).append(": ").withColor(RoutingGlossary.VALUE_COLOR);
        return Component.empty().append(label)
                .append(RoutingGlossary.highlight(Language.getInstance().getOrDefault(key + ".description")));
    }
}
