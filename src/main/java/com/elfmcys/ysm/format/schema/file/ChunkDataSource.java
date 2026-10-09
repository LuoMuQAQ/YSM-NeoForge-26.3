package com.elfmcys.ysm.format.schema.file;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.container.AssetContainerView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public interface ChunkDataSource {
    /** Returns the decoded logical payload, or the stored bytes for direct media encodings. */
    UniBuffer readPayload(AssetContainerView.ChunkInfo chunk,
                          BufferType bufferType) throws IOException;

    /** Returns the exact stored representation after validating its logical content. */
    UniBuffer readStoredVerified(AssetContainerView.ChunkInfo chunk,
                                 BufferType bufferType) throws IOException;

    default UniBuffer readPayload(BooleanSupplier cancelled,
                                  AssetContainerView.ChunkInfo chunk,
                                  BufferType bufferType) throws IOException {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Chunk read was cancelled");
        }
        var result = readPayload(chunk, bufferType);
        if (cancelled.getAsBoolean()) {
            result.close();
            throw new CancellationException("Chunk read was cancelled");
        }
        return result;
    }

    default byte[] readPayloadBytes(AssetContainerView.ChunkInfo chunk) throws IOException {
        try (var payload = readPayload(chunk, BufferType.ARRAY)) {
            return toByteArray(payload);
        }
    }

    default byte[] readStoredVerifiedBytes(AssetContainerView.ChunkInfo chunk) throws IOException {
        try (var stored = readStoredVerified(chunk, BufferType.ARRAY)) {
            return toByteArray(stored);
        }
    }

    default InputStream openPayloadStream(AssetContainerView.ChunkInfo chunk) throws IOException {
        return new ByteArrayInputStream(readPayloadBytes(chunk));
    }

    private static byte[] toByteArray(UniBuffer buffer) {
        var result = new byte[buffer.size()];
        buffer.nio().get(result);
        return result;
    }
}
