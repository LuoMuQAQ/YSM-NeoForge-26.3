// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.sound.stream;

import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.media.SupportedAudioProbe;
import net.minecraft.client.sounds.JOrbisAudioStream;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class VorbisAudioStream implements CustomAudioStream {
    private static final ByteBuffer EMPTY_BUFFER = BufferUtils.createByteBuffer(0);

    private final JOrbisAudioStream decoder;
    private final AudioFormat audioFormat;
    private final int channels;
    private final long expectedFrames;
    private long decodedFrames;
    private volatile boolean closed;
    private boolean eof;

    public VorbisAudioStream(ByteBuffer data) throws IOException, UnsupportedAudioFileException {
        this(data, inspect(data));
    }

    public VorbisAudioStream(ByteBuffer data, SupportedAudioProbe.MediaInfo media)
            throws IOException, UnsupportedAudioFileException {
        if (media.encoding() != SupportedAudioProbe.Encoding.OGG_VORBIS) {
            throw new UnsupportedAudioFileException("media is not Ogg Vorbis");
        }
        float representedRate = (float) media.sampleRate();
        if ((long) representedRate != media.sampleRate()) {
            throw new UnsupportedAudioFileException("Vorbis sample rate is not exactly representable by the host");
        }

        final JOrbisAudioStream opened;
        var input = new ByteBufInputStream(Unpooled.wrappedBuffer(data.duplicate()), true);
        try {
            opened = new JOrbisAudioStream(input);
        } catch (IOException failure) {
            try {
                input.close();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw AssetLoadException.content("Invalid Ogg Vorbis stream", failure);
        }
        var format = opened.getFormat();
        if (format.getChannels() != media.channels()
                || format.getSampleRate() != representedRate) {
            var failure = AssetLoadException.content(
                    "Vorbis decoder metadata disagrees with inspection");
            try {
                opened.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        decoder = opened;
        channels = media.channels();
        expectedFrames = media.frames();
        audioFormat = new AudioFormat(representedRate, 16, 1, true, false);
    }

    @Override
    public @NotNull AudioFormat getFormat() {
        return audioFormat;
    }

    @Override
    public @NotNull ByteBuffer read(int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (size == 0 || eof || closed) {
            return EMPTY_BUFFER;
        }

        int requested = size == 1 ? 2 : size - size % 2;
        int sourceBytes;
        try {
            sourceBytes = Math.multiplyExact(requested, channels);
        } catch (ArithmeticException exception) {
            throw new IOException("Vorbis read size is too large", exception);
        }
        final ByteBuffer decoded;
        try {
            decoded = decoder.read(sourceBytes);
        } catch (IOException failure) {
            throw AssetLoadException.content("Invalid Ogg Vorbis stream", failure);
        }
        if (!decoded.hasRemaining()) {
            if (decodedFrames != expectedFrames) {
                eof = true;
                throw AssetLoadException.content(
                        "Vorbis frame count does not match the inspected timeline");
            }
            eof = true;
            return EMPTY_BUFFER;
        }

        int frameBytes = channels * Short.BYTES;
        if (decoded.remaining() % frameBytes != 0) {
            eof = true;
            throw AssetLoadException.content("Vorbis decoder returned a partial frame");
        }
        long frames = decoded.remaining() / frameBytes;
        if (frames > expectedFrames - decodedFrames) {
            eof = true;
            throw AssetLoadException.content(
                    "Vorbis decoder exceeded the inspected frame count");
        }
        decodedFrames += frames;
        if (channels == 1) {
            return decoded;
        }

        var source = decoded.duplicate().order(ByteOrder.nativeOrder());
        ByteBuffer destination;
        if (!decoded.isReadOnly()) {
            destination = decoded.duplicate().order(ByteOrder.nativeOrder());
            destination.limit(source.remaining() / 2);
        } else {
            destination = BufferUtils.createByteBuffer(source.remaining() / 2)
                    .order(ByteOrder.nativeOrder());
        }
        var result = destination.slice();
        while (source.hasRemaining()) {
            int left = source.getShort();
            int right = source.getShort();
            destination.putShort((short) Math.rint((left + right) / 2.0));
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            decoder.close();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    private static SupportedAudioProbe.MediaInfo inspect(ByteBuffer data)
            throws UnsupportedAudioFileException {
        var inspection = SupportedAudioProbe.inspect(data);
        if (!inspection.playable()
                || inspection.media().encoding() != SupportedAudioProbe.Encoding.OGG_VORBIS) {
            throw new UnsupportedAudioFileException(inspection.diagnostic());
        }
        return inspection.media();
    }
}
