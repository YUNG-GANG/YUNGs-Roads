package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * A tab of the rail down the road type tab's left edge, showing an item for one page of settings. Icons keep the rail
 * narrow, so it has room for more pages than a row of labeled tabs would. The page's name is shown as its heading, and
 * when the tab is hovered.
 */
final class PageRailTab extends AbstractWidget {
    static final int SIZE = 20;
    private static final int SELECTED_COLOR = 0xFF383838;
    private static final int HOVERED_COLOR = 0xFF282828;
    /** The same blue as the step badges and the help page's current link. */
    private static final int ACCENT_COLOR = 0xFF3A6EA5;
    private static final int ACCENT_WIDTH = 2;
    /** Drawn over the icons of the pages not shown, so the shown page's icon stands out. */
    private static final int DIM_COLOR = 0xA0141414;

    private final ItemStack icon;
    private final BooleanSupplier selected;
    private final Runnable onPress;

    PageRailTab(int x, int y, Component name, ItemStack icon, BooleanSupplier selected, Runnable onPress) {
        super(x, y, SIZE, SIZE, name);
        this.icon = icon;
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean selected = this.selected.getAsBoolean();
        boolean hovered = isHoveredOrFocused();
        if (selected || hovered) {
            guiGraphics.fill(getX(), getY(), getRight(), getBottom(), selected ? SELECTED_COLOR : HOVERED_COLOR);
        }
        if (selected) {
            guiGraphics.fill(getX(), getY(), getX() + ACCENT_WIDTH, getBottom(), ACCENT_COLOR);
        }
        guiGraphics.renderItem(this.icon, getX() + 2, getY() + 2);
        if (!selected && !hovered) {
            guiGraphics.pose().pushPose();
            // Over the item, which is drawn raised
            guiGraphics.pose().translate(0, 0, 300);
            guiGraphics.fill(getX() + 2, getY() + 2, getX() + 18, getY() + 18, DIM_COLOR);
            guiGraphics.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", getMessage()));
    }
}
