// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class FlatCheckbox extends AbstractButton implements IConfigFormsButton {
    private static final Identifier BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");
    private final Consumer<Boolean> onClick;
    private final Component name;
    private boolean isStateTriggered;

    public FlatCheckbox(int xIn, int yIn, int width, Component name, Consumer<Boolean> onClick) {
        super(xIn, yIn, width, 12, name);
        this.name = name;
        this.onClick = onClick;
    }

    public FlatCheckbox(int xIn, int yIn, Component name, Consumer<Boolean> onClick) {
        this(xIn, yIn, 115, name, onClick);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, getX(), getY(),
                isStateTriggered ? 128 : 0, isHoveredOrFocused() ? 12 : 0, 12, 12, 256, 256);
        graphics.text(Minecraft.getInstance().font, name, this.getX() + 14, this.getY() + 2, 0xffffffff, false);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.isStateTriggered = !this.isStateTriggered;
        onClick.accept(this.isStateTriggered);
    }

    public void setStateTriggered(boolean selected) {
        this.isStateTriggered = selected;
    }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
