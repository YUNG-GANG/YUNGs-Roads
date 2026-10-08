package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One of a road type's lists of blocks on the blocks page, on one line: its label, its blocks as items, and their
 * names. Clicking it opens the list in a dialog to edit. The label is marked while the list differs from what Reset
 * restores.
 */
final class BlockListRow extends AbstractWidget {
    static final int HEIGHT = 20;
    private static final int MAX_ICONS = 3;
    private static final int HOVERED_COLOR = 0xFF282828;
    private static final int BORDER_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int EDITED_COLOR = 0xFFFFD050;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private final int labelWidth;
    private final List<ItemStack> icons;
    private final Component summary;
    private final BooleanSupplier edited;
    private final Runnable onPress;

    /**
     * @param labelWidth The width kept for the label, the same for every row so their blocks line up.
     * @param icons      The list's blocks as items, of which the first few are shown.
     * @param summary    The list's blocks in words, cut off where it doesn't fit.
     */
    BlockListRow(int x, int y, int width, Component label, int labelWidth, List<ItemStack> icons, Component summary,
                 BooleanSupplier edited, Runnable onPress) {
        super(x, y, width, HEIGHT, label);
        this.labelWidth = labelWidth;
        this.icons = icons;
        this.summary = summary;
        this.edited = edited;
        this.onPress = onPress;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.fill(getX(), getY(), getRight(), getBottom(), BORDER_COLOR);
        guiGraphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, isHoveredOrFocused() ? HOVERED_COLOR : 0xFF101010);
        guiGraphics.drawString(font, getMessage(), getX() + 4, getY() + 6, this.edited.getAsBoolean() ? EDITED_COLOR : LABEL_COLOR, false);
        int x = getX() + 4 + this.labelWidth;
        for (ItemStack icon : this.icons.subList(0, Math.min(MAX_ICONS, this.icons.size()))) {
            guiGraphics.renderItem(icon, x, getY() + 2);
            x += 17;
        }
        // Left-aligned while it fits, and scrolling when it doesn't
        int textLeft = x + 2;
        renderScrollingString(guiGraphics, font, this.summary, textLeft + font.width(this.summary) / 2, textLeft, getY(),
                getRight() - 4, getBottom(), TEXT_COLOR);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(": ").append(this.summary));
    }
}
