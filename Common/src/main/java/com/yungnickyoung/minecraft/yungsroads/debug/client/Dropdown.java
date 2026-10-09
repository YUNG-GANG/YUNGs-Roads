package com.yungnickyoung.minecraft.yungsroads.debug.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A box showing the selected option, which opens a list of all options below it when clicked.
 * <p>
 * The open list covers other widgets, so the screen must pass it clicks and scrolls before anything else, and draw it
 * after everything else, with {@link #listClicked}, {@link #listDragged}, {@link #listReleased}, {@link #listScrolled}
 * and {@link #renderList}.
 */
final class Dropdown<T> extends AbstractWidget {
    private static final int OPTION_HEIGHT = 12;
    private static final int MAX_VISIBLE_OPTIONS = 10;
    private static final int BORDER_COLOR = 0xFFA0A0A0;
    private static final int FOCUSED_BORDER_COLOR = 0xFFFFFFFF;
    private static final int BOX_COLOR = 0xFF000000;
    private static final int LIST_COLOR = 0xF0101010;
    private static final int HOVERED_OPTION_COLOR = 0xFF404040;
    private static final int SELECTED_OPTION_COLOR = 0xFF282828;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int INACTIVE_TEXT_COLOR = 0xFF707070;

    private final List<T> options;
    private final Function<T, Component> label;
    private final Consumer<T> onSelect;
    private T value;
    private boolean open = false;
    private int scroll = 0;
    /** The open list's scroll bar, scrolling by whole options. */
    private final ScrollBar scrollBar = new ScrollBar(1);

    /**
     * @param name     Narrated before the selected option's label.
     * @param label    An option's label, in the box and the list.
     * @param onSelect Called when an option is picked from the list, even the one already selected.
     */
    Dropdown(int x, int y, int width, int height, Component name, List<T> options, T value, Function<T, Component> label, Consumer<T> onSelect) {
        super(x, y, width, height, name);
        this.options = List.copyOf(options);
        this.label = label;
        this.onSelect = onSelect;
        this.value = value;
    }

    boolean isOpen() {
        return this.open;
    }

    void close() {
        this.open = false;
        this.scrollBar.mouseReleased();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.open = !this.open;
        if (this.open) {
            // Show the selected option
            int index = this.options.indexOf(this.value);
            this.scroll = Mth.clamp(index - MAX_VISIBLE_OPTIONS / 2, 0, maxScroll());
        }
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int borderColor = this.active && (isHoveredOrFocused() || this.open) ? FOCUSED_BORDER_COLOR : BORDER_COLOR;
        guiGraphics.fill(getX(), getY(), getRight(), getBottom(), borderColor);
        guiGraphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, BOX_COLOR);
        int textColor = this.active ? TEXT_COLOR : INACTIVE_TEXT_COLOR;
        // Inactive, the label is all one gray, without the colors of parts such as road type names
        Component label = this.label.apply(this.value);
        renderOption(guiGraphics, font, this.active ? label : Component.literal(label.getString()), getY(), this.height, getRight() - 12, textColor);
        if (this.active) {
            guiGraphics.drawString(font, this.open ? "▲" : "▼", getRight() - 9, getY() + (this.height - 8) / 2, textColor, false);
        }
    }

    /** Draws the open list below the box, above everything else. */
    void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int top = getBottom();
        int bottom = top + visibleCount() * OPTION_HEIGHT + 2;
        int hovered = optionAt(mouseX, mouseY);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        guiGraphics.fill(getX(), top - 1, getRight(), bottom, FOCUSED_BORDER_COLOR);
        guiGraphics.fill(getX() + 1, top, getRight() - 1, bottom - 1, LIST_COLOR);
        for (int row = 0; row < visibleCount(); row++) {
            int index = row + this.scroll;
            int y = top + 1 + row * OPTION_HEIGHT;
            T option = this.options.get(index);
            if (index == hovered || option.equals(this.value)) {
                guiGraphics.fill(getX() + 1, y, getRight() - 1, y + OPTION_HEIGHT, index == hovered ? HOVERED_OPTION_COLOR : SELECTED_OPTION_COLOR);
            }
            renderOption(guiGraphics, font, this.label.apply(option), y, OPTION_HEIGHT, getRight() - 5, TEXT_COLOR);
        }
        this.scrollBar.layOut(getRight() - 3, top + 1, bottom - 1, visibleCount(), this.options.size());
        this.scrollBar.render(guiGraphics, this.scroll, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    /**
     * Draws an option's label left-aligned, as in a text field, so the box reads as a picker rather than a button.
     * Labels too long to fit scroll, as on vanilla buttons.
     */
    private void renderOption(GuiGraphics guiGraphics, Font font, Component label, int y, int height, int right, int color) {
        int left = getX() + 4;
        if (font.width(label) <= right - left) {
            guiGraphics.drawString(font, label, left, y + (height - 8) / 2, color);
        } else {
            renderScrollingString(guiGraphics, font, label, left, y, right, y + height, color);
        }
    }

    /** Whether the open list is under the mouse, including the box it hangs from. */
    boolean isMouseOverList(double mouseX, double mouseY) {
        return this.open && mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom() + visibleCount() * OPTION_HEIGHT + 2;
    }

    /**
     * Handles a click while the list is open: picks the option under the mouse, then closes the list. A click on the
     * list's scroll bar scrolls it instead. A click anywhere else only closes it, so it can't press whatever the list
     * was covering.
     *
     * @return Whether the click was taken, which it always is while the list is open.
     */
    boolean listClicked(double mouseX, double mouseY) {
        if (!this.open) {
            return false;
        }
        int offset = this.scrollBar.mouseClicked(mouseX, mouseY, this.scroll);
        if (offset >= 0) {
            this.scroll = offset;
            return true;
        }
        int index = optionAt(mouseX, mouseY);
        this.open = false;
        if (index >= 0) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            this.value = this.options.get(index);
            this.onSelect.accept(this.value);
        }
        return true;
    }

    /** @return Whether the scroll was taken, which it is while the list is open and under the mouse. */
    boolean listScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isMouseOverList(mouseX, mouseY)) {
            return false;
        }
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    /** @return Whether the list's scroll bar is being dragged, and so took the drag. */
    boolean listDragged(double mouseY) {
        if (!this.scrollBar.isDragging()) {
            return false;
        }
        this.scroll = this.scrollBar.mouseDragged(mouseY);
        return true;
    }

    void listReleased() {
        this.scrollBar.mouseReleased();
    }

    private int optionAt(double mouseX, double mouseY) {
        if (!this.open || mouseX < getX() || mouseX >= getRight() || mouseY < getBottom() + 1) {
            return -1;
        }
        int row = (int) ((mouseY - getBottom() - 1) / OPTION_HEIGHT);
        return row < visibleCount() ? row + this.scroll : -1;
    }

    private int visibleCount() {
        return Math.min(this.options.size(), MAX_VISIBLE_OPTIONS);
    }

    private int maxScroll() {
        return Math.max(0, this.options.size() - MAX_VISIBLE_OPTIONS);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(": ").append(this.label.apply(this.value)));
    }
}
