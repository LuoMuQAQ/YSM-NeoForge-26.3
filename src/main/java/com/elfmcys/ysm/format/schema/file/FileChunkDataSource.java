package com.elfmcys.ysm.format.schema.file;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.container.InlineChunkReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Opens a new channel for every operation, making one ModelFileView safe for concurrent requests. */
public final class FileChunkDataSource implements ChunkDataSource {
    private final Path file;
    private final long expectedFileSize;

    public FileChunkDataSource(Path file) {
        this(file, -1);
    }

    public FileChunkDataSource(Path file, long expectedFileSize) {
        if (expectedFileSize < -1) {
            throw new IllegalArgumentException("Expected file size must not be less than -1");
        }
        this.file = file.toAbsolutePath().normalize();
        this.expectedFileSize = expectedFileSize;
    }

    @Override
    public UniBuffer readPayload(AssetContainerView.ChunkInfo chunk,
                                 BufferType bufferType) throws IOException {
        return read(chunk, bufferType, InlineChunkReader::readPayload);
    }

    @Override
    public UniBuffer readStoredVerified(AssetContainerView.ChunkInfo chunk,
                                        BufferType bufferType) throws IOException {
        return read(chunk, bufferType, InlineChunkReader::readStoredVerified);
    }

    private UniBuffer read(AssetContainerView.ChunkInfo chunk, BufferType bufferType,
                           ChannelRead operation) throws IOException {
        UniBuffer result = null;
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            verifyExpectedSize(channel);
            result = operation.read(channel, chunk, bufferType);
            verifyExpectedSize(channel);
        } catch (AssetLoadException error) {
            close(result);
            throw error;
        } catch (IOException | SecurityException error) {
            close(result);
            throw readFailure(chunk, error);
        } catch (RuntimeException | Error error) {
            close(result);
            throw error;
        }
        return result;
    }

    private void verifyExpectedSize(SeekableByteChannel channel) throws IOException {
        if (expectedFileSize >= 0 && channel.size() != expectedFileSize) {
            throw AssetLoadException.content(
                    "Model file length changed after source admission: " + file);
        }
    }

    private AssetLoadException readFailure(AssetContainerView.ChunkInfo chunk, Throwable cause) {
        var message = "Failed to read model chunk file=" + file
                + " type=" + chunk.type()
                + " encoding=" + chunk.encoding()
                + " encodedSize=" + chunk.size()
                + " decodedSize=" + chunk.decodeSize();
        return AssetLoadException.access(message, cause);
    }

    private static void close(UniBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }

    @FunctionalInterface
    private interface ChannelRead {
        UniBuffer read(SeekableByteChannel channel, AssetContainerView.ChunkInfo chunk,
                       BufferType bufferType) throws IOException;
    }
}
