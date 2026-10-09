package com.elfmcys.ysm.model.resource.client.remote;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.parser.DefaultAnimationFilter;
import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.schema.model.ModelFileConstant;
import com.elfmcys.ysm.format.schema.model.ModelFileIdentityReader;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogIndexEntry;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.domain.ModelRepresentation;
import com.elfmcys.ysm.model.session.server.state.Selection;
import com.elfmcys.ysm.model.storage.AtomicSharedCache;
import com.elfmcys.ysm.model.storage.ManagedContainer;
import com.elfmcys.ysm.natives.Blake3;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteModelStoreTest {
    @TempDir
    static Path fixtureRoot;

    @TempDir
    Path temp;

    private static ModelRepresentation representation;
    private static ManagedContainer sourceContent;

    @BeforeAll
    static void createMetadataFixture() throws Exception {
        var manifest = RemoteModelStoreTest.class.getResource(
                "/assets/ysm/builtin/misc/1_alex/ysm.json");
        var source = Path.of(Objects.requireNonNull(manifest).toURI()).getParent();
        final Path file;
        try (var vfs = new Directory(source)) {
            file = ModelParser.parse(vfs,
                    Files.createDirectories(fixtureRoot.resolve("model")),
                    DefaultAnimationFilter.keepAll());
        }
        final ModelFileIdentity identity;
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            identity = ModelFileIdentityReader.read(channel);
        }
        sourceContent = ManagedContainer.openIndexed(new CatalogIndexEntry(
                identity,
                new CatalogModelLocation(CatalogRootKind.CUSTOM,
                        new ModelPath("fixture")), file));
        representation = sourceContent.representation();
    }

    @Test
    void corruptCachedContentIsAMissWithoutDeletionOrNetwork() throws Exception {
        var valid = new byte[]{1, 2, 3};
        var chunk = chunk(valid);
        var modelId = hash(1);
        var identity = new ModelFileIdentity(modelId, hash(2));
        var store = new RemoteModelStore(temp);
        var path = store.chunkPath(modelId, new Hash256(chunk.hash()), chunk.encoding());
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{9, 9, 9});

        var source = store.chunkSource(identity);
        var failure = assertThrows(AssetLoadException.class,
                () -> source.readStoredVerified(chunk, BufferType.ARRAY));

        assertEquals(AssetLoadException.Reason.CONTENT, failure.reason());
        assertArrayEquals(new byte[]{9, 9, 9}, Files.readAllBytes(path));
    }

    @Test
    void staleBadReadCannotDeleteANewerValidObject() throws Exception {
        var valid = new byte[]{1, 2, 3};
        var corrupt = new byte[]{9, 9, 9};
        var chunk = chunk(valid);
        var modelId = hash(30);
        var identity = new ModelFileIdentity(modelId, hash(31));
        var replaced = new AtomicBoolean();
        try (var writer = new RemoteModelStore(temp);
             var reader = new RemoteModelStore(temp, AtomicSharedCache::moveCommitted,
                     ignored -> {
                         if (replaced.compareAndSet(false, true)) {
                             writer.publishChunk(modelId, chunk, valid);
                         }
                     })) {
            var path = reader.chunkPath(modelId, new Hash256(chunk.hash()), chunk.encoding());
            Files.createDirectories(path.getParent());
            Files.write(path, corrupt);

            var failure = assertThrows(AssetLoadException.class,
                    () -> reader.chunkSource(identity)
                            .readStoredVerified(chunk, BufferType.ARRAY));

            assertEquals(AssetLoadException.Reason.CONTENT, failure.reason());
            assertArrayEquals(valid, Files.readAllBytes(path));
            try (var stored = writer.chunkSource(identity)
                    .readStoredVerified(chunk, BufferType.ARRAY)) {
                assertArrayEquals(valid, bytes(stored));
            }
        }
    }

    @Test
    void atomicPublishFailureKeepsThePreviousObjectAndCleansItsTemporary() throws Exception {
        var valid = new byte[]{4, 5, 6};
        var previous = new byte[]{9, 8, 7};
        var chunk = chunk(valid);
        var modelId = hash(32);
        try (var store = new RemoteModelStore(temp, (temporary, target) -> {
            throw new IOException("injected atomic move failure");
        }, ignored -> { })) {
            var path = store.chunkPath(modelId, new Hash256(chunk.hash()), chunk.encoding());
            Files.createDirectories(path.getParent());
            Files.write(path, previous);

            assertThrows(AssetLoadException.class,
                    () -> store.publishChunk(modelId, chunk, valid));

            assertArrayEquals(previous, Files.readAllBytes(path));
            try (var files = Files.list(path.getParent())) {
                assertTrue(files.noneMatch(candidate -> candidate.getFileName().toString()
                        .startsWith(path.getFileName() + ".tmp-")));
            }
        }
    }

    @Test
    void twoQualifiedWritersLeaveAValidExactObject() throws Exception {
        var valid = new byte[]{7, 8, 9};
        var chunk = chunk(valid);
        var modelId = hash(33);
        try (var firstStore = new RemoteModelStore(temp);
             var secondStore = new RemoteModelStore(temp)) {
            firstStore.publishChunk(modelId, chunk, valid);
            secondStore.publishChunk(modelId, chunk, valid);

            var identity = new ModelFileIdentity(modelId, hash(34));
            try (var stored = firstStore.chunkSource(identity)
                    .readStoredVerified(chunk, BufferType.ARRAY)) {
                assertArrayEquals(valid, bytes(stored));
            }
        }
    }

    @Test
    void remoteCacheKeepsItsDocumentedIdentityPartitioning() throws Exception {
        var modelId = hash(41);
        var identity = new ModelFileIdentity(modelId, hash(42));
        var chunkHash = hash(43);
        try (var store = new RemoteModelStore(temp)) {
            assertEquals(Path.of(modelId.toString(), "metadata",
                            identity.containerId().toString(), "metadata.bin"),
                    store.root().relativize(store.metadataPath(identity)));
            assertEquals(Path.of(modelId.toString(), chunkHash + ".bin"),
                    store.root().relativize(store.chunkPath(modelId, chunkHash, "direct")));
        }
    }

    @Test
    void remoteDescriptorFieldsCannotSelectAPathOutsideTheOwnerRoot() throws Exception {
        var modelId = hash(1);
        var identity = new ModelFileIdentity(modelId, hash(2));
        try (var store = new RemoteModelStore(temp)) {
            assertEquals(temp.toRealPath(), store.root());
            assertTrue(store.metadataPath(identity).startsWith(store.root()));

            var direct = store.chunkPath(modelId, hash(3), "../../outside/native.dll");
            assertTrue(direct.startsWith(store.root()));
            assertTrue(direct.getFileName().toString().endsWith(".bin"));
            assertFalse(direct.getFileName().toString().contains("native"));

            var compressed = store.chunkPath(modelId, hash(3), "zstd");
            assertTrue(compressed.startsWith(store.root()));
            assertTrue(compressed.getFileName().toString().endsWith("-zst.bin"));
        }
    }

    @Test
    void remoteCacheSymlinkCannotEscapeItsOwnerRoot() throws Exception {
        var cache = temp.resolve("cache");
        var outside = Files.createDirectories(temp.resolve("outside"));
        var modelId = hash(1);
        var identity = new ModelFileIdentity(modelId, hash(2));
        var valid = new byte[]{1, 2, 3};
        var chunk = chunk(valid);
        try (var store = new RemoteModelStore(cache)) {
            Files.createSymbolicLink(store.root().resolve(modelId.toString()), outside);
            Files.write(outside.resolve(new Hash256(chunk.hash()) + ".bin"), valid);

            assertThrows(AssetLoadException.class, () -> {
                try (var ignored = store.chunkSource(identity)
                        .readStoredVerified(chunk, BufferType.ARRAY)) {
                    // A physical path outside the cache root must never become a cache hit.
                }
            });
        }
    }

    @Test
    void escapingCachePathCannotInvalidateOrPublishOutsideItsOwnerRoot() throws Exception {
        var cache = temp.resolve("cache-write");
        var outside = Files.createDirectories(temp.resolve("outside-write"));
        var modelId = hash(3);
        var identity = new ModelFileIdentity(modelId, hash(4));
        var valid = new byte[]{4, 5, 6};
        var corrupt = new byte[]{9, 9, 9};
        var chunk = chunk(valid);
        var outsideFile = outside.resolve(new Hash256(chunk.hash()) + ".bin");
        try (var store = new RemoteModelStore(cache)) {
            Files.createSymbolicLink(store.root().resolve(modelId.toString()), outside);
            Files.write(outsideFile, corrupt);

            assertThrows(AssetLoadException.class, () -> store.chunkSource(identity)
                    .readStoredVerified(chunk, BufferType.ARRAY));
            assertArrayEquals(corrupt, Files.readAllBytes(outsideFile));

            assertThrows(AssetLoadException.class,
                    () -> store.publishChunk(modelId, chunk, valid));
            assertArrayEquals(corrupt, Files.readAllBytes(outsideFile));
        }
    }

    @Test
    void accessFailureIsNotRetriedInternally() throws Exception {
        var valid = new byte[]{4, 5, 6};
        var chunk = chunk(valid);
        var modelId = hash(3);
        var identity = new ModelFileIdentity(modelId, hash(4));
        var store = new RemoteModelStore(temp);
        var source = store.chunkSource(identity);

        var failure = assertThrows(AssetLoadException.class, () ->
                source.readStoredVerified(chunk, BufferType.ARRAY));

        assertEquals(AssetLoadException.Reason.ACCESS, failure.reason());
        assertFalse(Files.exists(store.chunkPath(
                modelId, new Hash256(chunk.hash()), chunk.encoding())));
    }

    @Test
    void metadataProbeRequiresTheExactSingleFileAndIgnoresTheOldLayout() throws Exception {
        var store = new RemoteModelStore(temp);
        var identity = representation.identity();
        var metadata = store.metadataPath(identity);
        Files.createDirectories(metadata.getParent());
        Files.write(metadata.getParent().resolve("preamble.bin"), preamble());
        Files.write(metadata.getParent().resolve("manifest.bin"), manifest());
        Files.write(metadata.getParent().resolve("ready"), new byte[]{1});
        assertTrue(store.probeMetadata(identity).isEmpty(),
                "the old pair and marker must not be a cache hit");

        Files.write(metadata, metadataPrefix());
        assertTrue(store.probeMetadata(identity).isPresent());
        Files.delete(metadata);
        assertTrue(store.probeMetadata(identity).isEmpty());
    }

    @Test
    void prefixCommitPublishesOneFileWithoutAMarker() throws Exception {
        var store = new RemoteModelStore(temp);
        var identity = representation.identity();
        store.commitMetadataPrefix(identity, ArrayBuffer.borrow(metadataPrefix()));

        assertTrue(Files.exists(store.metadataPath(identity)));
        assertFalse(Files.exists(store.metadataPath(identity).getParent().resolve("ready")));
        assertFalse(Files.exists(store.metadataPath(identity).getParent().resolve("preamble.bin")));
        assertFalse(Files.exists(store.metadataPath(identity).getParent().resolve("manifest.bin")));
        var cached = store.probeMetadata(identity).orElseThrow();
        try (var encoded = cached.representation().metadataPrefix().orElseThrow();
             var prefix = encoded.acquireArray()) {
            assertArrayEquals(metadataPrefix(),
                    Arrays.copyOfRange(prefix.array(), prefix.arrayOffset(),
                            prefix.arrayOffset() + prefix.size()));
        }
    }

    @Test
    void corruptSingleFileBecomesAMissWithoutFetching() throws Exception {
        var store = new RemoteModelStore(temp);
        var identity = representation.identity();
        Files.createDirectories(store.metadataPath(identity).getParent());
        Files.write(store.metadataPath(identity), new byte[]{1, 2, 3});

        assertTrue(store.probeMetadata(identity).isEmpty());
        assertArrayEquals(new byte[]{1, 2, 3},
                Files.readAllBytes(store.metadataPath(identity)));
    }

    @Test
    void truncatedOrTrailingMetadataPrefixBecomesAMiss() throws Exception {
        var store = new RemoteModelStore(temp);
        var identity = representation.identity();
        var path = store.metadataPath(identity);
        Files.createDirectories(path.getParent());
        var valid = metadataPrefix();

        Files.write(path, Arrays.copyOf(valid, valid.length - 1));
        assertTrue(store.probeMetadata(identity).isEmpty());
        Files.write(path, Arrays.copyOf(valid, valid.length + 1));
        assertTrue(store.probeMetadata(identity).isEmpty());
        assertArrayEquals(Arrays.copyOf(valid, valid.length + 1),
                Files.readAllBytes(path));
    }

    @Test
    void invalidReplacementDoesNotOverwriteThePreviousPrefix() throws Exception {
        var store = new RemoteModelStore(temp);
        var identity = representation.identity();
        var expected = metadataPrefix();
        store.commitMetadataPrefix(identity, ArrayBuffer.borrow(expected));
        var invalid = ArrayBuffer.borrow(new byte[]{1, 2, 3});
        assertThrows(AssetLoadException.class,
                () -> store.commitMetadataPrefix(identity, invalid));
        assertArrayEquals(expected, Files.readAllBytes(store.metadataPath(identity)));
    }

    @Test
    void cachedOpenVerifiesEveryLazyChunkAndKeepsTheReturnedSourceOffline()
            throws Exception {
        var store = new RemoteModelStore(temp);
        var remote = commitMetadata(store);
        publishLazyChunks(store);

        var cached = remote.openCached(() -> false).orElseThrow();
        assertSame(remote.representation(), cached.representation());

        var victim = lazyChunks().get(0);
        Files.delete(store.chunkPath(remote.modelId(), new Hash256(victim.hash()),
                victim.encoding()));
        assertThrows(AssetLoadException.class, () ->
                cached.chunks().readStoredVerified(victim, BufferType.ARRAY));
    }

    @Test
    void missingCorruptAndInaccessibleChunksAreOfflineMissesWithoutFetch()
            throws Exception {
        var store = new RemoteModelStore(temp);
        var remote = commitMetadata(store);
        var victim = lazyChunks().get(0);
        assertTrue(remote.openCached(() -> false).isEmpty());

        publishLazyChunks(store);
        var path = store.chunkPath(remote.modelId(), new Hash256(victim.hash()),
                victim.encoding());
        Files.write(path, new byte[]{1, 2, 3});
        assertTrue(remote.openCached(() -> false).isEmpty());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(path),
                "a corrupt read must not mutate the exact cache path");

        publishLazyChunks(store);
        Files.delete(path);
        Files.createDirectory(path);
        assertTrue(remote.openCached(() -> false).isEmpty());
        assertTrue(Files.isDirectory(path), "access failures must not invalidate cache paths");
    }

    @Test
    void cancellationIsAnOfflineMissAndDoesNotPoisonLaterCachedOpen() throws Exception {
        var store = new RemoteModelStore(temp);
        var remote = commitMetadata(store);
        assertTrue(remote.openCached(() -> true).isEmpty());

        publishLazyChunks(store);
        assertTrue(remote.openCached(() -> false).isPresent());
    }

    @Test
    void cachedTargetProbeAndLoadAreOneWorkerTask() throws Exception {
        try (var writer = new RemoteModelStore(temp)) {
            commitMetadata(writer);
            publishLazyChunks(writer);
        }
        var cacheReadThread = new AtomicReference<String>();
        try (var store = new RemoteModelStore(temp, AtomicSharedCache::moveCommitted,
                ignored -> cacheReadThread.set(Thread.currentThread().getName()))) {
            var remote = store.probeMetadata(representation.identity()).orElseThrow();
            cacheReadThread.set(null);
            var submitted = new AtomicInteger();
            var loadThread = new AtomicReference<String>();
            var worker = Executors.newSingleThreadExecutor(runnable ->
                    new Thread(runnable, "remote-load-worker"));
            Executor executor = command -> {
                submitted.incrementAndGet();
                worker.execute(command);
            };
            try {
                var selection = selection();
                var result = remote.loadRenderTarget(() -> false,
                        selection.targetId, selection.texture, null, executor, prepared -> {
                            loadThread.set(Thread.currentThread().getName());
                            return prepared;
                        }).join();

                assertSame(remote, result);
                assertEquals(1, submitted.get());
                assertEquals("remote-load-worker", cacheReadThread.get());
                assertEquals("remote-load-worker", loadThread.get());
            } finally {
                worker.shutdownNow();
            }
        }
    }

    @Test
    void corruptRequiredChunksAreRefetchedBeforeOneWorkerLoad() throws Exception {
        var store = new RemoteModelStore(temp);
        var remote = commitMetadata(store);
        for (var chunk : lazyChunks()) {
            var path = store.chunkPath(remote.modelId(), new Hash256(chunk.hash()),
                    chunk.encoding());
            Files.createDirectories(path.getParent());
            Files.write(path, new byte[]{1, 2, 3});
        }
        var submitted = new AtomicInteger();
        var fetchThread = new AtomicReference<String>();
        var loadThread = new AtomicReference<String>();
        var requested = new AtomicReference<List<AssetContainerView.ChunkInfo>>();
        var worker = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "remote-refetch-worker"));
        Executor executor = command -> {
            submitted.incrementAndGet();
            worker.execute(command);
        };
        try {
            var selection = selection();
            var result = remote.loadRenderTarget(() -> false,
                    selection.targetId, selection.texture,
                    (identity, chunks, receiver) -> {
                        fetchThread.set(Thread.currentThread().getName());
                        requested.set(chunks);
                        try {
                            for (var chunk : chunks) {
                                receiver.accept(chunk,
                                        ArrayBuffer.borrow(storedBytes(chunk)));
                            }
                            return CompletableFuture.completedFuture(null);
                        } catch (Exception failure) {
                            return CompletableFuture.failedFuture(failure);
                        }
                    }, executor, prepared -> {
                        loadThread.set(Thread.currentThread().getName());
                        verifyChunks(prepared, requested.get());
                        return true;
                    }).join();

            assertTrue(result);
            assertEquals(2, submitted.get());
            assertEquals("remote-refetch-worker", fetchThread.get());
            assertEquals("remote-refetch-worker", loadThread.get());
            assertFalse(requested.get().isEmpty());
            verifyChunks(remote, requested.get());
        } finally {
            worker.shutdownNow();
            store.close();
        }
    }

    @Test
    void cacheCommitFailureDoesNotWithdrawVerifiedNetworkContent() throws Exception {
        try (var writer = new RemoteModelStore(temp)) {
            commitMetadata(writer);
        }
        try (var store = new RemoteModelStore(temp, (temporary, target) -> {
            throw new IOException("injected atomic move failure");
        }, ignored -> { })) {
            var remote = store.probeMetadata(representation.identity()).orElseThrow();
            var requested = new AtomicReference<List<AssetContainerView.ChunkInfo>>();
            var worker = Executors.newSingleThreadExecutor();
            try {
                var selection = selection();
                assertTrue(remote.loadRenderTarget(() -> false,
                        selection.targetId, selection.texture,
                        (identity, chunks, receiver) -> {
                            requested.set(chunks);
                            try {
                                for (var chunk : chunks) {
                                    receiver.accept(chunk,
                                            ArrayBuffer.borrow(storedBytes(chunk)));
                                }
                                return CompletableFuture.completedFuture(null);
                            } catch (Exception failure) {
                                return CompletableFuture.failedFuture(failure);
                            }
                        }, worker, prepared -> {
                            verifyChunks(prepared, requested.get());
                            return true;
                        }).join());
                assertFalse(requested.get().isEmpty());
                for (var chunk : requested.get()) {
                    assertFalse(Files.exists(store.chunkPath(remote.modelId(),
                            new Hash256(chunk.hash()), chunk.encoding())));
                }
            } finally {
                worker.shutdownNow();
            }
        }
    }

    @Test
    void cancelledReadRejectsBeforeCacheAccess()
            throws Exception {
        var valid = new byte[]{7, 8, 9};
        var chunk = chunk(valid);
        var identity = new ModelFileIdentity(hash(20), hash(21));
        var store = new RemoteModelStore(temp);
        try {
            var source = store.chunkSource(identity);

            assertThrows(CancellationException.class,
                    () -> source.readPayload(() -> true, chunk, BufferType.ARRAY));
            assertFalse(Files.exists(store.chunkPath(
                    identity.modelId(), new Hash256(chunk.hash()), chunk.encoding())));
        } finally {
            store.close();
        }
    }

    private static RemoteModelContent commitMetadata(RemoteModelStore store) throws Exception {
        return store.commitMetadataPrefix(representation.identity(),
                ArrayBuffer.borrow(metadataPrefix()));
    }

    private static void publishLazyChunks(RemoteModelStore store) throws IOException {
        for (var chunk : lazyChunks()) {
            store.publishChunk(representation.identity().modelId(), chunk, storedBytes(chunk));
        }
    }

    private static List<AssetContainerView.ChunkInfo> lazyChunks() {
        var asset = representation.view().getFileView().getAssetView();
        var manifest = asset.getChunkInfo(
                ModelFileConstant.MANIFEST_CHUNK_NAME);
        var metadataEnd = manifest.offset() + manifest.size();
        return asset.getChunkTable().values().stream()
                .filter(chunk -> chunk.offset() >= metadataEnd)
                .sorted(Comparator.comparingInt(AssetContainerView.ChunkInfo::offset)
                .thenComparing(AssetContainerView.ChunkInfo::type))
                .toList();
    }

    private static Selection selection() {
        var target = representation.view().getRenderTargets().stream()
                .findFirst().orElseThrow();
        return new Selection(target.id(), target.getTextureNames().stream()
                .findFirst().orElseThrow());
    }

    private static void verifyChunks(
            ModelContent content,
            List<AssetContainerView.ChunkInfo> chunks) {
        try {
            for (var chunk : chunks) {
                try (var stored = content.chunks().readStoredVerified(
                        chunk, BufferType.ARRAY)) {
                    assertArrayEquals(storedBytes(chunk), bytes(stored));
                }
            }
        } catch (Exception failure) {
            throw new CompletionException(failure);
        }
    }

    private static byte[] storedBytes(AssetContainerView.ChunkInfo chunk) throws IOException {
        try (var stored = sourceContent.chunks().readStoredVerified(chunk, BufferType.ARRAY)) {
            return bytes(stored);
        }
    }

    private static byte[] bytes(UniBuffer source) {
        var result = new byte[source.size()];
        source.nio().get(result);
        return result;
    }

    private static byte[] preamble() {
        return prefixRange(0, representation.view().getFileView().getAssetView()
                .getContainerPreambleSize());
    }

    private static byte[] manifest() {
        var chunk = representation.view().getFileView().getAssetView().getChunkInfo(
                ModelFileConstant.MANIFEST_CHUNK_NAME);
        return prefixRange(chunk.offset(), chunk.size());
    }

    private static byte[] prefixRange(int offset, int size) {
        try (var encoded = representation.metadataPrefix().orElseThrow();
             var prefix = encoded.acquireArray()) {
            return Arrays.copyOfRange(prefix.array(), prefix.arrayOffset() + offset,
                    prefix.arrayOffset() + offset + size);
        }
    }

    private static byte[] metadataPrefix() {
        try (var prefix = representation.metadataPrefix().orElseThrow()) {
            return prefixRange(0, prefix.size());
        }
    }

    private static AssetContainerView.ChunkInfo chunk(byte[] bytes) {
        var hash = Blake3.computeHash(ArrayBuffer.borrow(bytes));
        return new AssetContainerView.ChunkInfo(
                "test", "", 0, bytes.length, bytes.length, 0, 0, 0, hash);
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) marker;
        return new Hash256(bytes);
    }

    private record Selection(String targetId, String texture) {
    }

}
