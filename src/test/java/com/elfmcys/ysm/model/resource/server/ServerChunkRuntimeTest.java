package com.elfmcys.ysm.model.resource.server;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.schema.file.ChunkDataSource;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelRepresentation;
import com.elfmcys.ysm.util.UnsafeUtil;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerChunkRuntimeTest {
    @Test
    void idleEvictionDropsOnlyCacheReachabilityWhileAnActiveLeaseStaysValid()
            throws Exception {
        var now = new AtomicLong();
        var source = source(identity(1, 11), bytes(1, 2, 3));
        try (var runtime = new ServerChunkRuntime(ignored -> { }, now::get, 16)) {
            var lease = finish(runtime, runtime.acquire(source.content(), source.chunk()));
            assertEquals(1, runtime.cachedCount());

            now.set(ServerChunkRuntime.IDLE_NANOS - 1);
            runtime.tick();
            assertEquals(1, runtime.cachedCount());
            now.incrementAndGet();
            runtime.tick();

            assertEquals(0, runtime.cachedCount());
            assertArrayEquals(bytes(1, 2, 3), copy(lease));
            lease.close();
        }
    }

    @Test
    void bytePressureEvictsTheLruCacheEntryWithoutClosingEitherLease()
            throws Exception {
        var now = new AtomicLong();
        var first = source(identity(2, 12), bytes(1, 2, 3));
        var second = source(identity(3, 13), bytes(4, 5, 6));
        try (var runtime = new ServerChunkRuntime(ignored -> { }, now::get, 5)) {
            var firstLease = finish(runtime,
                    runtime.acquire(first.content(), first.chunk()));
            now.incrementAndGet();
            var secondLease = finish(runtime,
                    runtime.acquire(second.content(), second.chunk()));

            assertEquals(1, runtime.cachedCount());
            assertEquals(3, runtime.cachedBytes());
            assertArrayEquals(bytes(1, 2, 3), copy(firstLease));
            assertArrayEquals(bytes(4, 5, 6), copy(secondLease));

            var firstAgain = finish(runtime,
                    runtime.acquire(first.content(), first.chunk()));
            assertEquals(2, first.reads().get());
            firstAgain.close();
            firstLease.close();
            secondLease.close();
        }
    }

    @Test
    void oversizedChunkIsDeliveredWithoutEnteringTheResidentCache() throws Exception {
        var source = source(identity(4, 14), bytes(1, 2, 3));
        try (var runtime = new ServerChunkRuntime(ignored -> { }, () -> 0L, 2)) {
            var lease = finish(runtime, runtime.acquire(source.content(), source.chunk()));

            assertEquals(0, runtime.cachedCount());
            assertEquals(0, runtime.cachedBytes());
            assertArrayEquals(bytes(1, 2, 3), copy(lease));
            lease.close();
        }
    }

    @Test
    void pendingReadsCoalesceOnlyForTheSameExactSourceButReadyBytesAreReusable()
            throws Exception {
        var identity = identity(5, 15);
        var first = source(identity, bytes(7, 8, 9));
        try (var runtime = new ServerChunkRuntime(ignored -> { }, () -> 0L, 16)) {
            var sameSourceA = runtime.acquire(first.content(), first.chunk());
            var sameSourceB = runtime.acquire(first.content(), first.chunk());
            var firstLease = finish(runtime, sameSourceA);
            var secondLease = finish(runtime, sameSourceB);
            assertEquals(1, first.reads().get());

            var equivalent = source(identity, bytes(7, 8, 9));
            var reused = finish(runtime,
                    runtime.acquire(equivalent.content(), equivalent.chunk()));
            assertEquals(0, equivalent.reads().get());
            assertEquals(3, runtime.cachedBytes());

            firstLease.close();
            secondLease.close();
            reused.close();
        }
    }

    @Test
    void concurrentEquivalentExactSourcesDoNotDoubleCountOneReadyEntry()
            throws Exception {
        var identity = identity(6, 16);
        var first = source(identity, bytes(1, 1, 1));
        var second = source(identity, bytes(1, 1, 1));
        try (var runtime = new ServerChunkRuntime(ignored -> { }, () -> 0L, 16)) {
            var a = runtime.acquire(first.content(), first.chunk());
            var b = runtime.acquire(second.content(), second.chunk());
            var firstLease = finish(runtime, a);
            var secondLease = finish(runtime, b);

            assertEquals(1, first.reads().get());
            assertEquals(1, second.reads().get());
            assertEquals(1, runtime.cachedCount());
            assertEquals(3, runtime.cachedBytes());
            firstLease.close();
            secondLease.close();
        }
    }

    @Test
    void acquireAfterRuntimeCloseFailsWithoutQueuingWork() {
        var source = source(identity(7, 17), bytes(1));
        var runtime = new ServerChunkRuntime(ignored -> { });
        runtime.close();

        var failure = runtime.acquire(source.content(), source.chunk());
        assertTrue(failure.isCompletedExceptionally());
        assertThrows(Exception.class, () -> failure.get(1, TimeUnit.SECONDS));
        assertEquals(0, source.reads().get());
    }

    private static ServerChunkRuntime.ChunkLease finish(
            ServerChunkRuntime runtime,
            CompletableFuture<ServerChunkRuntime.ChunkLease> result) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!result.isDone() && System.nanoTime() < deadline) {
            runtime.tick();
            Thread.onSpinWait();
        }
        return result.get(1, TimeUnit.SECONDS);
    }

    private static byte[] copy(ServerChunkRuntime.ChunkLease lease) {
        var target = ByteBuffer.allocate(lease.size());
        lease.copyTo(0, target);
        return target.array();
    }

    private static Source source(ModelFileIdentity identity, byte[] bytes) {
        var reads = new AtomicInteger();
        var chunk = new AssetContainerView.ChunkInfo(
                "test", "raw", 0, bytes.length, bytes.length,
                0, bytes.length, 0, hash(bytes[0]).bytes());
        ChunkDataSource chunks = new ChunkDataSource() {
            @Override
            public UniBuffer readPayload(AssetContainerView.ChunkInfo ignored,
                                         BufferType bufferType) {
                throw new AssertionError("Stored-byte cache must not request decoded payload");
            }

            @Override
            public UniBuffer readStoredVerified(AssetContainerView.ChunkInfo ignored,
                                                BufferType bufferType) {
                reads.incrementAndGet();
                return ArrayBuffer.move(Arrays.copyOf(bytes, bytes.length));
            }
        };
        var representation = representation(identity);
        ModelContent content = new ModelContent() {
            @Override
            public ModelRepresentation representation() {
                return representation;
            }

            @Override
            public ChunkDataSource chunks() {
                return chunks;
            }
        };
        return new Source(content, chunk, reads);
    }

    private static ModelRepresentation representation(ModelFileIdentity identity) {
        try {
            var value = (ModelRepresentation) UnsafeUtil.getUnsafe()
                    .allocateInstance(ModelRepresentation.class);
            var field = ModelRepresentation.class.getDeclaredField("identity");
            UnsafeUtil.getUnsafe().putObject(
                    value, UnsafeUtil.getUnsafe().objectFieldOffset(field), identity);
            return value;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ModelFileIdentity identity(int model, int container) {
        return new ModelFileIdentity(hash(model), hash(container));
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) marker;
        return new Hash256(bytes);
    }

    private static byte[] bytes(int... values) {
        var result = new byte[values.length];
        for (var index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private record Source(ModelContent content, AssetContainerView.ChunkInfo chunk,
                          AtomicInteger reads) {
    }
}
