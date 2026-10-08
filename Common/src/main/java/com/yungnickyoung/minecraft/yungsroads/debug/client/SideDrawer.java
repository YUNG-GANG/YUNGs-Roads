package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A drawer that slides out over the right side of the debug screen's map, showing one page at a time, such as help or
 * the details of a spot on the map. The settings panel stays visible and usable beside it.
 * <p>
 * Pages can differ in width, so switching pages while the drawer is out eases its edge to the new width.
 */
final class SideDrawer {
    static final int PADDING = 8;
    private static final long SLIDE_MILLIS = 150;
    private static final int HEADER_HEIGHT = 22;
    private static final int BACKGROUND_COLOR = 0xF0141414;
    private static final int EDGE_COLOR = 0xFF505050;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private final Font font;
    private DrawerPage page;
    private boolean open = false;
    /** The slide's progress from closed (0) to open (1) when it was last toggled, so a toggle mid-slide reverses it. */
    private float progressAtToggle = 0;
    private long toggledAt = 0;
    /** The width when the page was last switched, which the drawer eases from to the new page's width. */
    private int widthAtSwitch = -1;
    private long switchedAt = 0;

    SideDrawer(Font font, DrawerPage page) {
        this.font = font;
        this.page = page;
    }

    boolean isOpen() {
        return this.open;
    }

    /** Whether the drawer is out and showing the given page. */
    boolean isShowing(DrawerPage page) {
        return this.open && this.page == page;
    }

    /** Whether any of the drawer is on screen, including while it slides closed. */
    boolean isVisible() {
        return progress() > 0;
    }

    /** Shows the given page, sliding the drawer out if it isn't already. */
    void open(DrawerPage page, int areaWidth) {
        if (this.page != page) {
            if (this.open) {
                this.widthAtSwitch = width(areaWidth);
                this.switchedAt = Util.getMillis();
            }
            this.page = page;
        }
        if (!this.open) {
            this.progressAtToggle = linearProgress();
            this.toggledAt = Util.getMillis();
            this.open = true;
        }
    }

    /** Closes the drawer if it's showing the given page, and otherwise shows that page. */
    void toggle(DrawerPage page, int areaWidth) {
        if (isShowing(page)) {
            close();
        } else {
            open(page, areaWidth);
        }
    }

    void close() {
        if (this.open) {
            this.progressAtToggle = linearProgress();
            this.toggledAt = Util.getMillis();
            this.open = false;
        }
    }

    /** The drawer's width when out, easing to the page's width after the page is switched. */
    int width(int areaWidth) {
        int target = this.page.width(areaWidth);
        float t = Math.min(1, (Util.getMillis() - this.switchedAt) / (float) SLIDE_MILLIS);
        if (this.widthAtSwitch < 0 || t >= 1) {
            return target;
        }
        return Math.round(Mth.lerp(ease(t), this.widthAtSwitch, target));
    }

    /** The x of the drawer's left edge, sliding in from the screen's right edge. */
    int left(int screenWidth, int areaWidth) {
        return screenWidth - Math.round(width(areaWidth) * progress());
    }

    boolean isMouseOver(double mouseX, double mouseY, int screenWidth, int areaWidth) {
        return isVisible() && mouseX >= left(screenWidth, areaWidth);
    }

    void mouseScrolled(double scrollY) {
        this.page.mouseScrolled(scrollY);
    }

    void mouseClicked(double mouseX, double mouseY) {
        this.page.mouseClicked(mouseX, mouseY);
    }

    /** @return Whether something on the page is being dragged, and so took the drag. */
    boolean mouseDragged(double mouseY) {
        return this.page.mouseDragged(mouseY);
    }

    void mouseReleased() {
        this.page.mouseReleased();
    }

    /**
     * Draws the drawer and its page, and the hover text of any highlighted word under the mouse.
     *
     * @param areaWidth The width of the area the drawer slides over, which pages size themselves by.
     */
    void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int areaWidth, int mouseX, int mouseY) {
        if (!isVisible()) {
            return;
        }
        int left = left(screenWidth, areaWidth);
        guiGraphics.pose().pushPose();
        // Drawn above the map and the other widgets
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.fill(left, 0, screenWidth, screenHeight, BACKGROUND_COLOR);
        guiGraphics.fill(left, 0, left + 1, screenHeight, EDGE_COLOR);
        guiGraphics.drawString(this.font, this.page.title().copy().withStyle(ChatFormatting.BOLD), left + PADDING, PADDING, TEXT_COLOR);
        guiGraphics.fill(left + PADDING, HEADER_HEIGHT - 5, screenWidth - PADDING, HEADER_HEIGHT - 4, EDGE_COLOR);
        Component hovered = this.page.render(guiGraphics, left + PADDING, screenWidth - PADDING, HEADER_HEIGHT, screenHeight - PADDING, mouseX, mouseY);
        if (hovered != null) {
            guiGraphics.renderTooltip(this.font, this.font.split(hovered, 220), mouseX, mouseY);
        }
        guiGraphics.pose().popPose();
    }

    /** The slide's progress from closed (0) to open (1), eased so it starts fast and settles. */
    private float progress() {
        return ease(linearProgress());
    }

    private float linearProgress() {
        float elapsed = Math.min(1, (Util.getMillis() - this.toggledAt) / (float) SLIDE_MILLIS);
        float target = this.open ? 1 : 0;
        return this.progressAtToggle + (target - this.progressAtToggle) * elapsed;
    }

    private static float ease(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
