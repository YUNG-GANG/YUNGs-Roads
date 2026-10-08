package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws the frame of a dialog shown over the tuning screen: the screen behind, dimmed, and the dialog's box.
 */
final class DialogFrame {
    private static final int OVERLAY_COLOR = 0xB0000000;
    private static final int BACKGROUND_COLOR = 0xF0181818;
    private static final int BORDER_COLOR = 0xFF505050;

    private DialogFrame() {
    }

    /**
     * Draws the screen behind, without its hover states, then dims it and draws the dialog's box. Raises everything drawn
     * after it above what the screen behind draws raised, such as its drawer's tabs, until {@link #end}.
     */
    static void begin(GuiGraphics guiGraphics, Screen behind, int screenWidth, int screenHeight, int left, int top, int width, int height,
                      float partialTick) {
        behind.render(guiGraphics, -1, -1, partialTick);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        guiGraphics.fill(0, 0, screenWidth, screenHeight, OVERLAY_COLOR);
        guiGraphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, BORDER_COLOR);
        guiGraphics.fill(left, top, left + width, top + height, BACKGROUND_COLOR);
    }

    static void end(GuiGraphics guiGraphics) {
        guiGraphics.pose().popPose();
    }
}
