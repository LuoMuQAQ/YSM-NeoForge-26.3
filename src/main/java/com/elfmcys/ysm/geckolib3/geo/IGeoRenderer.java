// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo;

import com.elfmcys.ysm.geckolib3.core.util.Color;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.elfmcys.ysm.natives.render.DeferredModelDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    default void preRender(GeoRenderData data, T animatable, PoseStack poseStack, int packedLight, int packedOverlay, Color color) {
        poseStack.scale(data.widthScale, data.heightScale, data.widthScale);
    }

    default void render(GeoRenderData data, T animatable, RenderType type, PoseStack poseStack,
                         SubmitNodeCollector collector, int packedLight, int packedOverlay, Color color, int outlineColor) {
        DeferredModelDraw.submit(
                collector, poseStack, type, data.modelState,
                packedLight, packedOverlay, color.getColor(), data.ctx.nativeType(), outlineColor);
    }

    default void postRender(GeoRenderData data, T animatable, PoseStack poseStack, int packedLight, int packedOverlay, Color color) {
        animatable.countRender();
    }

    @Nullable
    default RenderType getRenderType(Identifier texture, boolean visible, boolean glowing, boolean translucent) {
        if (visible) {
            return translucent ? CustomTranslucentRenderType.create(texture) : RenderTypes.entityCutout(texture);
        }
        return glowing ? RenderTypes.outline(texture) : null;
    }
}
