package com.elfmcys.ysm.network.dispatch;

import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelPackDescriptor;
import com.elfmcys.ysm.model.domain.ModelScanReport;
import com.elfmcys.ysm.model.resource.server.ServerChunkRuntime;
import com.elfmcys.ysm.model.session.server.ServerModelSession;
import com.elfmcys.ysm.model.storage.ModelHashing;
import com.elfmcys.ysm.network.ProtocolBuffer;
import com.elfmcys.ysm.network.frame.FrameCodec;
import com.elfmcys.ysm.network.frame.OutboundFrame;
import com.elfmcys.ysm.network.protocol.ProtocolMessages;
import com.elfmcys.ysm.proto.network.ChunkMember;
import com.elfmcys.ysm.proto.network.MetadataPrefixRequest;
import com.elfmcys.ysm.proto.network.ModelChunkRequest;
import com.elfmcys.ysm.proto.network.PackCoverFragment;
import com.elfmcys.ysm.proto.network.PackCoverMember;
import com.elfmcys.ysm.proto.network.PresentationPageRequest;
import com.elfmcys.ysm.proto.network.ResourceFailureReason;
import com.elfmcys.ysm.proto.network.ResourceTransferFailure;
import com.elfmcys.ysm.util.ProtoBytes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAssetTransfersTest {
    @TempDir
    Path root;
    private final List<ServerChunkRuntime> runtimes = new ArrayList<>();

    @AfterEach
    void closeRuntimes() {
        runtimes.forEach(ServerChunkRuntime::close);
    }

    @Test
    void structuralRejectionDoesNotAdvanceTheSharedHighWater() {
        var failures = new ArrayList<ResourceTransferFailure>();
        var owner = active(CatalogSnapshot.empty());
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = transfers(owner, worker, new RecordingPort(), false, failures)) {
            assertFalse(transfers.accept(MetadataPrefixRequest.newBuilder()
                    .setDataTransferId(1).build()));

            assertTrue(transfers.accept(MetadataPrefixRequest.newBuilder()
                    .setDataTransferId(1).addContainerIds(ProtoBytes.wrap(hash(1))).build()));
            assertFailure(failures, 1, notFound());

            assertFalse(transfers.accept(modelRequest(1, "chunk")));
            assertTrue(transfers.accept(modelRequest(2, "chunk")));
            assertFailure(failures, 2, notFound());

            var invalidPage = PresentationPageRequest.newBuilder()
                    .setDataTransferId(3)
                    .addPackCovers(cover(1, "../escape/", hash(4), 1))
                    .addPackCovers(cover(2, "pack/", hash(4), 1)).build();
            assertFalse(transfers.accept(invalidPage));
            assertTrue(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(3).addPackCovers(cover(1, "missing/", hash(4), 1)).build()));
            assertFailure(failures, 3, notFound());
        }
    }

    @Test
    void traversalIsRejectedBeforeCatalogRootResolution() {
        var failures = new ArrayList<ResourceTransferFailure>();
        var rootCalls = new AtomicInteger();
        var owner = active(CatalogSnapshot.empty());
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = new ServerAssetTransfers(owner, worker, new RecordingPort(),
                     () -> false, failures::add, runtime(), ignored -> {
                         rootCalls.incrementAndGet();
                         return root;
                     })) {
            assertFalse(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPackCovers(cover(1, "../escape/", hash(4), 1)).build()));

            assertEquals(0, rootCalls.get());
            assertEquals(0, transfers.activeCount());
            assertTrue(failures.isEmpty());
        }
    }

    @Test
    void packCoverSymlinkCannotEscapeItsCatalogRoot() throws IOException {
        var catalogRoot = root.resolve("catalog");
        var outsideRoot = root.resolve("outside");
        Files.createDirectories(catalogRoot);
        Files.createDirectories(outsideRoot);
        var bytes = new byte[]{1, 3, 3, 7};
        var outsideCover = outsideRoot.resolve("ysm-pack.png");
        Files.write(outsideCover, bytes);
        Files.createSymbolicLink(catalogRoot.resolve("linked"), outsideRoot);

        var contentHash = ModelHashing.blake3(outsideCover);
        var owner = active(catalog("linked", contentHash, bytes.length));
        var failures = new ArrayList<ResourceTransferFailure>();
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = new ServerAssetTransfers(owner, worker, new RecordingPort(),
                     () -> false, failures::add, runtime(), ignored -> catalogRoot)) {
            assertTrue(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPackCovers(cover(1, "linked/", contentHash, bytes.length)).build()));

            assertFailure(failures, 1, unavailable());
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
        }
    }

    @Test
    void validatesWholePresentationBeforeResolvingAnySource() {
        var contentHash = ModelHashing.blake3(new byte[]{1, 2, 3});
        var owner = active(catalog("pack", contentHash, 3));
        var failures = new ArrayList<ResourceTransferFailure>();
        var rootCalls = new AtomicInteger();
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = new ServerAssetTransfers(owner, worker, new RecordingPort(),
                     () -> false, failures::add, runtime(), ignored -> {
                         rootCalls.incrementAndGet();
                         return root;
                     })) {
            assertTrue(transfers.accept(com.elfmcys.ysm.proto.network.PresentationPageRequest
                    .newBuilder().setDataTransferId(1)
                    .addPackCovers(cover(1, "pack/", contentHash, 3))
                    .addPackCovers(cover(2, "missing/", contentHash, 3)).build()));

            assertFailure(failures, 1, notFound());
            assertEquals(0, rootCalls.get());
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
        }
    }

    @Test
    void activeTransferCapacityReturnsTypedBusyWithoutRetiringSession() {
        var contentHash = ModelHashing.blake3(new byte[]{4, 5, 6});
        var owner = active(catalog("missing", contentHash, 3));
        var failures = new ArrayList<ResourceTransferFailure>();
        try (var worker = new ResourceDispatchWorker(255, 256, 0);
             var transfers = transfers(owner, worker, new RecordingPort(), false, failures)) {
            for (var transferId = 1; transferId <= 256; transferId++) {
                assertTrue(transfers.accept(com.elfmcys.ysm.proto.network.PresentationPageRequest
                        .newBuilder().setDataTransferId(transferId)
                        .addPackCovers(cover(1, "missing/", contentHash, 3)).build()));
            }
            assertEquals(256, transfers.activeCount());
            assertTrue(failures.isEmpty());

            assertTrue(transfers.accept(com.elfmcys.ysm.proto.network.PresentationPageRequest
                    .newBuilder().setDataTransferId(257)
                    .addPackCovers(cover(1, "missing/", contentHash, 3)).build()));
            assertFailure(failures, 257, busy());
            assertEquals(256, transfers.activeCount());
            assertFalse(transfers.accept(com.elfmcys.ysm.proto.network.PresentationPageRequest
                    .newBuilder().setDataTransferId(257)
                    .addPackCovers(cover(1, "missing/", contentHash, 3)).build()));
        }
    }

    @Test
    void independentlyAdmitsChildrenAndRollsBackOnlyTheRejectedChild() throws IOException {
        var bytes = new byte[]{1, 2, 3, 4};
        var contentHash = writeCover("pack", bytes);
        var owner = active(catalog("pack", contentHash, bytes.length));
        var failures = new ArrayList<ResourceTransferFailure>();
        var port = new RecordingPort();
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            var first = PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPackCovers(cover(1, "pack/", contentHash, bytes.length))
                    .addPackCovers(cover(2, "pack/", contentHash, bytes.length)).build();
            assertTrue(transfers.accept(first));
            assertEquals(2, worker.queued(owner));
            assertEquals(1, transfers.activeCount());

            assertTrue(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(2)
                    .addPackCovers(cover(3, "pack/", contentHash, bytes.length)).build()));
            assertFailure(failures, 2, busy());
            assertEquals(2, worker.queued(owner));
            assertEquals(1, transfers.activeCount());

            assertTrue(transfers.cancel(1));
            assertEquals(0, transfers.activeCount());
            assertTrue(worker.dispatchOnce());
            assertTrue(port.frames.isEmpty());
        }
    }

    @Test
    void enqueueExceptionRollsBackTheCallerOwnedChildAsBusy() throws IOException {
        var bytes = new byte[]{1, 2, 3, 4};
        var contentHash = writeCover("pack", bytes);
        var owner = active(catalog("pack", contentHash, bytes.length));
        var failures = new ArrayList<ResourceTransferFailure>();
        var settingsCalls = new AtomicInteger();
        try (var worker = new ResourceDispatchWorker(() -> {
                 if (settingsCalls.incrementAndGet() > 1) {
                     throw new IllegalStateException("configuration refresh failed");
                 }
                 return new ResourceDispatchWorker.Settings(1, 2, 0);
             });
             var transfers = transfers(owner, worker, new RecordingPort(), false, failures)) {
            assertTrue(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPackCovers(cover(1, "pack/", contentHash, bytes.length)).build()));

            assertFailure(failures, 1, busy());
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
        }
    }

    @Test
    void missingPresentationSourceProducesTypedUnavailableOutcome() throws IOException {
        var bytes = new byte[]{5, 6, 7};
        var contentHash = ModelHashing.blake3(bytes);
        var owner = active(catalog("missing", contentHash, bytes.length));
        var failures = new ArrayList<ResourceTransferFailure>();
        var port = new RecordingPort();
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            assertTrue(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPackCovers(cover(4, "missing/", contentHash, bytes.length)).build()));

            assertTrue(worker.dispatchOnce());
            assertEquals(1, transfers.activeCount());
            transfers.tick();
            assertTrue(failures.isEmpty());
            assertEquals(0, transfers.activeCount());
            assertEquals(1, port.frames.size());
            try (var decoded = FrameCodec.decode(port.frames.get(0),
                    id -> ProtocolMessages.REGISTRY.find(id).isPresent())) {
                assertEquals(ProtocolMessages.PACK_COVER_FRAGMENT_ID, decoded.messageId());
                assertEquals(0, decoded.attachment().size());
                var message = ProtocolBuffer.parse(decoded.protobuf(),
                        PackCoverFragment::parseFrom);
                assertEquals(1, message.dataTransferId());
                assertEquals(4, message.slot());
                assertEquals(com.elfmcys.ysm.proto.network.PresentationDelivery
                        .PRESENTATION_DELIVERY_UNAVAILABLE, message.delivery());
            }
        }
    }

    @Test
    void sourceFailureAfterEnqueueTerminatesTheChildOnce() throws IOException {
        var bytes = new byte[]{9, 10, 11};
        var contentHash = writeCover("volatile", bytes);
        var owner = active(catalog("volatile", contentHash, bytes.length));
        var failures = new ArrayList<ResourceTransferFailure>();
        var port = new RecordingPort();
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            assertTrue(transfers.accept(PresentationPageRequest.newBuilder()
                    .setDataTransferId(1)
                    .addPackCovers(cover(1, "volatile/", contentHash, bytes.length)).build()));
            Files.delete(root.resolve("volatile").resolve("ysm-pack.png"));

            assertTrue(worker.dispatchOnce());
            assertTrue(failures.isEmpty());
            assertEquals(1, transfers.activeCount());
            transfers.tick();
            assertFailure(failures, 1, unavailable());
            assertEquals(0, transfers.activeCount());
            assertTrue(port.frames.isEmpty());
            assertFalse(worker.dispatchOnce());
            assertEquals(1, failures.size());
        }
    }

    @Test
    void productionFailureStopsTheRemainingAcceptedPacketsBeforeOwnerTick() throws IOException {
        var firstBytes = new byte[]{1, 2, 3};
        var secondBytes = new byte[]{4, 5, 6};
        var firstHash = writeCover("volatile", firstBytes);
        var secondHash = writeCover("stable", secondBytes);
        var owner = active(new CatalogSnapshot(Map.of(),
                List.of(pack("volatile", firstHash, firstBytes.length),
                        pack("stable", secondHash, secondBytes.length)),
                ModelScanReport.empty()));
        var port = new RecordingPort();
        var failures = new ArrayList<ResourceTransferFailure>();
        try (var worker = new ResourceDispatchWorker(1, 4, 0);
             var transfers = transfers(owner, worker, port, false, failures)) {
            assertTrue(transfers.accept(com.elfmcys.ysm.proto.network.PresentationPageRequest
                    .newBuilder().setDataTransferId(1)
                    .addPackCovers(cover(1, "volatile/", firstHash, firstBytes.length))
                    .addPackCovers(cover(2, "stable/", secondHash, secondBytes.length))
                    .build()));
            Files.delete(root.resolve("volatile").resolve("ysm-pack.png"));

            assertTrue(worker.dispatchOnce());
            assertTrue(port.frames.isEmpty());
            assertTrue(worker.dispatchOnce());
            assertTrue(port.frames.isEmpty());
            assertEquals(1, transfers.activeCount());
            assertTrue(failures.isEmpty());

            transfers.tick();

            assertFailure(failures, 1, unavailable());
            assertEquals(0, transfers.activeCount());
            assertEquals(0, worker.queued(owner));
        }
    }

    private ServerAssetTransfers transfers(ServerModelSession owner,
                                            ResourceDispatchWorker worker,
                                            TransportPort port, boolean restricted,
                                            List<ResourceTransferFailure> failures) {
        return new ServerAssetTransfers(owner, worker, port, () -> restricted,
                failures::add, runtime(), ignored -> root);
    }

    @Test
    void transferOwnerExistsBeforeSynchronousSourceResolutionAndCancelPreventsEnqueue()
            throws Exception {
        var bytes = new byte[]{1, 2, 3, 4};
        var contentHash = writeCover("blocked", bytes);
        var owner = active(catalog("blocked", contentHash, bytes.length));
        var failures = new ArrayList<ResourceTransferFailure>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var worker = new ResourceDispatchWorker(1, 2, 0);
             var transfers = new ServerAssetTransfers(owner, worker, new RecordingPort(),
                     () -> false, failures::add, runtime(), ignored -> {
                         entered.countDown();
                         try {
                             if (!release.await(5, TimeUnit.SECONDS)) {
                                 throw new IllegalStateException("source resolution timed out");
                             }
                         } catch (InterruptedException interrupted) {
                             Thread.currentThread().interrupt();
                             throw new IllegalStateException("source resolution interrupted",
                                     interrupted);
                         }
                         return root;
                     })) {
            var accepted = executor.submit(() -> transfers.accept(
                    PresentationPageRequest.newBuilder()
                            .setDataTransferId(1)
                            .addPackCovers(cover(1, "blocked/", contentHash, bytes.length))
                            .build()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, transfers.activeCount());
            assertTrue(transfers.cancel(1));
            release.countDown();

            assertTrue(accepted.get(5, TimeUnit.SECONDS));
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
            assertTrue(failures.isEmpty());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void partialPacketConstructionClosesThePrefixAndNeverEnqueues() throws IOException {
        var firstBytes = new byte[]{1, 2, 3};
        var secondBytes = new byte[]{4, 5, 6};
        var firstHash = writeCover("first", firstBytes);
        var secondHash = writeCover("second", secondBytes);
        var firstPack = pack("first", firstHash, firstBytes.length);
        var secondPack = pack("second", secondHash, secondBytes.length);
        var owner = active(new CatalogSnapshot(Map.of(),
                List.of(firstPack, secondPack), ModelScanReport.empty()));
        var failures = new ArrayList<ResourceTransferFailure>();
        var resolutions = new AtomicInteger();
        try (var worker = new ResourceDispatchWorker(1, 4, 0);
             var transfers = new ServerAssetTransfers(owner, worker, new RecordingPort(),
                     () -> false, failures::add, runtime(), ignored -> {
                         if (resolutions.incrementAndGet() == 2) {
                             try {
                                 Files.write(root.resolve("second").resolve("ysm-pack.png"),
                                         new byte[]{7, 8, 9});
                             } catch (IOException failure) {
                                 throw new UncheckedIOException(failure);
                             }
                         }
                         return root;
                     })) {
            assertTrue(transfers.accept(
                    PresentationPageRequest.newBuilder()
                            .setDataTransferId(1)
                            .addPackCovers(cover(1, "first/", firstHash, firstBytes.length))
                            .addPackCovers(cover(2, "second/", secondHash, secondBytes.length))
                            .build()));

            assertFailure(failures, 1, unavailable());
            assertEquals(0, worker.queued(owner));
            assertEquals(0, transfers.activeCount());
        }
    }

    private ServerChunkRuntime runtime() {
        var runtime = new ServerChunkRuntime(ignored -> { });
        runtimes.add(runtime);
        return runtime;
    }

    private Hash256 writeCover(String hierarchy, byte[] bytes) throws IOException {
        var directory = root.resolve(hierarchy);
        Files.createDirectories(directory);
        var path = directory.resolve("ysm-pack.png");
        Files.write(path, bytes);
        return ModelHashing.blake3(path);
    }

    private static ServerModelSession active(CatalogSnapshot catalog) {
        var owner = new ServerModelSession(() -> catalog, false);
        assertTrue(owner.activate());
        owner.commitCatalog(catalog);
        return owner;
    }

    private static CatalogSnapshot catalog(String hierarchy, Hash256 hash, int size) {
        return new CatalogSnapshot(Map.of(), List.of(pack(hierarchy, hash, size)),
                ModelScanReport.empty());
    }

    private static ModelPackDescriptor pack(String hierarchy, Hash256 hash, int size) {
        return new ModelPackDescriptor(CatalogRootKind.CUSTOM, hierarchy,
                hierarchy, "", Map.of(), hash, "png", size);
    }

    private static ModelChunkRequest modelRequest(long id, String name) {
        return ModelChunkRequest.newBuilder().setDataTransferId(id)
                .setModelId(ProtoBytes.wrap(hash(10))).setContainerId(ProtoBytes.wrap(hash(11)))
                .addChunks(ChunkMember.newBuilder().setName(name)
                        .setExpectedHash(ProtoBytes.wrap(hash(12))).setStoredSize(1)
                        .setDecodedSize(0).setEncoding("").build())
                .build();
    }

    private static PackCoverMember cover(int slot, String hierarchy,
                                                        Hash256 hash, int size) {
        return PackCoverMember.newBuilder().setSlot(slot)
                .setHierarchyPath(hierarchy).setExpectedHash(ProtoBytes.wrap(hash))
                .setStoredSize(size).setDecodedSize(size).setEncoding("png").build();
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[Hash256.SIZE - 1] = (byte) marker;
        return new Hash256(bytes);
    }

    private static void assertFailure(List<ResourceTransferFailure> failures,
                                      long id,
                                      ResourceFailureReason reason) {
        var failure = failures.get(failures.size() - 1);
        assertEquals(id, failure.dataTransferId());
        assertEquals(reason, failure.reason());
    }

    private static ResourceFailureReason notFound() {
        return ResourceFailureReason.RESOURCE_FAILURE_NOT_FOUND;
    }

    private static ResourceFailureReason busy() {
        return ResourceFailureReason.RESOURCE_FAILURE_BUSY;
    }

    private static ResourceFailureReason unavailable() {
        return ResourceFailureReason.RESOURCE_FAILURE_UNAVAILABLE;
    }

    private static final class RecordingPort implements TransportPort {
        private final List<byte[]> frames = new ArrayList<>();

        @Override public boolean isOpen() { return true; }
        @Override public boolean isWritable() { return true; }
        @Override public long highWatermarkBytes() { return 64 * 1024; }
        @Override public long pendingBytes() { return 0; }

        @Override
        public SendResult trySend(OutboundFrame frame) {
            frames.add(frame.bytes());
            return SendResult.SUCCESS;
        }
    }
}
