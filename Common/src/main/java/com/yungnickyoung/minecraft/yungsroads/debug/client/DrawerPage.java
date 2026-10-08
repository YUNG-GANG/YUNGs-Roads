package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/** A page of the side drawer, drawn below the drawer's title. */
interface DrawerPage {
    /** The page's title, at the top of the drawer. */
    Component title();

    /** The tab that opens the page. */
    Component tabName();

    /** The page's width, given the width of the area the drawer slides over. */
    int width(int areaWidth);

    /**
     * Draws the page's content.
     *
     * @return The hover text of the highlighted word under the mouse, or null if there isn't one.
     */
    @Nullable
    Component render(GuiGraphics guiGraphics, int left, int right, int top, int bottom, int mouseX, int mouseY);

    void mouseScrolled(double scrollY);

    void mouseClicked(double mouseX, double mouseY);

    /** @return Whether something on the page is being dragged, and so took the drag. */
    boolean mouseDragged(double mouseY);

    void mouseReleased();
}
