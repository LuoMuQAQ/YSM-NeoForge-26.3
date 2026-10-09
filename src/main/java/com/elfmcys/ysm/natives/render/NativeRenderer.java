// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.natives.render;

import com.elfmcys.ysm.buffer.NativeBuffer;
import com.elfmcys.ysm.buffer.annotation.Aligned;
import com.elfmcys.ysm.buffer.annotation.Borrowed;
import com.elfmcys.ysm.client.compat.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.lang.ref.Reference;
import java.nio.ByteOrder;

public class NativeRenderer {
    public static void render(VertexConsumer vertexConsumer, PoseStack.Pose pose,
                              NativeModelState modelState, int vertexCount,
                              int light, int overlay, int color, RenderContextType contextType) {
        var vertices = capture(pose, modelState, vertexCount, light, overlay, color, contextType);
        if (vertices != null) {
            FallbackVertexWriter.writeOwned(vertexConsumer, vertices, vertexCount, overlay);
        }
    }

    /**
     * Renders into an owned fallback vertex copy while the model state and this pass's
     * matrices are still valid. Returns null when native render fails or this pass has
     * no CPU matrix for its bound projection. A missing projection is never treated as identity.
     */
    public static int[] capture(PoseStack.Pose pose, NativeModelState modelState, int vertexCount,
                                int light, int overlay, int color, RenderContextType contextType) {
        if (!HostProjection.hasCapture() || vertexCount <= 0 || modelState == null || !modelState.isValid()) {
            return null;
        }
        var matBuffer = getMatBuffer(pose);
        var vertexData = FallbackVertexWriter.getVertexData(vertexCount);
        final boolean result;
        try {
            result = nRender(vertexData, 0, matBuffer.ptr(), modelState.get(),
                    packLightAndOverlay(light, overlay), packColor(color),
                    packFlags(VertexFormatType.FALLBACK, contextType), IrisCompat.getEntityId());
        } finally {
            Reference.reachabilityFence(vertexData);
            Reference.reachabilityFence(matBuffer);
            Reference.reachabilityFence(modelState);
        }
        return result ? FallbackVertexWriter.copyVertexData(vertexCount) : null;
    }

    @Borrowed
    @Aligned(64)
    public static NativeBuffer getMatBuffer(PoseStack.Pose pose) {
        var buffer = MatBufferHolder.BUFFER;
        // NIO duplicate views reset their byte order. These matrices cross JNI
        // as native floats, so explicitly select the native order for every view.
        var buf = buffer.nio().order(ByteOrder.nativeOrder());

        var view = RenderSystem.getModelViewStack();
        var proj = HostProjection.copyForNative(MatBufferHolder.PROJECTION);

        pose.pose().get(buf);
        view.get(64, buf);
        proj.get(128, buf);
        pose.normal().get(192, buf);

        return buffer;
    }

    private static long packFlags(VertexFormatType v, RenderContextType c) {
        return ((long) v.id() << 2) | c.id();
    }

    private static long packLightAndOverlay(int lightUv, int overlayOv) {
        return ((long) lightUv << 32) | overlayOv;
    }

    static int packColor(int argb) {
        return ((argb >>> 16) & 0xFF) |
                (argb & 0xFF00) |
                ((argb & 0xFF) << 16) |
                (argb & 0xFF000000);
    }

    private static native boolean nRender(Object vertexBuffer, int vertexBufferFlag, long matPtr, long modelStatePtr,
                                          long lightAndOverlay, int color, long flags, long irisEntityId);

    private static final class MatBufferHolder {
        private static final NativeBuffer BUFFER = NativeBuffer.allocate(256, 64);
        private static final Matrix4f PROJECTION = new Matrix4f();
    }

}
