package com.yungnickyoung.minecraft.yungsroads.debug.client;

import com.yungnickyoung.minecraft.yungsroads.debug.RoadTypeExport;
import com.yungnickyoung.minecraft.yungsroads.debug.RoadTypeNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A dialog over the tuning screen for saving a road type, as applied, to the world's tuned road type datapack. Saved
 * under its own id, it replaces the road type in this world. Saved under a new id, it's a new road type, which roads
 * only get once it's added to a road network, so the dialog says where.
 */
final class SaveRoadTypeScreen extends Screen {
    private static final int MAX_WIDTH = 300;
    private static final int PADDING = 8;
    private static final int BUTTON_HEIGHT = 20;
    /** Room for the explanation below the id, which changes with it. */
    private static final int EXPLANATION_LINES = 5;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int NOTE_COLOR = 0xFFA0A0A0;
    private static final int INVALID_COLOR = 0xFFFF5050;

    private final Screen parent;
    private final ResourceLocation typeId;
    private final Set<ResourceLocation> existing;
    /** Where a new road type's id must be added for the dimension's roads to get it. */
    private final Function<ResourceLocation, Component> whereToAdd;
    private final Consumer<ResourceLocation> onSave;

    private String id;
    private EditBox idBox;
    private Button saveButton;
    private int left;
    private int top;
    private int dialogWidth;
    private int dialogHeight;
    private List<FormattedCharSequence> descriptionLines = List.of();
    /** What saving under the id typed does, wrapped. Found when the id changes, since it looks up the road network. */
    private List<FormattedCharSequence> explanationLines = List.of();

    SaveRoadTypeScreen(Screen parent, ResourceLocation typeId, Set<ResourceLocation> existing, Function<ResourceLocation, Component> whereToAdd,
                       Consumer<ResourceLocation> onSave) {
        super(Component.translatable("yungsroads.screen.save.title", RoadTypeNames.styledName(typeId)));
        this.parent = parent;
        this.typeId = typeId;
        this.existing = existing;
        this.whereToAdd = whereToAdd;
        this.onSave = onSave;
        this.id = typeId.toString();
    }

    @Override
    protected void init() {
        if (this.parent.width != this.width || this.parent.height != this.height) {
            // The screen behind is drawn too, so it's laid out for the same size
            this.parent.init(this.minecraft, this.width, this.height);
        }
        this.dialogWidth = Math.min(MAX_WIDTH, this.width - 20);
        int innerWidth = this.dialogWidth - PADDING * 2;
        this.descriptionLines = this.font.split(Component.translatable("yungsroads.screen.save.description", RoadTypeExport.DATAPACK_NAME), innerWidth);
        this.dialogHeight = PADDING + 14 + this.descriptionLines.size() * 10 + 6 + 12 + 20 + 6 + EXPLANATION_LINES * 10 + 6 + BUTTON_HEIGHT + PADDING;
        this.left = (this.width - this.dialogWidth) / 2;
        this.top = Math.max(10, (this.height - this.dialogHeight) / 2);
        int x = this.left + PADDING;
        int y = this.top + PADDING + 14 + this.descriptionLines.size() * 10 + 6 + 12;

        this.idBox = addRenderableWidget(new EditBox(this.font, x, y, innerWidth, 16, Component.translatable("yungsroads.screen.save.id")));
        this.idBox.setMaxLength(256);
        this.idBox.setValue(this.id);
        this.idBox.setResponder(text -> {
            this.id = text;
            updateState();
        });
        setInitialFocus(this.idBox);

        int buttonWidth = (innerWidth - 4) / 2;
        int buttonY = this.top + this.dialogHeight - PADDING - BUTTON_HEIGHT;
        this.saveButton = addRenderableWidget(Button.builder(Component.translatable("yungsroads.screen.save.confirm"), button -> save())
                .bounds(x, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + buttonWidth + 4, buttonY, innerWidth - buttonWidth - 4, BUTTON_HEIGHT)
                .build());
        updateState();
    }

    /** The id typed, or null if it isn't a valid id. Without a namespace, it's in the road type's own. */
    @Nullable
    private ResourceLocation parsedId() {
        String text = this.id.trim();
        ResourceLocation id = text.contains(":") ? ResourceLocation.tryParse(text) : ResourceLocation.tryBuild(this.typeId.getNamespace(), text);
        return id == null || id.getPath().isEmpty() ? null : id;
    }

    private void updateState() {
        if (this.saveButton != null) {
            this.saveButton.active = parsedId() != null;
            this.idBox.setTextColor(parsedId() == null ? INVALID_COLOR : TEXT_COLOR);
            this.explanationLines = this.font.split(explanation(), this.dialogWidth - PADDING * 2);
        }
    }

    /** What saving under the id typed does. */
    private Component explanation() {
        ResourceLocation id = parsedId();
        if (id == null) {
            return Component.translatable("yungsroads.screen.save.invalid").withColor(INVALID_COLOR);
        }
        if (id.equals(this.typeId)) {
            return Component.translatable("yungsroads.screen.save.replaces_self", RoadTypeNames.styledId(id));
        }
        if (this.existing.contains(id)) {
            return Component.translatable("yungsroads.screen.save.replaces_other", RoadTypeNames.styledId(id), RoadTypeNames.styledId(this.typeId));
        }
        return Component.translatable("yungsroads.screen.save.new", RoadTypeNames.styledId(id)).append(" ").append(this.whereToAdd.apply(id));
    }

    private void save() {
        ResourceLocation id = parsedId();
        if (id != null) {
            this.onSave.accept(id);
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Drawn in render, over the screen behind
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        DialogFrame.begin(guiGraphics, this.parent, this.width, this.height, this.left, this.top, this.dialogWidth, this.dialogHeight, partialTick);
        int x = this.left + PADDING;
        int y = this.top + PADDING;
        guiGraphics.drawString(this.font, this.title, x, y, 0xFFFFFFFF);
        y += 14;
        for (FormattedCharSequence line : this.descriptionLines) {
            guiGraphics.drawString(this.font, line, x, y, NOTE_COLOR);
            y += 10;
        }
        guiGraphics.drawString(this.font, Component.translatable("yungsroads.screen.save.id"), x, this.idBox.getY() - 11, NOTE_COLOR, false);
        int explanationY = this.idBox.getBottom() + 6;
        for (FormattedCharSequence line : this.explanationLines.subList(0, Math.min(EXPLANATION_LINES, this.explanationLines.size()))) {
            guiGraphics.drawString(this.font, line, x, explanationY, TEXT_COLOR);
            explanationY += 10;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        DialogFrame.end(guiGraphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.saveButton.active) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
