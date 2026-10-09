package com.elfmcys.ysm.model.resource.server;

import com.elfmcys.ysm.model.resource.RuntimeContentStore;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.util.CleanerUtil;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Process server runtime for verified stored chunk bytes and their transfer leases. */
public final class ServerChunkRuntime implements AutoCloseable {
    static final long IDLE_NANOS = Duration.ofSeconds(30).toNanos();
    static final long MAX_CACHED_BYTES = 64L * 1024 * 1024;
    static final int MAX_IN_FLIGHT = 256;
    static final int MAX_REQUEST_BACKLOG = 4_096;
    private static final int DISPOSITIONS_PER_TICK = 4;
    private static final int REQUESTS_PER_TICK = 256;
    private static final int RELEASES_PER_TICK = 1_024;

    private final Object lock = new Object();
    private final LinkedHashMap<Key, Entry> ready =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Map<FlightKey, Flight> pending = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Completion> completions = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Entry> releases = new ConcurrentLinkedQueue<>();
    private final RuntimeContentStore contentStore;
    private final ScheduledThreadPoolExecutor workers;
    private final LongSupplier nanoTime;
    private final long maxCachedBytes;
    private final AtomicInteger requestBacklog = new AtomicInteger();
    private long cachedBytes;
    private boolean closed;

    public ServerChunkRuntime(Consumer<IOException> corruptionReporter) {
        this(corruptionReporter, System::nanoTime, MAX_CACHED_BYTES);
    }

    ServerChunkRuntime(Consumer<IOException> corruptionReporter, LongSupplier nanoTime) {
        this(corruptionReporter, nanoTime, MAX_CACHED_BYTES);
    }

    ServerChunkRuntime(Consumer<IOException> corruptionReporter, LongSupplier nanoTime,
                       long maxCachedBytes) {
        contentStore = new RuntimeContentStore(corruptionReporter);
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (maxCachedBytes < 1) {
            throw new IllegalArgumentException(
                    "Server chunk cache limit must be positive");
        }
        this.maxCachedBytes = maxCachedBytes;
        workers = new ScheduledThreadPoolExecutor(Math.max(2,
                Runtime.getRuntime().availableProcessors() / 4), runnable -> {
            var thread = new Thread(runnable, "YSM Server Resource Worker");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY,
                    Thread.NORM_PRIORITY - 1));
            return thread;
        });
        workers.setRemoveOnCancelPolicy(true);
    }

    public CompletableFuture<ChunkLease> acquire(
            ModelContent content, AssetContainerView.ChunkInfo chunk) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(chunk, "chunk");
        synchronized (lock) {
            if (closed) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Server chunk runtime is closed"));
            }
            if (requestBacklog.incrementAndGet() > MAX_REQUEST_BACKLOG) {
                requestBacklog.decrementAndGet();
                return CompletableFuture.failedFuture(
                        new IllegalStateException(
                                "Server chunk request capacity is full"));
            }
            var result = new CompletableFuture<ChunkLease>();
            requests.add(new Request(content, chunk, result));
            return result;
        }
    }

    public void tick() {
        contentStore.tick();
        processReleases();
        processRequests();
        for (var disposed = 0; disposed < DISPOSITIONS_PER_TICK; disposed++) {
            var completion = completions.poll();
            if (completion == null) {
                break;
            }
            accept(completion);
        }
        evict();
    }

    int cachedCount() {
        synchronized (lock) {
            return ready.size();
        }
    }

    long cachedBytes() {
        synchronized (lock) {
            return cachedBytes;
        }
    }

    private void processRequests() {
        for (var disposed = 0; disposed < REQUESTS_PER_TICK; disposed++) {
            var request = requests.poll();
            if (request == null) {
                return;
            }
            requestBacklog.decrementAndGet();
            if (request.result.isCancelled()) {
                continue;
            }
            final Flight start;
            final Entry cached;
            synchronized (lock) {
                if (closed) {
                    request.result.completeExceptionally(
                            new IllegalStateException("Server chunk runtime is closed"));
                    continue;
                }
                var key = Key.of(request.content, request.chunk);
                cached = ready.get(key);
                if (cached != null) {
                    start = null;
                } else {
                    var flightKey = new FlightKey(key, request.content);
                    var flight = pending.get(flightKey);
                    if (flight == null) {
                        if (pending.size() >= MAX_IN_FLIGHT) {
                            request.result.completeExceptionally(
                                    new IllegalStateException(
                                            "Server chunk in-flight capacity is full"));
                            continue;
                        }
                        flight = new Flight(flightKey, contentStore.exact(request.content),
                                request.chunk);
                        pending.put(flightKey, flight);
                        start = flight;
                    } else {
                        start = null;
                    }
                    flight.waiters.add(request.result);
                }
            }
            if (cached != null) {
                complete(request.result, cached);
                continue;
            }
            if (start != null) {
                try {
                    workers.execute(() -> load(start));
                } catch (RuntimeException rejected) {
                    enqueueCompletion(new Completion(start, null, rejected));
                }
            }
        }
    }

    private void load(Flight flight) {
        try (var stored = flight.content.chunks().readStoredVerified(
                 flight.chunk, BufferType.ARRAY)) {
            enqueueCompletion(new Completion(flight, stored.acquireArray(), null));
        } catch (Throwable failure) {
            enqueueCompletion(new Completion(flight, null, failure));
        }
    }

    private void enqueueCompletion(Completion completion) {
        synchronized (lock) {
            if (!closed && pending.get(completion.flight.flightKey) == completion.flight) {
                completions.add(completion);
            } else if (completion.bytes != null) {
                completion.bytes.close();
            }
        }
    }

    private void accept(Completion completion) {
        final List<CompletableFuture<ChunkLease>> waiters;
        Entry entry = null;
        synchronized (lock) {
            if (pending.remove(completion.flight.flightKey) != completion.flight) {
                if (completion.bytes != null) completion.bytes.close();
                return;
            }
            waiters = List.copyOf(completion.flight.waiters);
            if (completion.failure == null) {
                entry = ready.get(completion.flight.flightKey.key);
                if (entry == null) {
                    entry = new Entry(completion.flight.flightKey.key, completion.bytes,
                            nanoTime.getAsLong());
                    if (entry.bytes.size() <= maxCachedBytes) {
                        ready.put(entry.key, entry);
                        cachedBytes += entry.bytes.size();
                    }
                } else {
                    completion.bytes.close();
                }
            }
        }
        if (entry == null) {
            var failure = completion.failure == null
                    ? new IllegalStateException("Chunk load produced no bytes")
                    : completion.failure;
            waiters.forEach(waiter -> waiter.completeExceptionally(failure));
            return;
        }
        for (var waiter : waiters) {
            complete(waiter, entry);
        }
    }

    private void complete(CompletableFuture<ChunkLease> result, Entry entry) {
        var lease = new ChunkLease(this, entry);
        if (result.complete(lease)) {
            entry.consumers++;
            entry.lastAcquire = nanoTime.getAsLong();
            lease.arm();
        } else {
            lease.discard();
        }
    }

    private void processReleases() {
        for (var disposed = 0; disposed < RELEASES_PER_TICK; disposed++) {
            var entry = releases.poll();
            if (entry == null) {
                return;
            }
            synchronized (lock) {
                if (entry.consumers > 0) {
                    entry.consumers--;
                }
            }
        }
    }

    private void evict() {
        var now = nanoTime.getAsLong();
        synchronized (lock) {
            var iterator = ready.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next().getValue();
                if (now - entry.lastAcquire >= IDLE_NANOS) {
                    iterator.remove();
                    cachedBytes -= entry.bytes.size();
                }
            }
            iterator = ready.entrySet().iterator();
            while (cachedBytes > maxCachedBytes && iterator.hasNext()) {
                var entry = iterator.next().getValue();
                iterator.remove();
                cachedBytes -= entry.bytes.size();
            }
        }
    }

    private void release(Entry entry) {
        releases.add(entry);
    }

    @Override
    public void close() {
        var failures = new ArrayList<CompletableFuture<ChunkLease>>();
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            pending.values().forEach(flight -> failures.addAll(flight.waiters));
            pending.clear();
            ready.clear();
            cachedBytes = 0;
        }
        for (Request request; (request = requests.poll()) != null;) {
            requestBacklog.decrementAndGet();
            failures.add(request.result);
        }
        failures.forEach(result -> result.completeExceptionally(
                new IllegalStateException("Server chunk runtime is closed")));
        for (Completion completion; (completion = completions.poll()) != null;) {
            if (completion.bytes != null) completion.bytes.close();
        }
        releases.clear();
        workers.shutdownNow();
    }

    public static final class ChunkLease implements AutoCloseable {
        private final LeaseState state;
        private final Cleaner.Cleanable cleanable;

        private ChunkLease(ServerChunkRuntime owner, Entry entry) {
            state = new LeaseState(owner, entry);
            cleanable = CleanerUtil.ref(this, state, LeaseState::release);
        }

        public int size() {
            return requireOpen().bytes.size();
        }

        public void copyTo(int offset, ByteBuffer target) {
            var bytes = requireOpen().bytes;
            if (offset < 0 || target.remaining() > bytes.size() - offset) {
                throw new IndexOutOfBoundsException("Chunk lease range is invalid");
            }
            var source = bytes.nio();
            source.position(offset).limit(offset + target.remaining());
            target.put(source);
        }

        @Override
        public void close() {
            cleanable.clean();
        }

        private Entry requireOpen() {
            return state.requireOpen();
        }

        private void arm() {
            state.arm();
        }

        private void discard() {
            state.discard();
            cleanable.clean();
        }
    }

    private static final class LeaseState {
        private final ServerChunkRuntime owner;
        private final Entry entry;
        private boolean armed;
        private boolean closeRequested;
        private boolean released;

        private LeaseState(ServerChunkRuntime owner, Entry entry) {
            this.owner = owner;
            this.entry = entry;
        }

        private synchronized void arm() {
            if (released) {
                throw new IllegalStateException("Chunk lease was discarded before acquisition");
            }
            armed = true;
            if (closeRequested) {
                released = true;
                owner.release(entry);
            }
        }

        private synchronized void release() {
            if (released) {
                return;
            }
            if (!armed) {
                closeRequested = true;
                return;
            }
            released = true;
            owner.release(entry);
        }

        private synchronized void discard() {
            released = true;
        }

        private synchronized Entry requireOpen() {
            if (released || closeRequested) {
                throw new IllegalStateException("Chunk lease is closed");
            }
            return entry;
        }
    }

    private record Request(ModelContent content,
                           AssetContainerView.ChunkInfo chunk,
                           CompletableFuture<ChunkLease> result) {
    }

    private static final class Flight {
        private final FlightKey flightKey;
        private final ModelContent content;
        private final AssetContainerView.ChunkInfo chunk;
        private final List<CompletableFuture<ChunkLease>> waiters = new ArrayList<>();

        private Flight(FlightKey flightKey, ModelContent content,
                       AssetContainerView.ChunkInfo chunk) {
            this.flightKey = flightKey;
            this.content = content;
            this.chunk = chunk;
        }
    }

    private static final class Entry {
        private final Key key;
        private final ArrayBuffer bytes;
        private long lastAcquire;
        private int consumers;

        private Entry(Key key, ArrayBuffer bytes, long lastAcquire) {
            this.key = key;
            this.bytes = bytes;
            this.lastAcquire = lastAcquire;
        }
    }

    private record Completion(Flight flight, ArrayBuffer bytes, Throwable failure) {
    }

    private record Key(ModelFileIdentity identity, String type, Hash256 hash,
                       String encoding, int storedSize, int decodedSize) {
        private static Key of(ModelContent content,
                              AssetContainerView.ChunkInfo chunk) {
            return new Key(content.representation().identity(), chunk.type(),
                    new Hash256(chunk.hash()), chunk.encoding(),
                    chunk.size(), chunk.decodeSize());
        }
    }

    private static final class FlightKey {
        private final Key key;
        private final ModelContent source;

        private FlightKey(Key key, ModelContent source) {
            this.key = key;
            this.source = source;
        }

        @Override
        public boolean equals(Object value) {
            return this == value || value instanceof FlightKey other
                    && key.equals(other.key) && source == other.source;
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + System.identityHashCode(source);
        }
    }
}
