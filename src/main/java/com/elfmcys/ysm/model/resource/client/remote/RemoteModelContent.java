package com.elfmcys.ysm.model.resource.client.remote;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.container.ChunkDecoding;
import com.elfmcys.ysm.format.schema.file.ChunkDataSource;
import com.elfmcys.ysm.format.schema.model.ModelFileConstant;
import com.elfmcys.ysm.format.schema.model.views.SoundStreamView;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.domain.ModelRepresentation;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Verified remote metadata with cache-first, on-demand chunk access. */
public final class RemoteModelContent implements ModelContent {
    private final ModelRepresentation representation;
    private final ChunkDataSource chunks;
    private final ChunkDataSource cachedChunks;
    private final RemoteModelStore store;

    RemoteModelContent(ModelRepresentation representation, ChunkDataSource chunks,
                       ChunkDataSource cachedChunks, RemoteModelStore store) {
        this.representation = Objects.requireNonNull(representation, "representation");
        this.chunks = Objects.requireNonNull(chunks, "chunks");
        this.cachedChunks = Objects.requireNonNull(cachedChunks, "cachedChunks");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public ModelRepresentation representation() {
        return representation;
    }

    @Override
    public ChunkDataSource chunks() {
        return chunks;
    }

    public <T> CompletableFuture<T> loadRenderTarget(
            BooleanSupplier cancelled, String targetId, String textureName,
            RemoteChunkFetcher fetcher, Executor workers,
            Function<ModelContent, T> load) {
        return store.loadRenderTarget(
                this, targetId, textureName, cancelled, fetcher, workers, load);
    }

    public <T> CompletableFuture<T> loadSound(
            BooleanSupplier cancelled, SoundStreamView sound,
            RemoteChunkFetcher fetcher, Executor workers,
            Function<ModelContent, T> load) {
        Objects.requireNonNull(sound, "sound");
        return store.loadChunks(this, cancelled, fetcher, workers,
                List.of(sound.chunkInfo()), load);
    }

    public void publish(AssetContainerView.ChunkInfo chunk, UniBuffer bytes) throws IOException {
        store.publishChunk(representation.modelId(), chunk, bytes);
    }

    /** Opens this exact representation only when every lazy chunk is cached and verified. */
    public Optional<ModelContent> openCached(BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled, "cancelled");
        var asset = representation.view().getFileView().getAssetView();
        var manifest = Objects.requireNonNull(
                asset.getChunkInfo(ModelFileConstant.MANIFEST_CHUNK_NAME), "manifest");
        var metadataEnd = Math.addExact(manifest.offset(), manifest.size());
        var lazyChunks = asset.getChunkTable().values().stream()
                .filter(chunk -> chunk.offset() >= metadataEnd)
                .sorted(Comparator.comparingInt(
                                AssetContainerView.ChunkInfo::offset)
                        .thenComparing(
                                AssetContainerView.ChunkInfo::type))
                .toList();

        try {
            for (var chunk : lazyChunks) {
                try (var ignored = cachedChunks.readStoredVerified(chunk, BufferType.ARRAY)) {
                    if (cancelled.getAsBoolean()) {
                        throw new CancellationException(
                                "Cached model verification was cancelled");
                    }
                }
            }
            return cancelled.getAsBoolean()
                    ? Optional.empty()
                    : Optional.of(new CachedModelContent(representation, cachedChunks));
        } catch (IOException | CancellationException unavailable) {
            return Optional.empty();
        }
    }

    RemoteModelContent withReceived(Map<String, ? extends UniBuffer> received) {
        return new RemoteModelContent(representation,
                new ReceivedChunkDataSource(chunks, received), cachedChunks, store);
    }

    void closeReceived() {
        if (chunks instanceof ReceivedChunkDataSource received) {
            received.close();
        }
    }

    private static final class ReceivedChunkDataSource
            implements ChunkDataSource, AutoCloseable {
        private final ChunkDataSource fallback;
        private final Map<String, UniBuffer> received;

        private ReceivedChunkDataSource(ChunkDataSource fallback,
                                        Map<String, ? extends UniBuffer> received) {
            this.fallback = Objects.requireNonNull(fallback, "fallback");
            var copies = new LinkedHashMap<String, UniBuffer>();
            try {
                received.forEach((type, bytes) -> copies.put(type, bytes.acquire()));
                this.received = Map.copyOf(copies);
            } catch (RuntimeException | Error failure) {
                copies.values().forEach(UniBuffer::close);
                throw failure;
            }
        }

        @Override
        public UniBuffer readPayload(
                AssetContainerView.ChunkInfo chunk, BufferType bufferType) throws IOException {
            if (!ChunkDecoding.isZstd(chunk)) {
                return readStoredVerified(chunk, bufferType);
            }
            try (var stored = readStoredVerified(chunk, BufferType.NATIVE)) {
                return ChunkDecoding.decodeZstd(stored, chunk, bufferType);
            }
        }

        @Override
        public UniBuffer readStoredVerified(
                AssetContainerView.ChunkInfo chunk, BufferType bufferType) throws IOException {
            var bytes = received.get(chunk.type());
            if (bytes == null) {
                return fallback.readStoredVerified(chunk, bufferType);
            }
            var array = bytes.copy();
            try (array) {
                ChunkDecoding.validateStored(array, chunk);
                return bufferType == BufferType.NATIVE
                        ? array.acquireNative()
                        : array.acquireArray();
            }
        }

        @Override
        public void close() {
            received.values().forEach(UniBuffer::close);
        }
    }

    private record CachedModelContent(ModelRepresentation representation,
                                      ChunkDataSource chunks) implements ModelContent {
    }
}
