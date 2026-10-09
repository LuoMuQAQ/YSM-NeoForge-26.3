package com.elfmcys.ysm.model.resource.client;

import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.resource.client.AcquireResult;
import com.elfmcys.ysm.model.resource.client.BakeProfile;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.resource.client.ResourceFailure;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.model.resource.client.ResourceRequest;
import com.elfmcys.ysm.proto.mixel.common.Image;
import com.elfmcys.ysm.proto.mixel.manifest.Manifest;
import com.elfmcys.ysm.proto.mixel.manifest.asset.Common;
import com.elfmcys.ysm.proto.mixel.manifest.asset.PBRTextureSet;
import com.elfmcys.ysm.proto.mixel.manifest.asset.RenderTarget;
import com.elfmcys.ysm.proto.mixel.manifest.asset.RenderTargetKind;
import com.elfmcys.ysm.proto.mixel.manifest.info.Info;
import com.elfmcys.ysm.proto.mixel.manifest.info.License;
import com.elfmcys.ysm.proto.mixel.manifest.info.Metadata;
import com.elfmcys.ysm.proto.mixel.manifest.info.ModelSettings;
import com.elfmcys.ysm.proto.mixel.manifest.info.ModelStats;
import com.elfmcys.ysm.proto.mixel.manifest.info.Properties;
import com.elfmcys.ysm.proto.mixel.manifest.info.Settings;
import com.elfmcys.ysm.util.UnsafeUtil;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelRenderTargetManagerTest {
    private static final BakeProfile PROFILE = new BakeProfile("test");

    @Test
    void requestedTextureWinsWhenPresent() {
        var request = request("requested");

        var key = ClientModelRenderTargetManager.resolveEffectiveKey(
                manifest("fallback", "player", "first", "requested", "fallback"), request);

        assertEquals("requested", key.selectedTexture());
        assertEquals("requested", request.requestedTexture());
    }

    @Test
    void missingRequestedTextureFallsThroughToValidDefaultWithoutMutatingRequest() {
        var request = request("server-name");

        var key = ClientModelRenderTargetManager.resolveEffectiveKey(
                manifest("fallback", "player", "first", "fallback"), request);

        assertEquals("fallback", key.selectedTexture());
        assertEquals("server-name", request.requestedTexture());
    }

    @Test
    void invalidDefaultFallsThroughToFirstManifestTexture() {
        var key = ClientModelRenderTargetManager.resolveEffectiveKey(
                manifest("missing", "player", "first", "second"), request("server-name"));

        assertEquals("first", key.selectedTexture());
    }

    @Test
    void unknownAndTexturelessTargetsRemainDeterministicFailures() {
        var unknown = assertThrows(IllegalArgumentException.class, () ->
                ClientModelRenderTargetManager.resolveEffectiveKey(
                        manifest(null, "projectile", "only"), request("server-name")));
        assertEquals("Unknown render target: player", unknown.getMessage());

        var textureless = assertThrows(IllegalArgumentException.class, () ->
                ClientModelRenderTargetManager.resolveEffectiveKey(
                        manifest(null, "projectile"),
                        new ResourceRequest(hash(), "projectile", "server-name", PROFILE)));
        assertEquals("Render target has no texture: projectile", textureless.getMessage());
    }

    @Test
    void identicalResolutionConvergesAndChangedRepresentationInvalidatesOldKey() {
        var request = request("server-name");
        var firstRepresentation = manifest(null, "player", "first");
        var nextRepresentation = manifest(null, "player", "next");

        var first = ClientModelRenderTargetManager.resolveEffectiveKey(
                firstRepresentation, request);

        assertEquals(first, ClientModelRenderTargetManager.resolveEffectiveKey(
                firstRepresentation, request));
        assertNotEquals(first, ClientModelRenderTargetManager.resolveEffectiveKey(
                nextRepresentation, request));
        assertEquals("server-name", request.requestedTexture());
    }

    @Test
    void readyAcquisitionCancellationClosesBeforeReturningAndRejectsLateReady() {
        var lease = new TrackingLease(true);
        var terminal = new CompletableFuture<AcquireResult>();
        var acquisition = new ClientModelRenderTargetManager.ReadyAcquisition(
                request(""), lease, terminal);
        var result = acquisition.result().toCompletableFuture();

        acquisition.cancel();

        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, lease.cancellations.get());
        assertEquals(1, lease.closes.get());
        assertTrue(terminal.complete(new AcquireResult.Ready(target())));
        assertEquals(1, lease.closes.get());
    }

    @Test
    void readyAcquisitionClosesEveryNonDeliveredTerminalBeforePublication() {
        assertRejected(new AcquireResult.Pending(), null, true);
        assertRejected(new AcquireResult.Failed(new ResourceFailure(
                ResourceFailure.Kind.TRANSIENT, new IllegalStateException("failed"))),
                null, true);
        assertRejected(new AcquireResult.Ready(target()), null, false);
        assertRejected(null, new CancellationException("owner closed"), true);
    }

    @Test
    void readyAcquisitionTransfersCurrentReadyLeaseWithoutClosingIt() {
        var lease = new TrackingLease(true);
        var terminal = new CompletableFuture<AcquireResult>();
        var acquisition = new ClientModelRenderTargetManager.ReadyAcquisition(
                request(""), lease, terminal);
        var result = acquisition.result().toCompletableFuture();

        terminal.complete(new AcquireResult.Ready(target()));

        assertSame(lease, result.join());
        assertFalse(result.cancel(false));
        assertEquals(0, lease.closes.get());
        lease.close();
        assertEquals(1, lease.closes.get());
    }

    private static void assertRejected(AcquireResult outcome, Throwable failure,
                                       boolean current) {
        var lease = new TrackingLease(current);
        var terminal = new CompletableFuture<AcquireResult>();
        var acquisition = new ClientModelRenderTargetManager.ReadyAcquisition(
                request(""), lease, terminal);
        var result = acquisition.result().toCompletableFuture();
        var closesAtCompletion = new AtomicInteger();
        result.whenComplete((ignored, ignoredFailure) ->
                closesAtCompletion.set(lease.closes.get()));

        if (failure == null) {
            terminal.complete(outcome);
        } else {
            terminal.completeExceptionally(failure);
        }

        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, closesAtCompletion.get());
        assertEquals(1, lease.closes.get());
    }

    private static ResourceRequest request(String requestedTexture) {
        return new ResourceRequest(hash(), "player", requestedTexture, PROFILE);
    }

    private static Manifest manifest(
            String defaultTexture, String targetId, String... textures) {
        var target = RenderTarget.newBuilder()
                .setTargetId(targetId)
                .setKind(targetId.equals("player")
                        ? RenderTargetKind.RENDER_TARGET_KIND_PLAYER
                        : RenderTargetKind.RENDER_TARGET_KIND_PROJECTILE)
                .setBlobId(0)
                .setSettings(ModelSettings.newBuilder()
                        .setHeightScale(0)
                        .setWidthScale(0)
                        .setRenderLayersFirst(false)
                        .setForceCulling(false)
                        .setGuiNoLighting(false)
                        .setMergeMultilineExpr(false)
                        .build())
                .setStats(ModelStats.newBuilder()
                        .setBones(0)
                        .setCubes(0)
                        .setFaces(0)
                        .build());
        for (var texture : textures) {
            target.putTextures(texture, PBRTextureSet.newBuilder()
                    .setUv(emptyImage()).build());
        }

        var settings = Settings.newBuilder()
                .setDefaultTexture("")
                .setPreviewAnimation("")
                .setDisablePreviewRotation(false);
        if (defaultTexture != null) {
            settings.setDefaultTexture(defaultTexture);
        }
        var info = Info.newBuilder()
                .setSettings(settings.build())
                .setMetadata(Metadata.newBuilder()
                        .setName("")
                        .setTips("")
                        .setLicense(License.newBuilder()
                                .setType("")
                                .setDesc("")
                                .build())
                        .build())
                .setProperties(Properties.newBuilder()
                        .setModelId(ByteBuffer.wrap(new byte[Hash256.SIZE]))
                        .setFree(false)
                        .setOriginVer("")
                        .build())
                .build();
        return Manifest.newBuilder()
                .addRenderTargets(target.build())
                .setCommonAssets(Common.newBuilder()
                        .setStringsBlobId(0)
                        .build())
                .setInfo(info)
                .build();
    }

    private static Image emptyImage() {
        return Image.newBuilder()
                .setBlobId(0).setFormat("").setWidth(0).setHeight(0).setFrameCount(0)
                .build();
    }

    private static Hash256 hash() {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = 1;
        return new Hash256(bytes);
    }

    private static ModelRenderTarget target() {
        try {
            return (ModelRenderTarget) UnsafeUtil.getUnsafe()
                    .allocateInstance(ModelRenderTarget.class);
        } catch (InstantiationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class TrackingLease implements ResourceLease {
        private final boolean current;
        private final AtomicInteger cancellations = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        private TrackingLease(boolean current) {
            this.current = current;
        }

        @Override
        public AcquireResult poll() {
            return new AcquireResult.Pending();
        }

        @Override
        public boolean isCurrent(ResourceRequest request) {
            return current;
        }

        @Override
        public void cancelPending() {
            cancellations.incrementAndGet();
            close();
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
