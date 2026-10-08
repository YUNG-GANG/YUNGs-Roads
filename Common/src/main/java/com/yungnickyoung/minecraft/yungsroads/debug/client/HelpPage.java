package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.debug.RoadTuning;
import com.yungnickyoung.minecraft.yungsroads.module.ConfigModule.GlobalSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.RoadSetting;
import com.yungnickyoung.minecraft.yungsroads.world.config.ITunableSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The side drawer's help page, explaining road types, every setting, and the terms they use, so settings can be read
 * about while they're changed. The page follows the selected tab, opening at its section, and a bar of links under its
 * title jumps to any section, marking the one being read.
 * <p>
 * The text comes from the same lang entries as the setting tooltips and the exported datapack's README.
 */
final class HelpPage implements DrawerPage {
    /** The sections of the page, in order. Each tab of the screen opens at its own section. */
    enum Section {
        OVERVIEW("overview"),
        ROAD_TYPES("road_types"),
        ROAD_NETWORKS("road_networks"),
        SELECTION("selection"),
        ROUTING("routing"),
        SHAPING("shaping"),
        BLOCKS("blocks"),
        GLOBAL("global"),
        VIEW("view"),
        SAVING("saving"),
        GLOSSARY("glossary");

        final String key;
        /** The section's link in the navigation bar, shorter than its title so the links fit in a row or two. */
        final Component navName;

        Section(String id) {
            this.key = "yungsroads.help." + id;
            this.navName = Component.translatable(this.key + ".nav");
        }
    }

    private static final int MIN_WIDTH = 240;
    private static final int MAX_WIDTH = 360;
    private static final int SECTION_GAP = 10;
    private static final int LINK_HEIGHT = 12;
    private static final int LINK_PADDING = 4;
    private static final int LINK_GAP = 3;
    /** How far a section's title can be below the top and still count as the section being read. */
    private static final int READING_MARGIN = 12;
    private static final int LINK_COLOR = 0xFF2A2A2A;
    private static final int HOVERED_LINK_COLOR = 0xFF404040;
    private static final int CURRENT_LINK_COLOR = 0xFF3A6EA5;
    private static final int SECTION_TITLE_COLOR = 0xFF8CC4FF;
    private static final int EDGE_COLOR = 0xFF505050;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int DETAIL_COLOR = 0xFFA0A0A0;

    /** A paragraph's label, such as "Priority:", which is shown in bold. */
    private static final Pattern LABEL = Pattern.compile("^([^:.]{1,32}):\\s");

    private final Font font;
    private final ScrollingText text;
    private final Map<Section, Integer> sectionTops = new EnumMap<>(Section.class);
    /** The navigation bar's links, laid out when the page was last drawn. */
    private final List<Link> links = new ArrayList<>();
    /**
     * The section last jumped to, while the page is still scrolled to where the jump left it. Sections near the end
     * can't scroll to the top, so they're marked as being read this way instead.
     */
    private Section jumpedTo;
    private int jumpedToScroll;
    /** The section to scroll to once the page is laid out, and whether to animate the scroll there. */
    private Section pendingSection;
    private boolean animatePending;

    private record Link(Section section, int x, int y, int width) {
        boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + LINK_HEIGHT;
        }
    }

    HelpPage(Font font) {
        this.font = font;
        this.text = new ScrollingText(font);
    }

    @Override
    public Component title() {
        return Component.translatable("yungsroads.help.title");
    }

    @Override
    public Component tabName() {
        return Component.translatable("yungsroads.screen.help.tab");
    }

    /** It covers almost all of a narrow area, leaving room for its tab beside it. */
    @Override
    public int width(int areaWidth) {
        return areaWidth < MIN_WIDTH + 40 ? areaWidth - DrawerTab.WIDTH : Mth.clamp(areaWidth / 2, MIN_WIDTH, MAX_WIDTH);
    }

    /**
     * Scrolls to the start of the section, quickly easing there if animated, so the reader sees the page move rather
     * than being dropped somewhere new.
     */
    void showSection(Section section, boolean animate) {
        this.pendingSection = section;
        this.animatePending = animate;
    }

    @Override
    public void mouseScrolled(double scrollY) {
        this.text.mouseScrolled(scrollY);
    }

    /**
     * Jumps to the section of a clicked navigation link, or starts dragging the scroll bar if it was clicked, jumping
     * there if the click was off its thumb.
     */
    @Override
    public void mouseClicked(double mouseX, double mouseY) {
        for (Link link : this.links) {
            if (link.isMouseOver(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                showSection(link.section, true);
                return;
            }
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
        int textWidth = right - left - 4;
        if (textWidth != this.text.width()) {
            layOut(textWidth);
        }
        int navBottom = layOutLinks(left, right - left, top);
        renderLinks(guiGraphics, mouseX, mouseY);
        guiGraphics.fill(left, navBottom + 3, right, navBottom + 4, EDGE_COLOR);
        int contentTop = navBottom + 6;

        if (this.pendingSection != null) {
            this.text.setViewHeight(bottom - contentTop);
            int sectionTop = this.sectionTops.getOrDefault(this.pendingSection, 0);
            if (this.animatePending) {
                this.jumpedToScroll = this.text.animateScrollTo(sectionTop);
            } else {
                this.text.scrollTo(sectionTop);
                this.jumpedToScroll = this.text.scroll();
            }
            this.jumpedTo = this.pendingSection;
            this.pendingSection = null;
        }
        return this.text.render(guiGraphics, left, contentTop, bottom, right + SideDrawer.PADDING - 4, mouseX, mouseY);
    }

    /**
     * Lays out the navigation bar's links in rows, wrapping as needed.
     *
     * @return The bottom of the last row.
     */
    private int layOutLinks(int left, int width, int top) {
        this.links.clear();
        int x = left;
        int y = top;
        for (Section section : Section.values()) {
            int linkWidth = this.font.width(section.navName) + LINK_PADDING * 2;
            if (x > left && x + linkWidth > left + width) {
                x = left;
                y += LINK_HEIGHT + LINK_GAP;
            }
            this.links.add(new Link(section, x, y, linkWidth));
            x += linkWidth + LINK_GAP;
        }
        return y + LINK_HEIGHT;
    }

    private void renderLinks(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Section reading = sectionBeingRead();
        for (Link link : this.links) {
            boolean current = link.section == reading;
            int color = current ? CURRENT_LINK_COLOR : link.isMouseOver(mouseX, mouseY) ? HOVERED_LINK_COLOR : LINK_COLOR;
            guiGraphics.fill(link.x, link.y, link.x + link.width, link.y + LINK_HEIGHT, color);
            guiGraphics.drawString(this.font, link.section.navName, link.x + LINK_PADDING, link.y + 2,
                    current ? 0xFFFFFFFF : TEXT_COLOR, false);
        }
    }

    /**
     * The section at the top of the page: the last whose title is scrolled to near the top or above it. While the page
     * is scrolling to a section or still where it was left there, the section jumped to.
     */
    private Section sectionBeingRead() {
        if (this.pendingSection != null) {
            return this.pendingSection;
        }
        if (this.jumpedTo != null && (this.text.isAnimating() || this.text.scroll() == this.jumpedToScroll)) {
            return this.jumpedTo;
        }
        this.jumpedTo = null;
        Section reading = Section.values()[0];
        for (Section section : Section.values()) {
            if (this.sectionTops.getOrDefault(section, 0) <= this.text.scroll() + READING_MARGIN) {
                reading = section;
            }
        }
        return reading;
    }

    /** Wraps the page's text to the given width, recording where each section starts. */
    private void layOut(int textWidth) {
        this.text.clear(textWidth);
        this.sectionTops.clear();
        for (Section section : Section.values()) {
            if (section.ordinal() > 0) {
                // The divider sits in the gap above the section, so jumping to the section leaves it just out of view
                this.text.addDivider(this.text.height() - SECTION_GAP / 2 - 1);
            }
            this.sectionTops.put(section, this.text.height());
            this.text.addParagraph(Component.translatable(section.key + ".title").withStyle(ChatFormatting.BOLD), SECTION_TITLE_COLOR);
            for (Component paragraph : paragraphs(section)) {
                this.text.addParagraph(paragraph, TEXT_COLOR);
            }
            this.text.addGap(SECTION_GAP - ScrollingText.PARAGRAPH_GAP);
        }
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
                paragraphs.add(option("yungsroads.screen.view.terrain"));
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
