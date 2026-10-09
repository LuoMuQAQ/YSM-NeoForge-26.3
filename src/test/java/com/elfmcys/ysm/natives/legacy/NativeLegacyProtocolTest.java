package com.elfmcys.ysm.natives.legacy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLegacyProtocolTest {
    @Test
    void validatesDescriptorAndReleasesResultExactlyOnce() {
        var releasedHandle = new AtomicLong();
        var releaseCount = new AtomicInteger();
        var payloads = minimalPayloads();
        var raw = new NativeLegacyImportResult(0, "", 41,
                descriptor(payloads, 17, 1234), payloads);

        var response = NativeLegacyProtocol.accept(raw, handle -> {
            releasedHandle.set(handle);
            releaseCount.incrementAndGet();
        });
        var success = assertInstanceOf(NativeLegacyProtocol.Success.class, response);
        assertEquals(17, success.descriptor().innerVersion());
        assertEquals(1234, success.descriptor().sourceSize());
        assertArrayEquals(new byte[32], success.descriptor().modelId());
        assertEquals(3, success.descriptor().records().size());
        assertTrue(success.payloads().get(0).nio().isReadOnly());

        success.close();
        success.close();
        assertEquals(1, releaseCount.get());
        assertEquals(41, releasedHandle.get());
        assertThrows(IllegalStateException.class, success::payloads);
    }

    @Test
    void retainedWriterBufferOutlivesThePublicResultGate() {
        var releaseCount = new AtomicInteger();
        var payloads = minimalPayloads();
        var raw = new NativeLegacyImportResult(0, "", 9,
                descriptor(payloads, 1, 64), payloads);
        var success = assertInstanceOf(NativeLegacyProtocol.Success.class,
                NativeLegacyProtocol.accept(raw,
                        ignored -> releaseCount.incrementAndGet()));
        var publicView = success.payloads().get(2);
        var writerOwned = publicView.acquire();

        success.close();
        assertEquals(0, releaseCount.get());
        assertThrows(IllegalStateException.class, publicView::nio);
        assertEquals("model", StandardCharsets.UTF_8.decode(writerOwned.nio()).toString());

        writerOwned.close();
        writerOwned.close();
        assertEquals(1, releaseCount.get());
        assertThrows(IllegalStateException.class, writerOwned::nio);
    }

    @Test
    void closingBorrowedViewDoesNotCloseItsParent() {
        var releaseCount = new AtomicInteger();
        var payloads = minimalPayloads();
        var raw = new NativeLegacyImportResult(0, "", 10,
                descriptor(payloads, 1, 64), payloads);
        var success = assertInstanceOf(NativeLegacyProtocol.Success.class,
                NativeLegacyProtocol.accept(raw,
                        ignored -> releaseCount.incrementAndGet()));
        var publicView = success.payloads().get(2);
        var borrowed = publicView.borrow();

        borrowed.close();
        assertThrows(IllegalStateException.class, borrowed::nio);
        assertEquals("model", StandardCharsets.UTF_8.decode(publicView.nio()).toString());
        assertEquals(0, releaseCount.get());

        success.close();
        assertEquals(1, releaseCount.get());
    }

    @Test
    void rejectsNonDirectPayloadAndClosesTheNativeOwner() {
        var payloads = minimalPayloads();
        payloads[2] = ByteBuffer.wrap(new byte[5]).asReadOnlyBuffer();
        assertInvalidBuffer(payloads);
    }

    @Test
    void rejectsWritablePayloadAndClosesTheNativeOwner() {
        var payloads = minimalPayloads();
        payloads[2] = ByteBuffer.allocateDirect(5);
        assertInvalidBuffer(payloads);
    }

    @Test
    void rejectsPayloadWithNonzeroPositionAndClosesTheNativeOwner() {
        var payloads = minimalPayloads();
        var positioned = ByteBuffer.allocateDirect(6).asReadOnlyBuffer();
        positioned.position(1);
        payloads[2] = positioned;
        assertInvalidBuffer(payloads);
    }

    private static void assertInvalidBuffer(ByteBuffer[] payloads) {
        var releaseCount = new AtomicInteger();
        var raw = new NativeLegacyImportResult(0, "", 73,
                descriptor(payloads, 1, 64), payloads);

        var error = assertThrows(NativeLegacyProtocol.ProtocolException.class,
                () -> NativeLegacyProtocol.accept(raw,
                        ignored -> releaseCount.incrementAndGet()));
        assertEquals(NativeLegacyStatus.RESULT_PROTOCOL, error.status());
        assertEquals(1, releaseCount.get());
    }

    @Test
    void rejectsAliasedPayloadBackingReservedAndUnknownStatus() {
        var releaseCount = new AtomicInteger();
        var shared = ByteBuffer.allocateDirect(5);
        var payloads = new ByteBuffer[]{
                shared.asReadOnlyBuffer(),
                shared.asReadOnlyBuffer(),
                shared.asReadOnlyBuffer(),
        };
        var aliased = new NativeLegacyImportResult(0, "", 5,
                descriptor(payloads, 1, 64), payloads);
        assertThrows(NativeLegacyProtocol.ProtocolException.class,
                () -> NativeLegacyProtocol.accept(aliased,
                        ignored -> releaseCount.incrementAndGet()));

        var reserved = new NativeLegacyImportResult(7, "", 6,
                new byte[0], new ByteBuffer[0]);
        var reservedError = assertThrows(NativeLegacyProtocol.ProtocolException.class,
                () -> NativeLegacyProtocol.accept(reserved,
                        ignored -> releaseCount.incrementAndGet()));
        assertTrue(reservedError.getMessage().contains("7"));

        var unknown = new NativeLegacyImportResult(99, "", 7,
                new byte[0], new ByteBuffer[0]);
        var unknownError = assertThrows(NativeLegacyProtocol.ProtocolException.class,
                () -> NativeLegacyProtocol.accept(unknown,
                        ignored -> releaseCount.incrementAndGet()));
        assertTrue(unknownError.getMessage().contains("99"));
        assertEquals(3, releaseCount.get());
    }

    @Test
    void acceptsClosedFailureShapeWithoutOwnership() {
        var response = NativeLegacyProtocol.accept(
                new NativeLegacyImportResult(3, "invalid", 0,
                        new byte[0], new ByteBuffer[0]),
                ignored -> {
                    throw new AssertionError("failure result has no owner");
                });
        var failure = assertInstanceOf(NativeLegacyProtocol.Failure.class, response);
        assertEquals(NativeLegacyStatus.INVALID_CONTENT, failure.status());
        assertEquals("invalid", failure.diagnostic());
    }

    @Test
    void acceptsTargetRepresentationFailureAndClosesDescriptorLayout() {
        var failure = assertInstanceOf(NativeLegacyProtocol.Failure.class,
                NativeLegacyProtocol.accept(new NativeLegacyImportResult(
                        11, "not representable", 0,
                        new byte[0], new ByteBuffer[0]), ignored -> {
                            throw new AssertionError("failure has no owner");
                        }));
        assertEquals(NativeLegacyStatus.TARGET_REPRESENTATION, failure.status());

        var payloads = minimalPayloads();
        var exact = descriptor(payloads, 1, 64);
        var old = oldVersionedDescriptor(payloads, 1, 64);
        for (var invalid : new byte[][]{
                Arrays.copyOf(exact, exact.length - 1),
                Arrays.copyOf(exact, exact.length + 1), old}) {
            var releases = new AtomicInteger();
            assertThrows(NativeLegacyProtocol.ProtocolException.class,
                    () -> NativeLegacyProtocol.accept(new NativeLegacyImportResult(
                                    0, "", 92, invalid, payloads),
                            ignored -> releases.incrementAndGet()));
            assertEquals(1, releases.get());
        }
    }

    @Test
    void rejectsNonzeroLegacyModelIdTailAndClosesTheNativeOwner() {
        var payloads = minimalPayloads();
        var invalid = descriptor(payloads, 1, 64);
        invalid[28 + 16] = 1;
        var releases = new AtomicInteger();

        var failure = assertThrows(NativeLegacyProtocol.ProtocolException.class,
                () -> NativeLegacyProtocol.accept(new NativeLegacyImportResult(
                                0, "", 93, invalid, payloads),
                        ignored -> releases.incrementAndGet()));

        assertEquals(NativeLegacyStatus.RESULT_PROTOCOL, failure.status());
        assertEquals(1, releases.get());
    }

    @Test
    void rejectsNonzeroDescriptorReservedFieldAndClosesTheNativeOwner() {
        var payloads = minimalPayloads();
        var invalid = descriptor(payloads, 1, 64);
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(16, 1);
        var releases = new AtomicInteger();

        var failure = assertThrows(NativeLegacyProtocol.ProtocolException.class,
                () -> NativeLegacyProtocol.accept(new NativeLegacyImportResult(
                                0, "", 94, invalid, payloads),
                        ignored -> releases.incrementAndGet()));

        assertEquals(NativeLegacyStatus.RESULT_PROTOCOL, failure.status());
        assertEquals(1, releases.get());
    }

    @Test
    void requiresSequentialSoundIdsAndUnsignedUtf8NameOrder() {
        var payloads = payloadsWithSounds();
        var releases = new AtomicInteger();
        var accepted = assertInstanceOf(NativeLegacyProtocol.Success.class,
                NativeLegacyProtocol.accept(new NativeLegacyImportResult(
                                0, "", 95,
                                descriptorWithSounds(payloads, 1, "a", 2, "b"),
                                payloads),
                        ignored -> releases.incrementAndGet()));
        accepted.close();

        for (var invalid : new byte[][]{
                descriptorWithSounds(payloads, 2, "a", 3, "b"),
                descriptorWithSounds(payloads, 1, "b", 2, "a")}) {
            assertThrows(NativeLegacyProtocol.ProtocolException.class,
                    () -> NativeLegacyProtocol.accept(new NativeLegacyImportResult(
                                    0, "", 96, invalid, payloads),
                            ignored -> releases.incrementAndGet()));
        }
        assertEquals(3, releases.get());
    }

    @Test
    void recognizesZtxButNeverRgbaAsACompressedImageEncoding() {
        assertEquals(NativeLegacyProtocol.PayloadEncoding.ZTX,
                NativeLegacyProtocol.PayloadEncoding.fromCode(8));
        assertTrue(NativeLegacyProtocol.PayloadEncoding.ZTX.isImage());
        assertFalse(NativeLegacyProtocol.PayloadEncoding.RGBA.isImage());
    }

    @Test
    void explicitCloseAndLateCleanerShareOneReleaseGate() {
        var releases = new AtomicInteger();
        var ownership = new NativeLegacyOwnership(12,
                ignored -> releases.incrementAndGet());
        var lease = ownership.acquire();

        ownership.close();
        ownership.cleanForTesting();
        assertFalse(ownership.isOpen());
        assertEquals(0, releases.get());

        lease.close();
        lease.close();
        assertEquals(1, releases.get());
    }

    private static ByteBuffer[] minimalPayloads() {
        return new ByteBuffer[]{
                directReadOnly("manifest"),
                ByteBuffer.allocateDirect(0).asReadOnlyBuffer(),
                directReadOnly("model"),
        };
    }

    private static ByteBuffer[] payloadsWithSounds() {
        return new ByteBuffer[]{
                directReadOnly("manifest"),
                ByteBuffer.allocateDirect(0).asReadOnlyBuffer(),
                directReadOnly("model"),
                directReadOnly("vorbis"),
                directReadOnly("opus"),
        };
    }

    private static ByteBuffer directReadOnly(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        var buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        return buffer.asReadOnlyBuffer();
    }

    private static byte[] descriptor(ByteBuffer[] payloads, int innerVersion,
                                     long sourceSize) {
        byte[] player = "player".getBytes(StandardCharsets.UTF_8);
        int size = 60 + 36 * 3 + player.length;
        var output = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        output.put(new byte[]{'Y', 'S', 'M', 'L', 'E', 'G', 'I', 0});
        output.putInt(innerVersion);
        output.putInt(3);
        output.putInt(0);
        output.putLong(sourceSize);
        output.put(new byte[32]);
        putRecord(output, 1, 0, 0, "", payloads[0].capacity());
        putRecord(output, 2, 0, 1, "", payloads[1].capacity());
        putRecord(output, 3, 0, 2, "player", payloads[2].capacity());
        return output.array();
    }

    private static byte[] oldVersionedDescriptor(
            ByteBuffer[] payloads, int innerVersion, long sourceSize) {
        byte[] player = "player".getBytes(StandardCharsets.UTF_8);
        int size = 64 + 36 * 3 + player.length;
        var output = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        output.put(new byte[]{'Y', 'S', 'M', 'L', 'E', 'G', 'I', 0});
        output.putShort((short) 1).putShort((short) 0);
        output.putInt(innerVersion).putInt(3).putInt(0).putLong(sourceSize);
        output.put(new byte[32]);
        putRecord(output, 1, 0, 0, "", payloads[0].capacity());
        putRecord(output, 2, 0, 1, "", payloads[1].capacity());
        putRecord(output, 3, 0, 2, "player", payloads[2].capacity());
        return output.array();
    }

    private static byte[] descriptorWithSounds(
            ByteBuffer[] payloads, int firstId, String firstName,
            int secondId, String secondName) {
        byte[] player = "player".getBytes(StandardCharsets.UTF_8);
        int size = 60 + 36 * payloads.length + player.length
                + firstName.getBytes(StandardCharsets.UTF_8).length
                + secondName.getBytes(StandardCharsets.UTF_8).length;
        var output = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        output.put(new byte[]{'Y', 'S', 'M', 'L', 'E', 'G', 'I', 0});
        output.putInt(1);
        output.putInt(payloads.length);
        output.putInt(0);
        output.putLong(64);
        output.put(new byte[32]);
        putRecord(output, 1, 0, 0, "", payloads[0].capacity());
        putRecord(output, 2, 0, 1, "", payloads[1].capacity());
        putRecord(output, 3, 0, 2, "player", payloads[2].capacity());
        putRecord(output, 6, 6, firstId, firstName, payloads[3].capacity());
        putRecord(output, 6, 7, secondId, secondName, payloads[4].capacity());
        return output.array();
    }

    private static void putRecord(ByteBuffer output, int kind, int encoding,
                                  int logicalId, String name, int payloadSize) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        output.putShort((short) kind);
        output.putShort((short) encoding);
        output.putInt(0);
        output.putInt(logicalId);
        output.putInt(nameBytes.length);
        output.putInt(0);
        output.putInt(0);
        output.putInt(0);
        output.putLong(payloadSize);
        output.put(nameBytes);
    }
}
