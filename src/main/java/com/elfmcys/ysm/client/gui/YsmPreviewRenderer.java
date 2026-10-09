package com.elfmcys.ysm.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class YsmPreviewRenderer extends PictureInPictureRenderer<YsmModelPreviewState> {
    @Override
    public Class<YsmModelPreviewState> getRenderStateClass() {
        return YsmModelPreviewState.class;
    }

    @Override
    protected void renderToTexture(YsmModelPreviewState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.scale() == 0.0F) {
            return;
        }
        poseStack.pushPose();
        try {
            // The host has already translated to the texture center and scaled by
            // guiScale * scale. Express the original screen anchor in that space.
            poseStack.translate((state.anchorX() - (state.x0() + state.x1()) / 2.0F) / state.scale(),
                    (state.anchorY() - (state.y0() + state.y1()) / 2.0F) / state.scale(), 0.0F);
            YsmPreviewDraw.submit(state, poseStack, collector);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "ysm-model";
    }
}
