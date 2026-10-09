package com.elfmcys.ysm.network.dispatch;

import com.elfmcys.ysm.format.container.AssetContainerConstant;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.schema.model.ModelFileIdentityReader;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.content.AssetRef;
import com.elfmcys.ysm.model.catalog.content.CatalogContentBinding;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogEntry;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogIndexEntry;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogPresentation;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogRecord;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.domain.ModelScanReport;
import com.elfmcys.ysm.model.resource.server.ServerChunkRuntime;
import com.elfmcys.ysm.model.session.server.ServerModelSession;
import com.elfmcys.ysm.model.storage.ManagedContainer;
import com.elfmcys.ysm.model.storage.ModelExporter;
import com.elfmcys.ysm.model.storage.PreviewStore;
import com.elfmcys.ysm.model.storage.TestPreviews;
import com.elfmcys.ysm.network.ProtocolBuffer;
import com.elfmcys.ysm.network.frame.FrameCodec;
import com.elfmcys.ysm.network.frame.OutboundFrame;
import com.elfmcys.ysm.network.protocol.ProtocolMessages;
import com.elfmcys.ysm.proto.network.ChunkFragment;
import com.elfmcys.ysm.proto.network.ChunkMember;
import com.elfmcys.ysm.proto.network.MetadataPrefixFragment;
import com.elfmcys.ysm.proto.network.MetadataPrefixRequest;
import com.elfmcys.ysm.proto.network.ModelChunkRequest;
import com.elfmcys.ysm.proto.network.PresentationPageRequest;
import com.elfmcys.ysm.proto.network.PreviewFragment;
import com.elfmcys.ysm.proto.network.PreviewMember;
import com.elfmcys.ysm.proto.network.ResourceFailureReason;
import com.elfmcys.ysm.proto.network.ResourceTransferFailure;
import com.elfmcys.ysm.util.ProtoBytes;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTypedResourceFlowTest {
    @TempDir
    static Path fixtureRoot;

    @TempDir
    Path testRoot;

    private static Path fixture;
    private static Path fixtureWithoutPreview;
    private ManagedContainer content;
    private CatalogSnapshot catalog;
    private ServerChunkRuntime runtime;

    @BeforeAll
    static void createFixture() throws Exception {
        var manifest = ServerTypedResourceFlowTest.class.getResource(
                "/assets/ysm/builtin/default/ysm.json");
        var source = Path.of(Objects.requireNonNull(manifest).toURI()).getParent();
        try (var vfs = new Directory(source)) {
            var parsed = ModelParser.parseBuiltinDefault(vfs,
                    Files.createDirectories(fixtureRoot.resolve("model")));
            fixtureWithoutPreview = parsed;
            var location = new CatalogModelLocation(CatalogRootKind.AUTH,
                    new ModelPath("fixture"));
            var content = ManagedContainer.openDirect(parsed, location);
            try {
                fixture = ModelExporter.export(content,
                        TestPreviews.blank(),
                        fixtureRoot.resolve("model-with-preview.mxc"), null);
            } finally {
                content.representation().close();
            }
        }
    }

    @BeforeEach
    void openContent() throws Exception {
        runtime = new ServerChunkRuntime(ignored -> { });
        var file = Files.copy(fixture, testRoot.resolve("model.mxc"));
        final ModelFileIdentity identity;
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            identity = ModelFileIdentityReader.read(channel);
        }
        var location = new CatalogModelLocation(CatalogRootKind.AUTH,
                new ModelPath("fixture"));
        content = ManagedContainer.openIndexed(new CatalogIndexEntry(identity, location, file));
        var record = new CatalogRecord(location,
                new CatalogContentBinding(identity.modelId(), content));
        catalog = new CatalogSnapshot(Map.of(identity.modelId(), record), List.of(),
                ModelScanReport.empty());
    }

    @Test
    void descriptorlessPreviewFallsBackToTheContainerIdCache() throws Exception {
        var file = Files.copy(fixtureWithoutPreview, testRoot.resolve("cache-source.mxc"));
        var location = new CatalogModelLocation(CatalogRootKind.AUTH,
                new ModelPath("cache-fixture"));
        var cachedContent = ManagedContainer.openDirect(file, location);
        try {
            var identity = cachedContent.representation().identity();
            var cachedCatalog = new CatalogSnapshot(Map.of(identity.modelId(),
                    new CatalogRecord(location,
                            new CatalogContentBinding(identity.modelId(), cachedContent))),
                    List.of(), ModelScanReport.empty());
            var owner = active(cachedCatalog);
            var previews = new PreviewStore(testRoot.resolve("preview-cache"));
            var expected = TestPreviews.blank();
            previews.accept(identity.containerId(), expected.bytes());
            var failures = new ArrayList<ResourceTransferFailure>();
            var port = new RecordingPort();
            try (var worker = new ResourceDispatchWorker(1, 8, 0);
                 var transfers = new ServerAssetTransfers(owner, worker, port,
                         () -> false, failures::add, runtime, previews, Runnable::run)) {
                var request = com.elfmcys.ysm.proto.network.PresentationPageRequest
                        .newBuilder().setDataTransferId(1)
                        .addPreviews(PreviewMember.newBuilder()
                                .setSlot(0).setModelId(ProtoBytes.wrap(identity.modelId()))
                                .setContainerId(ProtoBytes.wrap(identity.containerId()))
                                .build()).build();
                assertTrue(transfers.accept(request));
                awaitQueued(worker, owner, transfers);
                drain(worker, owner, transfers);
                assertTrue(failures.isEmpty());
                var received = 0;
                for (var frame : port.take()) {
                    try (var decoded = FrameCodec.decode(frame,
                            id -> id == ProtocolMessages.PREVIEW_FRAGMENT_ID)) {
                        var message = ProtocolBuffer.parse(decoded.protobuf(),
                                PreviewFragment::parseFrom);
                        assertEquals(com.elfmcys.ysm.proto.network.PresentationDelivery
                                .PRESENTATION_DELIVERY_DATA, message.delivery());
                        received += decoded.attachment().size();
                    }
                }
                assertEquals(expected.bytes().length, received);
            }
        } finally {
            cachedContent.representation().close();
        }
    }

    @AfterEach
    void closeRuntime() {
        runtime.close();
    }

    @Test
    void metadataAndChunkRequestsProduceTheirTypedFragments() {
        var owner = active(catalog);
        var failures = new ArrayList<ResourceTransferFailure>();
        var port = new RecordingPort();
        try (var worker = new ResourceDispatchWorker(1, 8, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            var identity = content.representation().identity();
            assertTrue(transfers.accept(MetadataPrefixRequest.newBuilder()
                    .setDataTransferId(1).addContainerIds(ProtoBytes.wrap(identity.containerId())).build()));
            drain(worker, owner, transfers);
            assertTrue(failures.isEmpty());
            assertFalse(port.frames.isEmpty());
            assertMetadataFrames(port.take(), identity);

            var chunk = content.modelFile().getFileView().getAssetView().getChunkTable()
                    .values().stream()
                    .filter(value -> !value.type().equals(
                            AssetContainerConstant.VERIFICATION_CHUNK_TYPE))
                    .findFirst().orElseThrow();
            var request = ModelChunkRequest.newBuilder()
                    .setDataTransferId(2).setModelId(ProtoBytes.wrap(identity.modelId()))
                    .setContainerId(ProtoBytes.wrap(identity.containerId()))
                    .addChunks(ChunkMember.newBuilder()
                            .setName(chunk.type()).setExpectedHash(ByteBuffer.wrap(chunk.hash()))
                            .setStoredSize(chunk.size()).setDecodedSize(chunk.decodeSize())
                            .setEncoding(chunk.encoding()).build())
                    .build();
            assertTrue(transfers.accept(request));
            awaitQueued(worker, owner, transfers);
            drain(worker, owner, transfers);
            assertTrue(failures.isEmpty());
            assertChunkFrames(port.take(), chunk.type(), chunk.size());
        }
    }

    @Test
    void protectedChunkAuthorizationPrecedesSourceAvailability() throws Exception {
        var owner = active(catalog);
        var failures = new ArrayList<ResourceTransferFailure>();
        var identity = content.representation().identity();
        var chunk = content.modelFile().getFileView().getAssetView().getChunkTable()
                .values().stream()
                .filter(value -> !value.type().equals(AssetContainerConstant.VERIFICATION_CHUNK_TYPE))
                .findFirst().orElseThrow();
        Files.delete(content.file());
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = transfers(owner, worker, new RecordingPort(), true, failures)) {
            assertTrue(transfers.accept(ModelChunkRequest.newBuilder()
                    .setDataTransferId(1).setModelId(ProtoBytes.wrap(identity.modelId()))
                    .setContainerId(ProtoBytes.wrap(identity.containerId()))
                    .addChunks(ChunkMember.newBuilder()
                            .setName(chunk.type()).setExpectedHash(ByteBuffer.wrap(chunk.hash()))
                            .setStoredSize(chunk.size()).setDecodedSize(chunk.decodeSize())
                            .setEncoding(chunk.encoding()).build()).build()));
            assertEquals(1, failures.size());
            assertEquals(ResourceFailureReason.RESOURCE_FAILURE_UNAUTHORIZED,
                    failures.get(0).reason());
            assertEquals(0, worker.queued(owner));
        }
    }

    @Test
    void cancelBeforeChunkAcquisitionCompletesDoesNotRestartOrEnqueueLateSource() {
        var owner = active(catalog);
        var failures = new ArrayList<ResourceTransferFailure>();
        try (var worker = new ResourceDispatchWorker(1, 8, 0);
             var transfers = transfers(owner, worker, new RecordingPort(), false, failures)) {
            assertTrue(transfers.accept(chunkRequest(1)));
            assertEquals(1, transfers.activeCount());
            assertTrue(transfers.cancel(1));

            for (var attempt = 0; attempt < 20; attempt++) {
                runtime.tick();
                transfers.tick();
                Thread.yield();
            }
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
            assertTrue(failures.isEmpty());
        }
    }

    @Test
    void chunkAcquisitionFailureKeepsUnavailableProvenanceAndNeverEnqueues()
            throws Exception {
        var owner = active(catalog);
        var failures = new ArrayList<ResourceTransferFailure>();
        Files.delete(content.file());
        try (var worker = new ResourceDispatchWorker(1, 8, 0);
             var transfers = transfers(owner, worker, new RecordingPort(), false, failures)) {
            assertTrue(transfers.accept(chunkRequest(1)));
            var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (failures.isEmpty() && System.nanoTime() < deadline) {
                runtime.tick();
                transfers.tick();
                Thread.yield();
            }

            assertEquals(1, failures.size());
            assertEquals(com.elfmcys.ysm.proto.network.ResourceFailureReason
                    .RESOURCE_FAILURE_UNAVAILABLE, failures.get(0).reason());
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
        }
    }

    @Test
    void chunkSourceRetriesEveryCursorWithoutChangingItsRange() throws Exception {
        var chunk = content.modelFile().getFileView().getAssetView().getChunkTable()
                .values().stream()
                .filter(value -> !value.type().equals(
                        AssetContainerConstant.VERIFICATION_CHUNK_TYPE))
                .max(Comparator.comparingInt(
                        AssetContainerView.ChunkInfo::size))
                .orElseThrow();
        assertTrue(chunk.size() > RangePacket.FRAGMENT_BYTES * 2,
                "fixture must cover first, middle, and last range cursors");
        var expectedFuture = runtime.acquire(content, chunk);
        var sourceDeadline = System.nanoTime()
                + Duration.ofSeconds(5).toNanos();
        while (!expectedFuture.isDone() && System.nanoTime() < sourceDeadline) {
            runtime.tick();
            Thread.yield();
        }
        assertTrue(expectedFuture.isDone());
        byte[] expected;
        try (var source = expectedFuture.join()) {
            expected = new byte[source.size()];
            source.copyTo(0, ByteBuffer.wrap(expected));
        }
        var owner = active(catalog);
        var failures = new ArrayList<ResourceTransferFailure>();
        var port = new CursorRetryingPort();
        try (var worker = new ResourceDispatchWorker(1, 8, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            assertTrue(transfers.accept(chunkRequest(1, chunk)));
            awaitQueued(worker, owner, transfers);

            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (worker.queued(owner) != 0 && System.nanoTime() < deadline) {
                if (!worker.dispatchOnce()) {
                    Thread.sleep(110);
                }
                transfers.tick();
            }
            assertEquals(0, worker.queued(owner));
            transfers.tick();
            assertEquals(0, transfers.activeCount());
            assertTrue(failures.isEmpty());

            var fragmentCount = Math.toIntExact((chunk.size()
                    + (long) RangePacket.FRAGMENT_BYTES - 1) / RangePacket.FRAGMENT_BYTES);
            assertEquals(fragmentCount, port.attempts.size());
            for (var attempts : port.attempts.values()) {
                assertEquals(2, attempts.size());
                assertArrayEquals(attempts.get(0), attempts.get(1));
            }
            assertTrue(port.attempts.containsKey(0L));
            assertTrue(port.attempts.containsKey((long) RangePacket.FRAGMENT_BYTES));
            assertTrue(port.attempts.containsKey(
                    (long) (fragmentCount - 1) * RangePacket.FRAGMENT_BYTES));
            var rebuilt = new byte[chunk.size()];
            for (var entry : port.attempts.entrySet()) {
                var accepted = entry.getValue().get(1);
                System.arraycopy(accepted, 0, rebuilt, Math.toIntExact(entry.getKey()),
                        accepted.length);
            }
            assertArrayEquals(expected, rebuilt);
        }
    }

    @Test
    void presentationDispatchUsesVerifiedRuntimeBytesAfterTheSourceDisappears()
            throws Exception {
        var identity = content.representation().identity();
        var chunk = content.modelFile().getFileView().getAssetView().getChunkInfo(
                com.elfmcys.ysm.format.schema.model.ModelFileConstant
                        .THUMB_BUTTON_CHUNK_NAME);
        var preview = new AssetRef(
                AssetRef.Kind.PREVIEW,
                chunk.type(), new Hash256(chunk.hash()),
                chunk.size(), chunk.decodeSize(), chunk.encoding());
        var record = catalog.byModelId().get(identity.modelId());
        var entry = new CatalogEntry(
                identity.modelId(), record.entry().path(), record.entry().access(),
                new CatalogPresentation(
                        "fixture", "", Optional.empty(),
                        Optional.of(preview)));
        var owner = active(new CatalogSnapshot(Map.of(identity.modelId(),
                new CatalogRecord(entry, record.location(), record.binding())),
                List.of(), ModelScanReport.empty()));
        var failures = new ArrayList<ResourceTransferFailure>();
        var port = new RecordingPort();
        try (var worker = new ResourceDispatchWorker(1, 8, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            var request = PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPreviews(PreviewMember.newBuilder()
                            .setSlot(0).setModelId(ProtoBytes.wrap(identity.modelId()))
                            .setContainerId(ProtoBytes.wrap(identity.containerId()))
                            .build())
                    .build();

            assertTrue(transfers.accept(request));
            awaitQueued(worker, owner, transfers);
            Files.delete(content.file());
            drain(worker, owner, transfers);

            assertTrue(failures.isEmpty());
            var received = 0;
            for (var frame : port.take()) {
                try (var decoded = FrameCodec.decode(frame,
                        id -> id == ProtocolMessages.PREVIEW_FRAGMENT_ID)) {
                    var message = ProtocolBuffer.parse(decoded.protobuf(),
                            PreviewFragment::parseFrom);
                    assertEquals(com.elfmcys.ysm.proto.network.PresentationDelivery
                            .PRESENTATION_DELIVERY_DATA, message.delivery());
                    received += decoded.attachment().size();
                }
            }
            assertEquals(chunk.size(), received);
        }
    }

    private ServerAssetTransfers transfers(ServerModelSession owner,
                                            ResourceDispatchWorker worker,
                                            TransportPort port, boolean restricted,
                                            List<ResourceTransferFailure> failures) {
        return new ServerAssetTransfers(owner, worker, port, () -> restricted,
                failures::add, runtime, ignored -> testRoot);
    }

    private ModelChunkRequest chunkRequest(long transferId) {
        var chunk = content.modelFile().getFileView().getAssetView().getChunkTable()
                .values().stream()
                .filter(value -> !value.type().equals(
                        AssetContainerConstant.VERIFICATION_CHUNK_TYPE))
                .findFirst().orElseThrow();
        return chunkRequest(transferId, chunk);
    }

    private ModelChunkRequest chunkRequest(
            long transferId,
            AssetContainerView.ChunkInfo chunk) {
        var identity = content.representation().identity();
        return ModelChunkRequest.newBuilder()
                .setDataTransferId(transferId)
                .setModelId(ProtoBytes.wrap(identity.modelId()))
                .setContainerId(ProtoBytes.wrap(identity.containerId()))
                .addChunks(ChunkMember.newBuilder()
                        .setName(chunk.type())
                        .setExpectedHash(ByteBuffer.wrap(chunk.hash()))
                        .setStoredSize(chunk.size()).setDecodedSize(chunk.decodeSize())
                        .setEncoding(chunk.encoding()).build())
                .build();
    }

    private void awaitQueued(ResourceDispatchWorker worker, ServerModelSession owner,
                             ServerAssetTransfers transfers) {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (worker.queued(owner) == 0 && System.nanoTime() < deadline) {
            runtime.tick();
            transfers.tick();
            Thread.yield();
        }
        assertTrue(worker.queued(owner) > 0, "chunk acquisition did not reach dispatch");
    }

    private static ServerModelSession active(CatalogSnapshot catalog) {
        var owner = new ServerModelSession(() -> catalog, false);
        assertTrue(owner.activate());
        owner.commitCatalog(catalog);
        return owner;
    }

    private static void drain(ResourceDispatchWorker worker, ServerModelSession owner,
                              ServerAssetTransfers transfers) {
        while (worker.queued(owner) != 0) {
            assertTrue(worker.dispatchOnce());
            transfers.tick();
        }
    }

    private static void assertMetadataFrames(List<byte[]> frames, ModelFileIdentity identity) {
        for (var index = 0; index < frames.size(); index++) {
            try (var decoded = FrameCodec.decode(frames.get(index),
                    id -> id == ProtocolMessages.METADATA_PREFIX_FRAGMENT_ID)) {
                assertEquals(0, decoded.attachment().size());
                var message = ProtocolBuffer.parse(decoded.protobuf(),
                        MetadataPrefixFragment::parseFrom);
                assertEquals(1, message.dataTransferId());
                assertTrue(ProtoBytes.equals(identity.containerId(), message.containerId()));
                assertEquals(index, message.sequence());
                assertEquals(index + 1 == frames.size(), message.finalFragment());
                assertTrue(message.payload().remaining() > 0);
            }
        }
    }

    private static void assertChunkFrames(List<byte[]> frames, String name, int size) {
        var received = 0;
        for (var index = 0; index < frames.size(); index++) {
            try (var decoded = FrameCodec.decode(frames.get(index),
                    id -> id == ProtocolMessages.CHUNK_FRAGMENT_ID)) {
                var message = ProtocolBuffer.parse(decoded.protobuf(),
                        ChunkFragment::parseFrom);
                assertEquals(2, message.dataTransferId());
                assertEquals(name, message.name());
                assertEquals(received, message.offset());
                received += decoded.attachment().size();
                assertEquals(index + 1 == frames.size(), message.finalFragment());
            }
        }
        assertEquals(size, received);
    }

    private static final class RecordingPort implements TransportPort {
        private final List<byte[]> frames = new ArrayList<>();

        private List<byte[]> take() {
            var result = List.copyOf(frames);
            frames.clear();
            return result;
        }

        @Override public boolean isOpen() { return true; }
        @Override public boolean isWritable() { return true; }
        @Override public long highWatermarkBytes() { return 64 * 1024; }
        @Override public long pendingBytes() { return 0; }
        @Override public SendResult trySend(OutboundFrame frame) {
            frames.add(frame.bytes());
            return SendResult.SUCCESS;
        }
    }

    private static final class CursorRetryingPort implements TransportPort {
        private final Map<Long, List<byte[]>> attempts = new LinkedHashMap<>();

        @Override public boolean isOpen() { return true; }
        @Override public boolean isWritable() { return true; }
        @Override public long highWatermarkBytes() { return 64 * 1024; }
        @Override public long pendingBytes() { return 0; }

        @Override
        public SendResult trySend(OutboundFrame frame) {
            try (var decoded = FrameCodec.decode(frame.bytes(),
                    id -> id == ProtocolMessages.CHUNK_FRAGMENT_ID);
                 var attachment = decoded.attachment().acquireArray()) {
                var message = ProtocolBuffer.parse(decoded.protobuf(),
                        ChunkFragment::parseFrom);
                var copy = new byte[attachment.size()];
                System.arraycopy(attachment.array(), attachment.arrayOffset(), copy, 0,
                        copy.length);
                var cursorAttempts = attempts.computeIfAbsent(message.offset(),
                        ignored -> new ArrayList<>());
                cursorAttempts.add(copy);
                return cursorAttempts.size() == 1 ? SendResult.FAILED : SendResult.SUCCESS;
            }
        }
    }
}
