package com.elfmcys.ysm.model.resource.client.audio;

import com.elfmcys.ysm.client.sound.stream.AudioStreamProvider;
import com.elfmcys.ysm.client.sound.stream.CustomAudioStream;
import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.schema.model.views.SoundStreamView;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.resource.client.SoundSource;
import com.elfmcys.ysm.model.resource.client.remote.RemoteChunkFetcher;
import com.elfmcys.ysm.model.resource.client.remote.RemoteModelContent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns client model-audio retention and creates one independent playback per trigger. */
public final class ClientAudioRuntime implements AutoCloseable {
    private final Executor workers;
    private final AccessResolver accessResolver;
    private final AudioRetentionCache retention;
    private final Set<Playback> playbacks = Collections.newSetFromMap(
            new IdentityHashMap<>());
    private boolean closed;

    public ClientAudioRuntime(Executor workers, AccessResolver accessResolver) {
        this(workers, accessResolver, new AudioRetentionCache());
    }

    ClientAudioRuntime(Executor workers, AccessResolver accessResolver,
                       AudioRetentionCache retention) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.retention = Objects.requireNonNull(retention, "retention");
    }

    public synchronized AudioStreamProvider createPlayback(SoundSource source) {
        Objects.requireNonNull(source, "source");
        if (closed) {
            throw new IllegalStateException("Audio runtime is closed");
        }
        var playback = new Playback(source);
        playbacks.add(playback);
        return playback;
    }

    public void maintain() {
        retention.maintain();
    }

    public void stopAll() {
        ArrayList<Playback> active;
        synchronized (this) {
            active = new ArrayList<>(playbacks);
        }
        active.forEach(Playback::stop);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        stopAll();
        retention.close();
    }

    private CompletableFuture<EncodedAudio> load(
            SoundAccess access, SoundSource source, AtomicBoolean cancelled) {
        if (access.content() instanceof RemoteModelContent remote) {
            return remote.loadSound(cancelled::get, source.stream(), access.fetcher(),
                    workers, prepared -> loadEncoded(source, cancelled, prepared));
        }
        return CompletableFuture.supplyAsync(
                () -> loadEncoded(source, cancelled, access.content()), workers);
    }

    private static EncodedAudio loadEncoded(
            SoundSource source, AtomicBoolean cancelled, ModelContent content) {
        try (var admitted = readVerified(source, cancelled, content)) {
            return new EncodedAudio(admitted.acquireEncoded(), admitted.media());
        }
    }

    private static SoundStreamView.AdmittedSound readVerified(
            SoundSource source, AtomicBoolean cancelled, ModelContent content) {
        try {
            return source.stream().readVerified(cancelled::get, content.chunks());
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
    }

    private void unregister(Playback playback) {
        synchronized (this) {
            playbacks.remove(playback);
        }
    }

    private final class Playback implements AudioStreamProvider {
        private final SoundSource source;
        private final AudioCacheKey key;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CompletableFuture<Void> terminal = new CompletableFuture<>();
        private CompletableFuture<EncodedAudio> loading;
        private CompletableFuture<CustomAudioStream> result;
        private PlaybackAudioStream stream;
        private boolean opened;
        private boolean stopped;

        private Playback(SoundSource source) {
            this.source = source;
            key = AudioCacheKey.from(source);
        }

        @Override
        public CompletableFuture<CustomAudioStream> openStream(boolean looping) {
            SoundAccess access;
            synchronized (this) {
                if (opened) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Playback stream was already requested"));
                }
                opened = true;
                result = new CompletableFuture<>();
                if (stopped) {
                    result.completeExceptionally(new CancellationException(
                            "Playback was stopped before acquisition"));
                    return result;
                }
            }
            try {
                access = accessResolver.resolve(source);
            } catch (Throwable failure) {
                fail(failure);
                return result;
            }

            PlaybackAudioStream rejected = null;
            synchronized (this) {
                if (stopped) {
                    return result;
                }
                var ready = retention.acquire(key);
                if (ready != null) {
                    rejected = completeLocked(
                            ready.audio(), ready.receipt(), looping);
                }
            }
            closeQuietly(rejected);
            if (rejected != null || result.isDone()) {
                return result;
            }

            var future = load(access, source, cancelled);
            synchronized (this) {
                if (stopped) {
                    future.cancel(false);
                } else {
                    loading = future;
                }
            }
            future.whenComplete((audio, failure) -> {
                if (failure != null) {
                    fail(unwrap(failure));
                    return;
                }
                try {
                    PlaybackAudioStream rejectedStream;
                    synchronized (this) {
                        loading = null;
                        if (stopped) {
                            audio.close();
                            return;
                        }
                        var receipt = retention.retainAfterAcquire(key, audio);
                        rejectedStream = completeLocked(audio, receipt, looping);
                    }
                    closeQuietly(rejectedStream);
                } catch (Throwable completionFailure) {
                    audio.close();
                    fail(completionFailure);
                }
            });
            return result;
        }

        private PlaybackAudioStream completeLocked(
                CachedAudio audio, AudioRetentionCache.Receipt receipt,
                boolean looping) {
            loading = null;
            PlaybackAudioStream openedStream;
            try {
                openedStream = new PlaybackAudioStream(audio, looping, receipt,
                        this::canPublish,
                        (pcm, candidateReceipt) -> publish(
                                this, pcm, candidateReceipt),
                        () -> streamClosed(this),
                        failure -> streamFailed(this, failure));
            } catch (Throwable failure) {
                audio.close();
                invalidateEncodedIfContentFailure(key, failure);
                failLocked(failure);
                return null;
            }
            stream = openedStream;
            if (result.complete(openedStream)) {
                return null;
            }
            stream = null;
            return openedStream;
        }

        private void fail(Throwable failure) {
            synchronized (this) {
                failLocked(failure);
            }
        }

        private synchronized boolean canPublish() {
            return !stopped;
        }

        private void failLocked(Throwable failure) {
            stopped = true;
            cancelled.set(true);
            loading = null;
            if (result != null) {
                result.completeExceptionally(failure);
            }
            unregister(this);
            terminal.complete(null);
        }

        @Override
        public void stop() {
            CompletableFuture<EncodedAudio> pending;
            PlaybackAudioStream active;
            synchronized (this) {
                if (stopped) {
                    return;
                }
                stopped = true;
                cancelled.set(true);
                pending = loading;
                loading = null;
                active = stream;
                stream = null;
                if (result != null) {
                    result.completeExceptionally(new CancellationException(
                            "Playback was stopped"));
                }
            }
            if (pending != null) {
                pending.cancel(false);
            }
            closeQuietly(active);
            unregister(this);
            terminal.complete(null);
        }

        @Override
        public CompletionStage<Void> stopped() {
            return terminal.minimalCompletionStage();
        }
    }

    private void publish(Playback playback, PcmAudio pcm,
                         AudioRetentionCache.Receipt receipt) {
        synchronized (this) {
            if (closed) {
                return;
            }
        }
        synchronized (playback) {
            if (playback.stopped) {
                return;
            }
            retention.publishPcm(playback.key, pcm, receipt);
        }
    }

    private void streamClosed(Playback playback) {
        synchronized (playback) {
            playback.stream = null;
            playback.stopped = true;
            playback.cancelled.set(true);
        }
        unregister(playback);
        playback.terminal.complete(null);
    }

    private void streamFailed(Playback playback, Throwable failure) {
        synchronized (playback) {
            invalidateEncodedIfContentFailure(playback.key, failure);
        }
    }

    private void invalidateEncodedIfContentFailure(AudioCacheKey key, Throwable failure) {
        if (failure instanceof AssetLoadException asset
                && asset.reason() == AssetLoadException.Reason.CONTENT) {
            retention.invalidateEncoded(key);
        }
    }

    private static void closeQuietly(PlaybackAudioStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    public interface AccessResolver {
        SoundAccess resolve(SoundSource source) throws AssetLoadException;
    }

    public record SoundAccess(ModelContent content, RemoteChunkFetcher fetcher) {
        public SoundAccess {
            Objects.requireNonNull(content, "content");
        }
    }
}
