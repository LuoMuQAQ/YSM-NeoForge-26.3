// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.model.catalog.client.entry.ClientFailedCatalogEntry;
import com.elfmcys.ysm.util.ModelIdUtil;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Disabled path-only card for a publication entry whose metadata did not activate. */
public final class FailedCatalogModelButton extends Button {
    private final ClientFailedCatalogEntry entry;

    public FailedCatalogModelButton(int x, int y, ClientFailedCatalogEntry entry) {
        super(x, y, 52, 90,
                Component.literal(ModelIdUtil.getFileNameFromPath(entry.displayPath())),
                ignored -> { }, DEFAULT_NARRATION);
        this.entry = entry;
        active = false;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(getX(), getY(), getX() + width, getY() + height, 0xFF2E2E2E, 0xFF2E2E2E);
        graphics.centeredText(Minecraft.getInstance().font, Component.literal("!"), getX() + width / 2,
                getY() + (height - 20) / 2, 0xFFE57373);
        var lines = Minecraft.getInstance().font.split(getMessage(), 45);
        var lineY = getY() + height - (lines.size() > 1 ? 19 : 15);
        for (var line : lines.stream().limit(2).toList()) {
            graphics.centeredText(Minecraft.getInstance().font, line, getX() + width / 2, lineY, 0xFF9E9E9E);
            lineY += 9;
        }
    }

    public void renderTooltip(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        if (mouseX < getX() || mouseX >= getX() + width
                || mouseY < getY() || mouseY >= getY() + height) {
            return;
        }
        graphics.setComponentTooltipForNextFrame(screen.getMinecraft().font, List.of(
                Component.literal(entry.displayPath()).withStyle(ChatFormatting.GRAY),
                Component.literal(entry.error()).withStyle(ChatFormatting.RED)),
                mouseX, mouseY);
    }
}
