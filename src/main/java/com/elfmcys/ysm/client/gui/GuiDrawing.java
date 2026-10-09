package com.elfmcys.ysm.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class GuiDrawing {
    private GuiDrawing() {
    }

    public static void nineSlice(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width, int height,
                                 int u, int v, int sourceWidth, int sourceHeight, int left, int top, int right, int bottom) {
        int[] dstX = {0, left, width - right, width};
        int[] dstY = {0, top, height - bottom, height};
        int[] srcX = {0, left, sourceWidth - right, sourceWidth};
        int[] srcY = {0, top, sourceHeight - bottom, sourceHeight};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int w = dstX[col + 1] - dstX[col];
                int h = dstY[row + 1] - dstY[row];
                if (w > 0 && h > 0) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + dstX[col], y + dstY[row], u + srcX[col], v + srcY[row],
                            w, h, srcX[col + 1] - srcX[col], srcY[row + 1] - srcY[row], 256, 256);
                }
            }
        }
    }
}
