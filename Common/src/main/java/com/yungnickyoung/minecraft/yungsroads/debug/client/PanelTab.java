package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/**
 * A tab of the debug screen's settings panel, which looks like vanilla's world creation tabs.
 */
final class PanelTab extends AbstractWidget {
    private static final WidgetSprites SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/tab_selected"),
            ResourceLocation.withDefaultNamespace("widget/tab"),
            ResourceLocation.withDefaultNamespace("widget/tab_selected_highlighted"),
            ResourceLocation.withDefaultNamespace("widget/tab_highlighted"));
    private static final int SELECTED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int UNSELECTED_TEXT_COLOR = 0xFFA0A0A0;

    private final BooleanSupplier selected;
    private final Runnable onPress;

    PanelTab(int x, int y, int width, int height, Component name, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height, name);
        this.selected = selected;
        this.onPress = onPress;
    }

    boolean isSelected() {
        return this.selected.getAsBoolean();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean selected = isSelected();
        int textColor = selected || isHoveredOrFocused() ? SELECTED_TEXT_COLOR : UNSELECTED_TEXT_COLOR;
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(SPRITES.get(selected, isHoveredOrFocused()), getX(), getY(), this.width, this.height);
        RenderSystem.disableBlend();
        // As vanilla's tabs do, the selected tab stands taller, with its label underlined
        int textTop = getY() + (selected ? 0 : 3);
        renderScrollingString(guiGraphics, font, getMessage(), getX() + 1, textTop, getRight() - 1, getBottom(), textColor);
        if (selected) {
            int underlineWidth = Math.min(font.width(getMessage()), this.width - 4);
            int underlineX = getX() + (this.width - underlineWidth) / 2;
            guiGraphics.fill(underlineX, getBottom() - 3, underlineX + underlineWidth, getBottom() - 2, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", getMessage()));
    }
}
