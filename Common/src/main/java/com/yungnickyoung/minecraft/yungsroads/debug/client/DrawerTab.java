package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.math.Axis;
import com.yungnickyoung.minecraft.yungsroads.YungsRoadsCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * A tab that opens a page of the side drawer, standing out from the drawer's left edge like a handle, with its label
 * running up it. The tabs sit at the screen's right edge while the drawer is closed and slide out with it. The tab of
 * the page shown matches the drawer, joining it.
 * <p>
 * Until help has been opened for the first time, the help tab pulses yellow so it isn't missed. That it's been opened
 * is remembered across launches by an empty marker file in the config folder.
 */
final class DrawerTab extends AbstractWidget {
    static final int WIDTH = 20;
    private static final int PADDING = 10;
    /** The drawer's background, which the shown page's tab matches. */
    private static final int SELECTED_BACKGROUND_COLOR = 0xF0141414;
    private static final int BACKGROUND_COLOR = 0xF0262626;
    private static final int HOVERED_BACKGROUND_COLOR = 0xF0383838;
    private static final int EDGE_COLOR = 0xFF505050;
    private static final int SELECTED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFB0B0B0;
    /** The color the tab flashes until help is first opened, alternating with its usual background. */
    private static final int PULSE_COLOR = 0xFFFFD030;
    private static final int PULSE_TEXT_COLOR = 0xFF202020;
    private static final long PULSE_MILLIS = 1200;

    /** Whether help has ever been opened, read from the marker file the first time it's needed. */
    private static Boolean opened;

    private final BooleanSupplier selected;
    /** Whether the tab pulses until help is first opened. */
    private final boolean pulsesUntilHelpOpened;
    private final Runnable onPress;

    /**
     * @param selected Whether the drawer is out and showing the tab's page.
     */
    DrawerTab(Component label, BooleanSupplier selected, boolean pulsesUntilHelpOpened, Runnable onPress) {
        super(0, 0, WIDTH, Minecraft.getInstance().font.width(label.copy().withStyle(ChatFormatting.BOLD)) + PADDING * 2,
                label.copy().withStyle(ChatFormatting.BOLD));
        this.selected = selected;
        this.pulsesUntilHelpOpened = pulsesUntilHelpOpened;
        this.onPress = onPress;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onPress.run();
    }

    /** Stops the help tab's pulse for good, since the player has found help. */
    static void markHelpOpened() {
        if (hasHelpBeenOpened()) {
            return;
        }
        opened = true;
        try {
            Path marker = markerPath();
            Files.createDirectories(marker.getParent());
            Files.createFile(marker);
        } catch (IOException e) {
            // Only means the tab pulses again next launch
            YungsRoadsCommon.LOGGER.warn("Unable to record that the road debug help has been opened", e);
        }
    }

    static boolean hasHelpBeenOpened() {
        if (opened == null) {
            opened = Files.exists(markerPath());
        }
        return opened;
    }

    private static Path markerPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(YungsRoadsCommon.MOD_ID).resolve("help_opened");
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean selected = this.selected.getAsBoolean();
        int background = selected ? SELECTED_BACKGROUND_COLOR : isHoveredOrFocused() ? HOVERED_BACKGROUND_COLOR : BACKGROUND_COLOR;
        int edge = EDGE_COLOR;
        int text = selected || isHoveredOrFocused() ? SELECTED_TEXT_COLOR : TEXT_COLOR;
        if (this.pulsesUntilHelpOpened && !hasHelpBeenOpened()) {
            // Fades between the usual background and yellow, lingering at each so it reads as a flash rather than a
            // shimmer. The text switches to dark halfway, staying readable on both, and the yellow edge stays put so
            // the tab still stands out while dark.
            float wave = (Mth.sin((float) (Util.getMillis() % PULSE_MILLIS) / PULSE_MILLIS * Mth.TWO_PI) + 1) / 2;
            float pulse = wave * wave * (3 - 2 * wave);
            background = FastColor.ARGB32.lerp(pulse, background, PULSE_COLOR);
            edge = PULSE_COLOR;
            text = pulse > 0.5F ? PULSE_TEXT_COLOR : TEXT_COLOR;
        }
        // Open on the right, where it joins the page
        guiGraphics.fill(getX(), getY(), getRight(), getBottom(), edge);
        guiGraphics.fill(getX() + 1, getY() + 1, getRight(), getBottom() - 1, background);

        Font font = Minecraft.getInstance().font;
        guiGraphics.pose().pushPose();
        // The label runs up the tab, as on the side tabs of web pages
        guiGraphics.pose().translate(getX() + (this.width - font.lineHeight) / 2f + 1, getBottom() - PADDING, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(-90));
        guiGraphics.drawString(font, getMessage(), 0, 0, text, false);
        guiGraphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, getMessage());
    }
}
