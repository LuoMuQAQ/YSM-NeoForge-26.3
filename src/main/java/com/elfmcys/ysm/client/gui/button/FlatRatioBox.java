// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FlatRatioBox extends AbstractWidget implements IConfigFormsButton {
    private final int maxHeight;

    public FlatRatioBox(int pX, int pY, int maxHeight, Component component) {
        super(pX, pY, 115, 15, component);
        this.maxHeight = maxHeight;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int pMouseX, int pMouseY, float pPartialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + maxHeight, 0xef_434242);
        extractScrollingStringOverContents(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE), getMessage(), 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
