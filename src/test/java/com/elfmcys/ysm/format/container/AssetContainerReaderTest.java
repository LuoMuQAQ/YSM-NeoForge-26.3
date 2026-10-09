package com.elfmcys.ysm.format.container;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.lang3.SerializationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetContainerReaderTest {
    @Test
    void rejectsPreambleWithInvalidMagicBeforeParsingMetadata() {
        var invalidPreamble = new byte[AssetContainerConstant.HEAD.length + 1];

        assertThrows(IOException.class, () -> AssetContainerReader.readPreamble(invalidPreamble));
    }

    @Test
    void writerPublishesContainerIdFromTheVerificationPayload() throws Exception {
        var container = writeEmptyContainer();

        var view = AssetContainerReader.readPreamble(container);
        var verification = view.getChunkInfo(AssetContainerConstant.VERIFICATION_CHUNK_TYPE);

        assertEquals(AssetContainerConstant.VERIFICATION_PAYLOAD_HEADER_SIZE,
                verification.size());
        assertArrayEquals(view.getContainerId().bytes(), Arrays.copyOfRange(
                container, verification.offset(),
                verification.offset() + AssetContainerConstant.HASH_SIZE));
        assertEquals(0, container[verification.offset() + AssetContainerConstant.HASH_SIZE]);
        assertEquals(0, container[verification.offset() + AssetContainerConstant.HASH_SIZE + 1]);
    }

    @Test
    void rejectsLegacyVerificationPayloadWithoutSignatureLength() throws Exception {
        var current = writeEmptyContainer();
        var verificationRecord = findVerificationRecord(current);
        ByteBuffer.wrap(current).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(verificationRecord + 1 + "verification".length()
                        + 1 + AssetContainerConstant.HASH_NAME.length(),
                        AssetContainerConstant.HASH_SIZE);
        var legacy = Arrays.copyOf(current, current.length - Short.BYTES);

        assertThrows(SerializationException.class,
                () -> AssetContainerReader.readPreamble(legacy));
    }

    @Test
    void rejectsDifferentDevelopmentQualifierAtTheContainerBoundary() throws Exception {
        var container = writeEmptyContainer();
        var qualifierOffset = AssetContainerConstant.HEAD.length + 1 + 7;
        Arrays.fill(container, qualifierOffset,
                qualifierOffset + AssetContainerConstant.HEADER_QUALIFIER_VERSION_SIZE,
                (byte) 0);
        System.arraycopy("snapshot".getBytes(StandardCharsets.US_ASCII),
                0, container, qualifierOffset, "snapshot".length());

        assertThrows(UnsupportedEncodingException.class,
                () -> AssetContainerReader.readPreamble(container));
    }

    private static byte[] writeEmptyContainer() throws IOException {
        var output = new ByteArrayOutputStream();
        try (var writer = new AssetContainerWriter();
             var channel = Channels.newChannel(output)) {
            writer.setSchema("test");
            writer.write(channel);
        }
        return output.toByteArray();
    }

    private static int findVerificationRecord(byte[] container) {
        var marker = "\fverification\u0006BLAKE3".getBytes(
                StandardCharsets.US_ASCII);
        outer:
        for (var offset = 0; offset <= container.length - marker.length; offset++) {
            for (var index = 0; index < marker.length; index++) {
                if (container[offset + index] != marker[index]) {
                    continue outer;
                }
            }
            return offset;
        }
        throw new AssertionError("Verification record not found");
    }
}
