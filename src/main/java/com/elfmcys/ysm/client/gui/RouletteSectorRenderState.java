package com.elfmcys.ysm.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** Owned geometry and transform for one extracted roulette sector. */
final class RouletteSectorRenderState implements GuiElementRenderState {
    private final Matrix3x2f pose;
    private final float[] points;
    private final int color;
    private final @Nullable ScreenRectangle scissor;
    private final @Nullable ScreenRectangle bounds;

    RouletteSectorRenderState(GuiGraphicsExtractor graphics, int x, int y, float inner, float outer,
                               float start, float end, int color) {
        this.pose = new Matrix3x2f(graphics.pose());
        this.color = color;
        this.scissor = graphics.peekScissorStack();
        float cosStart = (float) Math.cos(start);
        float sinStart = (float) Math.sin(start);
        float cosEnd = (float) Math.cos(end);
        float sinEnd = (float) Math.sin(end);
        this.points = new float[]{x + outer * cosStart, y + outer * sinStart,
                x + inner * cosStart, y + inner * sinStart,
                x + inner * cosEnd, y + inner * sinEnd,
                x + outer * cosEnd, y + outer * sinEnd};
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        int left = (int) Math.floor(minX), top = (int) Math.floor(minY);
        var area = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top)
                .transformMaxBounds(pose);
        this.bounds = scissor == null ? area : scissor.intersection(area);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < points.length; i += 2) {
            consumer.addVertexWith2DPose(pose, points[i], points[i + 1]).setColor(color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }
}
