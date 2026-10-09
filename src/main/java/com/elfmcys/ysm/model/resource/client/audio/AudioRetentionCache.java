package com.elfmcys.ysm.model.resource.client.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

final class AudioRetentionCache implements AutoCloseable {
    static final long DEFAULT_BUDGET = 64L * 1024 * 1024;
    static final long DEFAULT_TTL_NANOS = 30_000_000_000L;

    private final long budget;
    private final long ttlNanos;
    private final LongSupplier clock;
    private final Map<AudioCacheKey, Entry> entries = new HashMap<>();
    private long retainedBytes;
    private long sequence;
    private boolean closed;

    AudioRetentionCache() {
        this(DEFAULT_BUDGET, DEFAULT_TTL_NANOS, System::nanoTime);
    }

    AudioRetentionCache(long budget, long ttlNanos, LongSupplier clock) {
        if (budget < 0 || ttlNanos < 0) {
            throw new IllegalArgumentException("Audio retention limits must not be negative");
        }
        this.budget = budget;
        this.ttlNanos = ttlNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized Acquisition acquire(AudioCacheKey key) {
        requireOpen();
        long now = clock.getAsLong();
        expire(now);
        var entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        var receipt = receipt(now);
        entry.receipt = receipt;
        return new Acquisition(entry.audio.acquire(), receipt);
    }

    synchronized Receipt retainAfterAcquire(AudioCacheKey key, CachedAudio audio) {
        requireOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(audio, "audio");
        long now = clock.getAsLong();
        expire(now);
        var receipt = receipt(now);
        var existing = entries.get(key);
        if (existing != null) {
            existing.receipt = receipt;
            if (existing.audio.pcm() || !audio.pcm()) {
                return receipt;
            }
            remove(key, existing);
        }
        if (audio.size() <= budget) {
            put(key, audio.acquire(), receipt);
            evictToBudget();
        }
        return receipt;
    }

    synchronized boolean publishPcm(AudioCacheKey key, CachedAudio pcm,
                                    Receipt candidateReceipt) {
        requireOpen();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(pcm, "pcm");
        Objects.requireNonNull(candidateReceipt, "candidateReceipt");
        if (!pcm.pcm()) {
            throw new IllegalArgumentException("Published audio must be PCM");
        }
        long now = clock.getAsLong();
        expire(now);
        var existing = entries.get(key);
        if (existing != null && existing.audio.pcm()) {
            return false;
        }
        var retainedReceipt = existing == null ? candidateReceipt : existing.receipt;
        if (expired(retainedReceipt, now) || pcm.size() > budget) {
            return false;
        }
        if (existing != null) {
            remove(key, existing);
        }
        put(key, pcm.acquire(), retainedReceipt);
        evictToBudget();
        var installed = entries.get(key);
        return installed != null && installed.audio.pcm();
    }

    synchronized void invalidateEncoded(AudioCacheKey key) {
        if (closed) {
            return;
        }
        var existing = entries.get(Objects.requireNonNull(key, "key"));
        if (existing != null && !existing.audio.pcm()) {
            remove(key, existing);
        }
    }

    synchronized void maintain() {
        if (!closed) {
            expire(clock.getAsLong());
        }
    }

    synchronized long retainedBytes() {
        return retainedBytes;
    }

    synchronized int entryCount() {
        return entries.size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        entries.values().forEach(entry -> entry.audio.close());
        entries.clear();
        retainedBytes = 0;
    }

    private Receipt receipt(long now) {
        return new Receipt(now, ++sequence);
    }

    private void put(AudioCacheKey key, CachedAudio audio, Receipt receipt) {
        entries.put(key, new Entry(audio, receipt));
        retainedBytes = Math.addExact(retainedBytes, audio.size());
    }

    private void expire(long now) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!expired(entry.getValue().receipt, now)) {
                continue;
            }
            retainedBytes -= entry.getValue().audio.size();
            entry.getValue().audio.close();
            iterator.remove();
        }
    }

    private boolean expired(Receipt receipt, long now) {
        return now - receipt.acquiredAtNanos >= ttlNanos;
    }

    private void evictToBudget() {
        while (retainedBytes > budget) {
            Map.Entry<AudioCacheKey, Entry> oldest = null;
            for (var entry : entries.entrySet()) {
                if (oldest == null || entry.getValue().receipt.sequence
                        < oldest.getValue().receipt.sequence) {
                    oldest = entry;
                }
            }
            if (oldest == null) {
                throw new IllegalStateException("Audio retention ledger is inconsistent");
            }
            remove(oldest.getKey(), oldest.getValue());
        }
    }

    private void remove(AudioCacheKey key, Entry entry) {
        if (entries.remove(key, entry)) {
            retainedBytes -= entry.audio.size();
            entry.audio.close();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Audio runtime is closed");
        }
    }

    record Receipt(long acquiredAtNanos, long sequence) {
    }

    record Acquisition(CachedAudio audio, Receipt receipt) {
    }

    private static final class Entry {
        private final CachedAudio audio;
        private Receipt receipt;

        private Entry(CachedAudio audio, Receipt receipt) {
            this.audio = audio;
            this.receipt = receipt;
        }
    }
}
