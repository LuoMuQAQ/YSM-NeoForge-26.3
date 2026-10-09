package com.elfmcys.ysm.client.sound.instance;

import com.elfmcys.ysm.client.sound.stream.AudioStreamProvider;
import com.elfmcys.ysm.client.sound.stream.CustomAudioStream;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CancellationException;

public final class HostAudioHandoff {
    private final AudioStreamProvider playback;
    private final Runnable terminalCallback;
    private State state = State.WAITING;

    public HostAudioHandoff(AudioStreamProvider playback, Runnable terminalCallback) {
        this.playback = Objects.requireNonNull(playback, "playback");
        this.terminalCallback = Objects.requireNonNull(
                terminalCallback, "terminalCallback");
    }

    public synchronized CustomAudioStream offer(CustomAudioStream stream) {
        Objects.requireNonNull(stream, "stream");
        if (state == State.TERMINAL) {
            closeQuietly(stream);
            throw new CancellationException("Audio handoff is already terminal");
        }
        if (state != State.WAITING) {
            closeQuietly(stream);
            throw new IllegalStateException("Audio handoff received more than one stream");
        }
        state = State.OFFERED;
        return new OfferedStream(stream, this);
    }

    public void stop() {
        terminate();
    }

    public void failed() {
        terminate();
    }

    public void hostReleased() {
        terminate();
    }

    private synchronized boolean tryAdopt() {
        if (state != State.OFFERED) {
            return false;
        }
        state = State.ADOPTED;
        return true;
    }

    private void terminate() {
        synchronized (this) {
            if (state == State.TERMINAL) {
                return;
            }
            state = State.TERMINAL;
        }
        try {
            playback.stop();
        } finally {
            terminalCallback.run();
        }
    }

    private static void closeQuietly(CustomAudioStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private enum State {
        WAITING,
        OFFERED,
        ADOPTED,
        TERMINAL
    }

    private static final class OfferedStream
            implements CustomAudioStream, HostAwareAudioStream {
        private final CustomAudioStream delegate;
        private final HostAudioHandoff handoff;

        private OfferedStream(CustomAudioStream delegate, HostAudioHandoff handoff) {
            this.delegate = delegate;
            this.handoff = handoff;
        }

        @Override
        public boolean ysm$tryHostAdopt() {
            return handoff.tryAdopt();
        }

        @Override
        public @NotNull AudioFormat getFormat() {
            return delegate.getFormat();
        }

        @Override
        public @NotNull ByteBuffer read(int size) throws IOException {
            return delegate.read(size);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }
    }
}
