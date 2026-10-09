package com.elfmcys.ysm.natives.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.joml.Matrix4f;

/** Render-thread CPU matrices associated with their successfully uploaded UBO slices. */
public final class HostProjection {
    // Values never retain their keys; closed PIP buffers can leave the registry naturally.
    private static final Map<GpuBufferSlice, Matrix4f> MATRICES = new WeakHashMap<>();

    private HostProjection() {
    }

    public static void capture(GpuBufferSlice slice, Matrix4f matrix) {
        RenderSystem.assertOnRenderThread();
        MATRICES.computeIfAbsent(slice, ignored -> new Matrix4f()).set(matrix);
    }

    public static boolean hasCapture() {
        return MATRICES.containsKey(RenderSystem.getProjectionMatrixBuffer());
    }

    public static Matrix4f current() {
        return Objects.requireNonNull(MATRICES.get(RenderSystem.getProjectionMatrixBuffer()),
                "The bound projection slice has no CPU matrix");
    }

    public static Matrix4f copyForNative(Matrix4f destination) {
        var projection = current();
        destination.set(projection);
        // 26.3 uses reversed depth. Native sorts descending z/w, so negate only
        // clip z. Culling uses clip x/y/w and vertex positions do not use projection.
        return destination.m02(-projection.m02()).m12(-projection.m12())
                .m22(-projection.m22()).m32(-projection.m32());
    }
}
