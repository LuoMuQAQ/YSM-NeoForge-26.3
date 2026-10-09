package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.model.resource.client.ResourceFailure;
import com.elfmcys.ysm.proto.mixel.asset.model.ModelData;
import com.elfmcys.ysm.proto.mixel.asset.model.data.GeoModel;
import com.elfmcys.ysm.proto.mixel.asset.model.data.GeoProperties;
import com.elfmcys.ysm.util.ProtoUtil;
import com.elfmcys.ysm.util.ResourceTransaction;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRenderTargetLoaderTest {
    @Test
    void unpublishedResourcesCloseInReverseOrder() {
        var order = new ArrayList<Integer>();
        try (var resources = new ResourceTransaction()) {
            resources.own((AutoCloseable) () -> order.add(1));
            resources.own((AutoCloseable) () -> order.add(2));
        }
        assertEquals(List.of(2, 1), order);
    }

    @Test
    void successfulPublicationTransfersOwnershipExactlyOnce() {
        var order = new ArrayList<Integer>();
        var first = (AutoCloseable) () -> order.add(1);
        var second = (AutoCloseable) () -> order.add(2);
        var candidate = new TestCandidate(first, second);
        try (var resources = new ResourceTransaction()) {
            resources.own(first);
            resources.own(second);
            assertSame(candidate, ModelRenderTargetLoader.publishCandidate(
                    () -> false, resources, candidate));
        }
        assertTrue(order.isEmpty());

        candidate.close();
        candidate.close();
        assertEquals(List.of(2, 1), order);
    }

    @Test
    void cancellationAfterTransferClosesCandidateExactlyOnce() {
        var order = new ArrayList<Integer>();
        var first = (AutoCloseable) () -> order.add(1);
        var second = (AutoCloseable) () -> order.add(2);
        var candidate = new TestCandidate(first, second);
        var cancelled = new AtomicBoolean();
        try (var resources = new ResourceTransaction()) {
            resources.own(first);
            resources.own(second);
            cancelled.set(true);
            assertThrows(CancellationException.class, () ->
                    ModelRenderTargetLoader.publishCandidate(
                            cancelled::get, resources, candidate));
        }
        candidate.close();
        assertEquals(List.of(2, 1), order);
    }

    @Test
    void resolvesMainAndArmGeometryFromOneModelData() throws Exception {
        var main = GeoModel.newBuilder()
                .setProperties(GeoProperties.newBuilder()
                        .setTextureHeight(0)
                        .setTextureWidth(0)
                        .build())
                .setCubes(ByteBuffer.allocate(0))
                .build();
        var arm = GeoModel.newBuilder()
                .setProperties(GeoProperties.newBuilder()
                        .setTextureHeight(0)
                        .setTextureWidth(0)
                        .build())
                .setCubes(ByteBuffer.allocate(0))
                .build();
        var definition = ModelData.newBuilder();
        var mainEntry = geometry("main", main);
        var armEntry = geometry("arm", arm);
        definition.putGeoModels(mainEntry.getKey(), mainEntry.getValue());
        definition.putGeoModels(armEntry.getKey(), armEntry.getValue());
        var modelData = definition.build();

        assertArrayEquals(ProtoUtil.serializeToArray(main),
                ProtoUtil.serializeToArray(ModelRenderTargetLoader.geoModel(modelData, "main")));
        assertArrayEquals(ProtoUtil.serializeToArray(arm),
                ProtoUtil.serializeToArray(ModelRenderTargetLoader.geoModel(modelData, "arm")));
        var missing = assertThrows(AssetLoadException.class,
                () -> ModelRenderTargetLoader.geoModel(modelData, "missing"));
        assertEquals(AssetLoadException.Reason.CONTENT, missing.reason());
    }

    private static Map.Entry<String, ByteBuffer> geometry(
            String name, GeoModel model) throws Exception {
        return Map.entry(name,
                ByteBuffer.wrap(ProtoUtil.serializeToArray(model)));
    }

    private static final class TestCandidate implements AutoCloseable {
        private final List<AutoCloseable> resources;
        private final AtomicBoolean closed = new AtomicBoolean();

        private TestCandidate(AutoCloseable... resources) {
            this.resources = List.of(resources);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            for (var index = resources.size() - 1; index >= 0; index--) {
                try {
                    resources.get(index).close();
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }
        }
    }

    @Test
    void mapsAssetProvenanceAtTheLoaderBoundary() {
        assertEquals(ResourceFailure.Kind.TRANSIENT,
                ModelRenderTargetLoader.failure(AssetLoadException.access("offline"))
                        .failure().kind());
        assertEquals(ResourceFailure.Kind.DETERMINISTIC,
                ModelRenderTargetLoader.failure(AssetLoadException.content("invalid"))
                        .failure().kind());
        assertEquals(ResourceFailure.Kind.TRANSIENT,
                ModelRenderTargetLoader.failure(new CancellationException("cancelled"))
                        .failure().kind());
    }
}
