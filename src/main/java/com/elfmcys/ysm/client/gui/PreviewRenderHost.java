// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.elfmcys.ysm.model.resource.client.AcquireResult;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.model.resource.client.ResourceRequest;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Draws one already-published player target into a private 256×256 texture and reads the pixels back. */
public final class PreviewRenderHost {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 256;

    private PreviewRenderHost() {
    }

    @FunctionalInterface
    public interface Completion {
        void complete(@Nullable NativeImage pixels, @Nullable Throwable failure);
    }

    public static void render(ResourceRequest request, ResourceLease lease, Completion completion) {
        RenderSystem.assertOnRenderThread();
        AtomicBoolean completed = new AtomicBoolean();
        CustomGuiPlayerEntity entity = null;
        try {
            var acquired = lease.poll();
            if (!(acquired instanceof AcquireResult.Ready ready)) {
                throw new IllegalArgumentException("Preview rendering requires a ready target");
            }
            var target = ready.target();
            if (target.playerResources() == null) {
                throw new IllegalArgumentException("Preview rendering requires a player target");
            }
            entity = new CustomGuiPlayerEntity();
            entity.installPreviewResource(request, new BorrowedTargetLease(request, target));
            var guiEntity = entity;
            draw(guiEntity, target, (pixels, failure) -> {
                if (completed.compareAndSet(false, true)) {
                    release(guiEntity, null);
                    completion.complete(pixels, failure);
                }
            });
        } catch (Throwable failure) {
            if (completed.compareAndSet(false, true)) {
                release(entity, failure);
                completion.complete(null, failure);
            }
        }
    }

    private static void draw(CustomGuiPlayerEntity entity, ModelRenderTarget target, Completion completion) {
        var minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.gameRenderer.mainRenderTarget();
        var mainDepth = mainTarget.getDepthTexture();
        if (mainDepth == null) {
            throw new IllegalStateException("Preview rendering requires the main depth texture");
        }
        var modelView = RenderSystem.getModelViewStack();
        TextureTarget offscreen = null;
        ProjectionMatrixBuffer projectionBuffer = null;
        ReadbackResources readback = null;
        Throwable primaryFailure = null;
        boolean submitted = false;
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        modelView.identity();
        try {
            offscreen = new TextureTarget("ysm-preview", WIDTH, HEIGHT, GpuFormat.RGBA8_UNORM, mainDepth.getFormat());
            var device = RenderSystem.getDevice();
            device.createCommandEncoder().clearColorAndDepthTextures(
                    offscreen.getColorTexture(), new Vector4f(0.0F), offscreen.getDepthTexture(), 0.0);
            var projection = new Projection();
            projection.setupOrtho(-1000.0F, 1000.0F, WIDTH, HEIGHT, true);
            projectionBuffer = new ProjectionMatrixBuffer("ysm-preview");
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

            var poseStack = new PoseStack();
            poseStack.translate(WIDTH / 2.0F, HEIGHT * 0.68F, 0.0F);
            poseStack.scale(88.0F, 88.0F, -88.0F);
            var storage = new SubmitNodeStorage();
            var state = YsmModelPreviewState.model(
                    entity, RegisterEntityRenderersEvent.getPlayerRenderer(), com.elfmcys.ysm.util.RenderUtil.guiPartialTick(),
                    target.info().getSettings().disablePreviewRotation(), true,
                    WIDTH / 2.0F, HEIGHT * 0.68F, 0, 0, WIDTH, HEIGHT, 88.0F, null);
            YsmPreviewDraw.submit(state, poseStack, storage);
            try (var frame = minecraft.gameRenderer.featureRenderDispatcher().prepareFrame(storage);
                 var pass = device.createCommandEncoder().createRenderPass(
                         () -> "ysm preview", offscreen.getColorTextureView(), Optional.empty(),
                         offscreen.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                FeatureRenderDispatcher.renderAllFeatures(pass, frame);
            }
            readback = new ReadbackResources(offscreen, projectionBuffer);
            var owner = readback;
            offscreen = null;
            projectionBuffer = null;
            net.minecraft.client.Screenshot.takeScreenshot(owner.target, image -> finishReadback(owner, image, completion));
            submitted = true;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable cleanupFailure = null;
            if (!submitted) {
                cleanupFailure = closeTarget(offscreen, null);
                cleanupFailure = closeProjection(projectionBuffer, cleanupFailure);
                if (readback != null) {
                    cleanupFailure = readback.closeOnce(cleanupFailure);
                }
            }
            try {
                RenderSystem.restoreProjectionMatrix();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
            try {
                modelView.popMatrix();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
            if (cleanupFailure != null) {
                YesSteveModel.LOGGER.error("YSM preview cleanup failed", cleanupFailure);
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else if (!submitted) {
                    if (cleanupFailure instanceof Error error) {
                        throw error;
                    }
                    throw (RuntimeException) cleanupFailure;
                }
            }
            if (primaryFailure != null) {
                YesSteveModel.LOGGER.error("YSM preview render failed", primaryFailure);
            }
        }
    }

    private static void finishReadback(ReadbackResources resources, NativeImage image, Completion completion) {
        if (!resources.terminal.compareAndSet(false, true)) {
            image.close();
            return;
        }
        Throwable failure = resources.closeResources(null);
        if (failure != null) {
            YesSteveModel.LOGGER.error("YSM preview readback cleanup failed", failure);
            try {
                image.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            image = null;
        }
        completion.complete(image, failure);
    }

    /** Shared by synchronous rollback and the possibly late readback callback. */
    private static final class ReadbackResources {
        private final RenderTarget target;
        private final ProjectionMatrixBuffer projectionBuffer;
        private final AtomicBoolean terminal = new AtomicBoolean();

        private ReadbackResources(RenderTarget target, ProjectionMatrixBuffer projectionBuffer) {
            this.target = target;
            this.projectionBuffer = projectionBuffer;
        }

        private @Nullable Throwable closeOnce(@Nullable Throwable failure) {
            return terminal.compareAndSet(false, true) ? closeResources(failure) : failure;
        }

        private @Nullable Throwable closeResources(@Nullable Throwable failure) {
            return closeProjection(projectionBuffer, closeTarget(target, failure));
        }
    }

    private static void release(@Nullable CustomGuiPlayerEntity entity, @Nullable Throwable failure) {
        if (entity == null) {
            return;
        }
        try {
            entity.reset();
        } catch (RuntimeException | Error resetFailure) {
            YesSteveModel.LOGGER.error("YSM preview entity reset failed", resetFailure);
            if (failure != null) {
                failure.addSuppressed(resetFailure);
            }
        }
    }

    private static @Nullable Throwable closeTarget(@Nullable RenderTarget target, @Nullable Throwable failure) {
        if (target == null) {
            return failure;
        }
        try {
            target.destroyBuffers();
            return failure;
        } catch (RuntimeException | Error closeFailure) {
            return append(failure, closeFailure);
        }
    }

    private static @Nullable Throwable closeProjection(@Nullable ProjectionMatrixBuffer buffer, @Nullable Throwable failure) {
        if (buffer == null) {
            return failure;
        }
        try {
            buffer.close();
            return failure;
        } catch (RuntimeException | Error closeFailure) {
            return append(failure, closeFailure);
        }
    }

    private static Throwable append(@Nullable Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }

    private record BorrowedTargetLease(ResourceRequest request, ModelRenderTarget target) implements ResourceLease {
        @Override
        public AcquireResult poll() {
            return new AcquireResult.Ready(target);
        }

        @Override
        public boolean isCurrent(ResourceRequest candidate) {
            return request.equals(candidate);
        }

        @Override
        public void cancelPending() {
        }

        @Override
        public void close() {
        }
    }
}
