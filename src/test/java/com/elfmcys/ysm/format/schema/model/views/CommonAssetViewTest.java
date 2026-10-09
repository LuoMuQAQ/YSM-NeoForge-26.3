package com.elfmcys.ysm.format.schema.model.views;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.schema.file.AssetFileView;
import com.elfmcys.ysm.format.schema.file.ChunkDataSource;
import com.elfmcys.ysm.proto.mixel.common.Sound;
import com.elfmcys.ysm.proto.mixel.manifest.asset.Common;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonAssetViewTest {
    @Test
    void treatsMissingSoundsAsEmptyWithoutMutatingMessage() throws IOException {
        var common = Common.newBuilder()
                .setStringsBlobId(0).build();

        var view = new CommonAssetView(common, new RecordingAssetFileView(Map.of()));

        assertTrue(common.sounds().isEmpty());
        assertTrue(view.sounds().isEmpty());
    }

    @Test
    void indexesValidatedDescriptorsWithoutReadingContent() throws IOException {
        var common = Common.newBuilder()
                .setStringsBlobId(0)
                .addSounds(sound("first", "OGG_VORBIS", 2, 44_100, 17, 11))
                .addSounds(sound("second", "OGG_OPUS", 1, 48_000, 23, 22))
                .build();
        var view = new CommonAssetView(common, new RecordingAssetFileView(Map.of(
                11, chunk(11, 100, "", 0, 0),
                22, chunk(22, 200, "", 0, 0))));

        assertEquals(11, view.sounds().get("first").streamId());
        assertEquals(22, view.sounds().get("second").streamId());
        assertEquals(2, view.sounds().size());
    }

    @Test
    void rejectsDuplicateNamesAndConflictingSharedStreams() {
        var files = new RecordingAssetFileView(Map.of(
                1, chunk(1, 100, "", 0, 0)));
        var duplicateName = Common.newBuilder()
                .addSounds(sound("tone", "OGG_OPUS", 2, 48_000, 10, 1))
                .addSounds(sound("tone", "OGG_OPUS", 2, 48_000, 10, 1))
                .build();
        var conflictingStream = Common.newBuilder()
                .addSounds(sound("first", "OGG_OPUS", 2, 48_000, 10, 1))
                .addSounds(sound("second", "OGG_OPUS", 1, 48_000, 10, 1))
                .build();

        assertThrows(IOException.class, () -> new CommonAssetView(duplicateName, files));
        assertThrows(IOException.class, () -> new CommonAssetView(conflictingStream, files));
    }

    @Test
    void rejectsInvalidDescriptorAndStorageShapes() {
        var direct = new RecordingAssetFileView(Map.of(
                1, chunk(1, 100, "", 0, 0)));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("", "OGG_OPUS", 2, 48_000, 10, 1)), direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "opus", 2, 48_000, 10, 1)), direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_OPUS", 3, 48_000, 10, 1)), direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_VORBIS", 2, 0, 10, 1)), direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_OPUS", 2, 44_100, 10, 1)),
                direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_OPUS", 2, 48_000, Long.MIN_VALUE, 1)),
                direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_OPUS", 2, 48_000, 10, 0)),
                new RecordingAssetFileView(Map.of())));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_OPUS", 2, 48_000, 10, 2)),
                direct));
        assertThrows(IOException.class, () -> new CommonAssetView(
                common(sound("tone", "OGG_OPUS", 2, 48_000, 10, 1)),
                new RecordingAssetFileView(Map.of(
                        1, chunk(1, 100, "zstd", 200, 1)))));
    }

    @Test
    void validatesExactContentOnlyAtTheExplicitM2Boundary() throws IOException {
        byte[] encoded = Files.readAllBytes(fixtureRoot().resolve("opus-under.ogg"));
        var fileView = new RecordingAssetFileView(Map.of(
                1, chunk(1, encoded.length, "", 0, 0)));
        var view = new CommonAssetView(common(sound(
                "tone", "OGG_OPUS", 2, 48_000, 191_999, 1)), fileView);
        var reads = new AtomicInteger();
        var source = new ChunkDataSource() {
            @Override
            public UniBuffer readPayload(AssetContainerView.ChunkInfo chunk,
                                         BufferType bufferType) {
                reads.incrementAndGet();
                return ArrayBuffer.move(encoded.clone());
            }

            @Override
            public UniBuffer readStoredVerified(AssetContainerView.ChunkInfo chunk,
                                                BufferType bufferType) {
                throw new AssertionError("stored representation was not requested");
            }
        };

        assertEquals(0, reads.get());
        view.validateSoundContent(() -> false, source);
        assertEquals(1, reads.get());
    }

    private static Common common(
            Sound sound) {
        return Common.newBuilder()
                .addSounds(sound).build();
    }

    private static Sound sound(
            String name, String encoding, int channels, int sampleRate,
            long frames, int streamId) {
        return Sound.newBuilder()
                .setName(name)
                .setEncoding(encoding)
                .setChannels(channels)
                .setSampleRate(sampleRate)
                .setSamples(frames)
                .setStreamId(streamId)
                .build();
    }

    private static AssetContainerView.ChunkInfo chunk(
            int streamId, int size, String encoding, int decodeSize, int flags) {
        return new AssetContainerView.ChunkInfo("stream-" + streamId, encoding,
                0, size, decodeSize, flags, 0, 0, new byte[32]);
    }

    private static Path fixtureRoot() {
        var configured = System.getenv("YSM_AUDIO_FIXTURE_DIR");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "YSM_AUDIO_FIXTURE_DIR must identify the contract fixture directory");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static final class RecordingAssetFileView extends AssetFileView {
        private final Map<Integer, AssetContainerView.ChunkInfo> streams;

        private RecordingAssetFileView(Map<Integer, AssetContainerView.ChunkInfo> streams) {
            super(null);
            this.streams = streams;
        }

        @Override
        public AssetContainerView.ChunkInfo streamInfo(int id) {
            return streams.get(id);
        }
    }
}
