package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapped paragraphs in a scrolling area of a side drawer page, with a scroll bar. Highlighted words show their hover
 * text when the mouse is over them, as in chat.
 * <p>
 * The owner lays the text out with {@link #clear} and the add methods, whenever it or its width changes.
 */
final class ScrollingText {
    static final int PARAGRAPH_GAP = 4;
    /** How far left of the text a bar marking a group of paragraphs is drawn. */
    static final int BAR_INDENT = 6;
    private static final int BAR_WIDTH = 2;
    private static final int DIVIDER_COLOR = 0xFF505050;

    private record Line(FormattedCharSequence text, int y, int color) {
    }

    /** A bar beside the paragraphs between the given ys, marking them as a group. */
    private record Bar(int top, int bottom, int color) {
    }

    private final Font font;
    private final List<Line> lines = new ArrayList<>();
    /** The y of each divider line across the text. */
    private final List<Integer> dividers = new ArrayList<>();
    private final List<Bar> bars = new ArrayList<>();
    /** Where the bar being added starts, and its color, while one is being added. */
    private int barTop;
    private int barColor;
    private final ScrollBar scrollBar = new ScrollBar(1);
    private int width = -1;
    private int height = 0;
    private int scroll = 0;
    private int maxScroll = 0;

    ScrollingText(Font font) {
        this.font = font;
    }

    /** Removes the text, to lay it out again at the given width. Keeps the scroll. */
    void clear(int width) {
        this.lines.clear();
        this.dividers.clear();
        this.bars.clear();
        this.width = width;
        this.height = 0;
    }

    /** The width the text is laid out for, or -1 before it's first laid out. */
    int width() {
        return this.width;
    }

    /** The height of the text laid out so far, which is where the next paragraph starts. */
    int height() {
        return this.height;
    }

    /** Adds a paragraph, wrapped to the width, followed by a small gap. */
    void addParagraph(Component paragraph, int color) {
        addParagraph(paragraph, color, PARAGRAPH_GAP);
    }

    /** Adds a paragraph, wrapped to the width, followed by the given gap. */
    void addParagraph(Component paragraph, int color, int gap) {
        for (FormattedCharSequence line : this.font.split(paragraph, this.width)) {
            this.lines.add(new Line(line, this.height, color));
            this.height += this.font.lineHeight + 1;
        }
        this.height += gap;
    }

    void addGap(int gap) {
        this.height += gap;
    }

    /** Adds a line across the text at the given y, which should be in a gap between paragraphs. */
    void addDivider(int y) {
        this.dividers.add(y);
    }

    /** Starts a bar of the given color beside the paragraphs added until {@link #endBar}. */
    void beginBar(int color) {
        this.barTop = this.height;
        this.barColor = color;
    }

    /** Ends the bar beside the paragraphs added since {@link #beginBar}, at the bottom of the last one's text. */
    void endBar() {
        this.bars.add(new Bar(this.barTop, this.height - PARAGRAPH_GAP, this.barColor));
    }

    int scroll() {
        return this.scroll;
    }

    /**
     * Sets the height the text is shown in, which limits how far it scrolls. Drawing sets it too, so it only needs
     * setting before scrolling the text before it's drawn.
     */
    void setViewHeight(int viewHeight) {
        this.maxScroll = Math.max(0, this.height - viewHeight);
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
    }

    /** Scrolls so the given y is at the top, or as near as the text's length allows. */
    void scrollTo(int y) {
        this.scroll = Mth.clamp(y, 0, this.maxScroll);
    }

    void mouseScrolled(double scrollY) {
        scrollTo(this.scroll - (int) Math.round(scrollY * this.font.lineHeight * 3));
    }

    /** @return Whether the click was on the scroll bar, which starts dragging it. */
    boolean mouseClicked(double mouseX, double mouseY) {
        int offset = this.scrollBar.mouseClicked(mouseX, mouseY, this.scroll);
        if (offset < 0) {
            return false;
        }
        this.scroll = offset;
        return true;
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
     * Draws the text between the given top and bottom, scrolled, with its scroll bar at the given x.
     *
     * @return The hover text of the highlighted word under the mouse, or null if there isn't one.
     */
    @Nullable
    Component render(GuiGraphics guiGraphics, int x, int top, int bottom, int scrollBarX, int mouseX, int mouseY) {
        setViewHeight(bottom - top);
        Style hovered = null;
        guiGraphics.enableScissor(x - BAR_INDENT, top, scrollBarX, bottom);
        for (Bar bar : this.bars) {
            guiGraphics.fill(x - BAR_INDENT, top + bar.top - this.scroll, x - BAR_INDENT + BAR_WIDTH, top + bar.bottom - this.scroll, bar.color);
        }
        for (int divider : this.dividers) {
            int y = top + divider - this.scroll;
            guiGraphics.fill(x, y, x + this.width, y + 1, DIVIDER_COLOR);
        }
        for (Line line : this.lines) {
            int y = top + line.y - this.scroll;
            if (y + this.font.lineHeight < top || y > bottom) {
                continue;
            }
            guiGraphics.drawString(this.font, line.text, x, y, line.color);
            if (mouseY >= y && mouseY < y + this.font.lineHeight && mouseY >= top && mouseY < bottom && mouseX >= x) {
                hovered = this.font.getSplitter().componentStyleAtWidth(line.text, mouseX - x);
            }
        }
        guiGraphics.disableScissor();
        this.scrollBar.layOut(scrollBarX, top, bottom, bottom - top, bottom - top + this.maxScroll);
        this.scrollBar.render(guiGraphics, this.scroll, mouseX, mouseY);
        return hovered == null || hovered.getHoverEvent() == null ? null : hovered.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
    }
}
