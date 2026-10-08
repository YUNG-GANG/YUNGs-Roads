package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * A thin vertical scroll bar for the debug screen's scrolling areas. Its thumb can be dragged, and clicking elsewhere
 * on its column jumps there, centering the thumb on the click and carrying on as a drag.
 * <p>
 * The owner keeps the scroll offset, lays the bar out each frame with {@link #layOut}, and passes it the mouse.
 * Offsets are in whatever unit the owner scrolls by, such as pixels or list rows.
 */
final class ScrollBar {
    private static final int WIDTH = 2;
    /** How far either side of the bar still counts as on it, since it's too thin to hit easily. */
    private static final int HIT_MARGIN = 2;
    private static final int MIN_THUMB_HEIGHT = 8;
    private static final int TRACK_COLOR = 0x40FFFFFF;
    private static final int THUMB_COLOR = 0xC0FFFFFF;
    private static final int ACTIVE_THUMB_COLOR = 0xFFFFFFFF;

    /** Offsets are rounded to a multiple of this, so a drag stops where the mouse wheel could. */
    private final int step;
    private int x;
    private int top;
    private int bottom;
    private int visible;
    private int total;
    private boolean dragging = false;
    /** Where the thumb was grabbed, as the distance from its top to the mouse. */
    private double grabOffset;

    /**
     * @param step The unit offsets are rounded to.
     */
    ScrollBar(int step) {
        this.step = step;
    }

    /**
     * Places the bar and sizes its thumb.
     *
     * @param x       The bar's left edge.
     * @param visible How much of the content is shown at once, in the owner's units.
     * @param total   The content's full size, in the owner's units.
     */
    void layOut(int x, int top, int bottom, int visible, int total) {
        this.x = x;
        this.top = top;
        this.bottom = bottom;
        this.visible = visible;
        this.total = total;
    }

    boolean isDragging() {
        return this.dragging;
    }

    /** Whether the mouse is over the bar's column. The bar only exists while the content doesn't fit. */
    boolean isMouseOver(double mouseX, double mouseY) {
        return maxScroll() > 0 && mouseX >= this.x - HIT_MARGIN && mouseX < this.x + WIDTH + HIT_MARGIN
                && mouseY >= this.top && mouseY < this.bottom;
    }

    void render(GuiGraphics guiGraphics, int scroll, int mouseX, int mouseY) {
        if (maxScroll() <= 0) {
            return;
        }
        int thumbTop = thumbTop(scroll);
        boolean active = this.dragging || isMouseOver(mouseX, mouseY);
        guiGraphics.fill(this.x, this.top, this.x + WIDTH, this.bottom, TRACK_COLOR);
        guiGraphics.fill(this.x, thumbTop, this.x + WIDTH, thumbTop + thumbHeight(), active ? ACTIVE_THUMB_COLOR : THUMB_COLOR);
    }

    /**
     * Starts dragging if the mouse is over the bar. A click off the thumb first jumps the thumb's center to the mouse.
     *
     * @return The new offset, or -1 if the click wasn't on the bar.
     */
    int mouseClicked(double mouseX, double mouseY, int scroll) {
        if (!isMouseOver(mouseX, mouseY)) {
            return -1;
        }
        this.dragging = true;
        int thumbTop = thumbTop(scroll);
        if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight()) {
            this.grabOffset = mouseY - thumbTop;
            return scroll;
        }
        this.grabOffset = thumbHeight() / 2.0;
        return scrollAt(mouseY);
    }

    /** @return The offset that keeps the grabbed point of the thumb under the mouse. */
    int mouseDragged(double mouseY) {
        return scrollAt(mouseY);
    }

    void mouseReleased() {
        this.dragging = false;
    }

    private int scrollAt(double mouseY) {
        int max = maxScroll();
        int travel = this.bottom - this.top - thumbHeight();
        if (travel <= 0) {
            return 0;
        }
        double raw = (mouseY - this.grabOffset - this.top) * max / travel;
        return Mth.clamp((int) Math.round(raw / this.step) * this.step, 0, max);
    }

    private int maxScroll() {
        return Math.max(0, this.total - this.visible);
    }

    private int thumbHeight() {
        int trackHeight = this.bottom - this.top;
        return Mth.clamp(trackHeight * this.visible / Math.max(1, this.total), MIN_THUMB_HEIGHT, trackHeight);
    }

    private int thumbTop(int scroll) {
        return this.top + (this.bottom - this.top - thumbHeight()) * scroll / maxScroll();
    }
}
