package com.elfmcys.ysm.natives.render;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.geckolib3.geo.animated.GeoModelState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Captures native vertices at submit time and replays them when the host
 * consumes custom geometry. The callback does not read model state, leases,
 * or the global projection.
 */
public final class DeferredModelDraw {
    private static boolean warnedMissingProjection;

    private DeferredModelDraw() {
    }

    public static boolean submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
                              GeoModelState modelState, int light, int overlay, int argb, RenderContextType contextType,
                              int outlineColor) {
        if (collector == null || poseStack == null || renderType == null || modelState == null || !modelState.isValid()) {
            return false;
        }
        if (!HostProjection.hasCapture()) {
            if (!warnedMissingProjection) {
                warnedMissingProjection = true;
                YesSteveModel.LOGGER.warn("Skipped a YSM model draw because the bound projection slice has no CPU matrix.");
            }
            return false;
        }
        var vertices = NativeRenderer.capture(
                poseStack.last(), modelState.getNativeState(), modelState.getVertexCount(),
                light, overlay, argb, contextType);
        if (vertices == null) {
            return false;
        }
        var vertexCount = modelState.getVertexCount();
        boolean submitted = false;
        if (!renderType.isOutline()) {
            collector.submitCustomGeometry(poseStack, renderType, (ignoredPose, consumer) ->
                    FallbackVertexWriter.writeOwned(consumer, vertices, vertexCount, overlay));
            submitted = true;
        }
        if (outlineColor != 0) {
            var outline = renderType.isOutline() ? java.util.Optional.of(renderType) : renderType.outline();
            if (outline.isPresent()) {
                collector.submitCustomGeometry(poseStack, outline.get(), (ignoredPose, consumer) ->
                        FallbackVertexWriter.writeOutline(consumer, vertices, vertexCount, outlineColor));
                submitted = true;
            }
        }
        return submitted;
    }
}
