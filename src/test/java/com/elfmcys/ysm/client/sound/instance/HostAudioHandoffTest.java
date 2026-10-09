package com.elfmcys.ysm.client.sound.instance;

import com.elfmcys.ysm.client.sound.stream.AudioStreamProvider;
import com.elfmcys.ysm.client.sound.stream.CustomAudioStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostAudioHandoffTest {
    @Test
    void releaseBeforeSkippedCallbackClosesTheOfferedPlayback() {
        var provider = new FakeProvider();
        var terminals = new AtomicInteger();
        var handoff = new HostAudioHandoff(provider, terminals::incrementAndGet);
        var offered = handoff.offer(new FakeStream());

        handoff.hostReleased();
        handoff.hostReleased();

        assertEquals(1, provider.stops.get());
        assertEquals(1, terminals.get());
        assertFalse(((HostAwareAudioStream) offered).ysm$tryHostAdopt());
    }

    @Test
    void adoptionAndReleaseHaveOneTerminalOwner() {
        var provider = new FakeProvider();
        var terminals = new AtomicInteger();
        var handoff = new HostAudioHandoff(provider, terminals::incrementAndGet);
        var offered = handoff.offer(new FakeStream());

        assertTrue(((HostAwareAudioStream) offered).ysm$tryHostAdopt());
        handoff.hostReleased();

        assertEquals(1, provider.stops.get());
        assertEquals(1, terminals.get());
    }

    @Test
    void stopBeforeLateOfferRejectsAndClosesTheStream() {
        var provider = new FakeProvider();
        var handoff = new HostAudioHandoff(provider, () -> { });
        var stream = new FakeStream();

        handoff.stop();

        assertThrows(CancellationException.class,
                () -> handoff.offer(stream));
        assertTrue(stream.closed);
        assertEquals(1, provider.stops.get());
    }

    private static final class FakeProvider implements AudioStreamProvider {
        private final AtomicInteger stops = new AtomicInteger();

        @Override
        public @NotNull CompletableFuture<CustomAudioStream> openStream(boolean looping) {
            return new CompletableFuture<>();
        }

        @Override
        public void stop() {
            stops.incrementAndGet();
        }

        @Override
        public CompletionStage<Void> stopped() {
            return new CompletableFuture<>();
        }
    }

    private static final class FakeStream implements CustomAudioStream {
        private boolean closed;

        @Override
        public @NotNull AudioFormat getFormat() {
            return new AudioFormat(48_000, 16, 1, true, false);
        }

        @Override
        public @NotNull ByteBuffer read(int size) {
            return ByteBuffer.allocate(0);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
