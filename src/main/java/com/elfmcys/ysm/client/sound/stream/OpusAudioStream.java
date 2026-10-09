package com.elfmcys.ysm.client.sound.stream;

import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.media.SupportedAudioProbe;
import com.elfmcys.ysm.natives.sound.OpusDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class OpusAudioStream implements CustomAudioStream {
    private static final int INPUT_CHUNK_BYTES = 32 * 1024;
    private static final ByteBuffer EMPTY_BUFFER = BufferUtils.createByteBuffer(0);
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(48_000, 16, 1, true, false);

    private final OpusDecoder decoder;
    private final ByteBuffer encoded;
    private final ByteBuf output;
    private boolean inputEnded;
    private volatile boolean closed;
    private boolean eof;

    public OpusAudioStream(ByteBuffer data) throws UnsupportedAudioFileException {
        this(data, inspect(data));
    }

    public OpusAudioStream(ByteBuffer data, SupportedAudioProbe.MediaInfo media)
            throws UnsupportedAudioFileException {
        if (media.encoding() != SupportedAudioProbe.Encoding.OGG_OPUS) {
            throw new UnsupportedAudioFileException("media is not Ogg Opus");
        }
        encoded = data.duplicate();
        if (!encoded.isDirect()) {
            throw new IllegalArgumentException("Opus input must be a direct buffer");
        }
        decoder = new OpusDecoder(media.frames());
        output = PooledByteBufAllocator.DEFAULT.directBuffer(8 * 1024);
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
        if (output.capacity() < requested) {
            output.capacity(requested);
        }
        var destination = output.nioBuffer(0, requested);
        while (true) {
            int length = decoder.decode(destination.duplicate());
            if (length > 0) {
                return destination.slice(0, length);
            }
            if (length == 0) {
                eof = true;
                return EMPTY_BUFFER;
            }
            if (length != OpusDecoder.NEED_INPUT) {
                eof = true;
                throw AssetLoadException.content("Invalid Ogg Opus stream");
            }
            if (encoded.hasRemaining()) {
                int chunkSize = Math.min(encoded.remaining(), INPUT_CHUNK_BYTES);
                var chunk = encoded.slice(encoded.position(), chunkSize);
                try {
                    decoder.feed(chunk);
                } catch (IllegalArgumentException failure) {
                    throw AssetLoadException.content(
                            "Native Opus decoder rejected the encoded stream", failure);
                }
                encoded.position(encoded.position() + chunkSize);
            } else if (!inputEnded) {
                decoder.endInput();
                inputEnded = true;
            } else {
                eof = true;
                throw AssetLoadException.content(
                        "Opus decoder made no progress after end of input");
            }
        }
    }

    @Override
    public @NotNull AudioFormat getFormat() {
        return AUDIO_FORMAT;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            decoder.close();
            output.release();
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
                || inspection.media().encoding() != SupportedAudioProbe.Encoding.OGG_OPUS) {
            throw new UnsupportedAudioFileException(inspection.diagnostic());
        }
        return inspection.media();
    }
}
