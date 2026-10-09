package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.natives.render.HostProjection;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectionMatrixBuffer.class)
abstract class ProjectionMatrixBufferMixin {
    @Inject(method = "writeBuffer", at = @At("RETURN"))
    private void ysm$captureUploadedProjection(Matrix4f projectionMatrix,
                                               CallbackInfoReturnable<GpuBufferSlice> callback) {
        HostProjection.capture(callback.getReturnValue(), projectionMatrix);
    }
}
