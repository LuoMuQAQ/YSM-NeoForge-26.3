// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo;

import com.elfmcys.ysm.geckolib3.core.util.Color;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;

public abstract class GeoEntityRenderer<TEntity extends Entity, T extends AnimatableEntity<TEntity>> implements IGeoRenderer<T> {
    public void submitAnimatable(T animatable, float yaw, float partialTick, PoseStack poseStack,
                                 SubmitNodeCollector collector, int packedLight, int outlineColor) {
        var data = animatable.update(partialTick);
        if (data == null) {
            return;
        }
        var entity = animatable.getEntity();
        var bodyVisible = !entity.isInvisible();
        var glowing = outlineColor != 0;
        var renderType = getRenderType(data.texture, bodyVisible, glowing, data.modelState.hasTranslucentVertices());
        if (renderType == null || (!bodyVisible && !glowing)) {
            return;
        }
        var overlay = OverlayTexture.NO_OVERLAY;
        poseStack.pushPose();
        try {
            poseStack.rotateDegrees(Axis.YP, 180.0F - yaw);
            preRender(data, animatable, poseStack, packedLight, overlay, Color.WHITE);
            if (data.modelState.isValid()) {
                render(data, animatable, renderType, poseStack, collector, packedLight, overlay, Color.WHITE, outlineColor);
            }
            postRender(data, animatable, poseStack, packedLight, overlay, Color.WHITE);
        } finally {
            poseStack.popPose();
        }
    }
}
