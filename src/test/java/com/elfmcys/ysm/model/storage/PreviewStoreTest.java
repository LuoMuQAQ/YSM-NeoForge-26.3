package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.NativeBuffer;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.natives.image.ImageSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PreviewStoreTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path temp;

    @Test
    void invalidReadsAreNonDestructiveAndValidReplacementUsesOnlyContainerId()
            throws Exception {
        var cacheRoot = temp.resolve("cache");
        var store = new PreviewStore(cacheRoot);
        var id = hash(1);
        Files.createDirectories(cacheRoot.resolve("previews"));
        var invalid = new byte[]{1, 2, 3};
        Files.write(preview(cacheRoot, id), invalid);

        assertTrue(store.load(id).isEmpty());
        assertArrayEquals(invalid, Files.readAllBytes(preview(cacheRoot, id)));

        var accepted = store.accept(id, PNG);
        assertArrayEquals(PNG, accepted.bytes());
        assertArrayEquals(PNG, store.load(id).orElseThrow().bytes());

        var replacement = Arrays.copyOf(PNG, PNG.length / 2);
        assertThrows(Exception.class, () -> store.accept(id, replacement));
        assertArrayEquals(PNG, Files.readAllBytes(preview(cacheRoot, id)));
    }

    @Test
    void differentContainerIdsNeverShareOneCacheEntry() throws Exception {
        var cacheRoot = temp.resolve("separate");
        var store = new PreviewStore(cacheRoot);
        store.accept(hash(1), PNG);

        assertTrue(store.load(hash(2)).isEmpty());
    }

    @Test
    void persistenceFailureDoesNotRevokeTheValidatedInMemoryImage() throws Exception {
        var cacheRoot = temp.resolve("failed-write");
        var cache = new AtomicSharedCache(cacheRoot, (temporary, target) -> {
            throw new IOException("injected commit failure");
        });

        var accepted = new PreviewStore(cacheRoot, cache).accept(hash(3), PNG);

        assertArrayEquals(PNG, accepted.bytes());
        try (var image = accepted.open(); var pixels = image.decodeToBuffer()) {
            assertEquals(4, pixels.size());
        }
        assertFalse(Files.exists(preview(cacheRoot, hash(3))));
    }

    @Test
    void decodingPreservesAnExistingNativeBufferWithoutAnArrayRoundTrip() throws Exception {
        try (var source = NativeBuffer.allocate(PNG.length)) {
            source.nio().put(PNG);

            var preview = PreviewStore.decode(source);
            source.close();

            try (var image = preview.open()) {
                assertEquals(BufferType.NATIVE, image.data().type());
                assertArrayEquals(PNG, preview.bytes());
            }
        }
    }

    @Test
    void placeholderIsAUsableBlankImageButRemainsExplicitlyNamedAsASubstitute()
            throws Exception {
        var preview = TestPreviews.blank();

        assertEquals(1, preview.width());
        assertEquals(1, preview.height());
        try (var image = preview.open(); var pixels = image.decodeToBuffer()) {
            assertEquals(4, pixels.size());
        }
    }

    @Test
    void rejectsAnOversizedSourceBeforeOpeningIt() {
        var opened = new AtomicBoolean();
        ImageSource source = () -> {
            opened.set(true);
            throw new AssertionError("oversized source must not be opened");
        };

        assertThrows(IOException.class,
                () -> PreviewStore.read(source, PreviewStore.MAX_STORED_BYTES + 1));
        assertFalse(opened.get());
    }

    @Test
    void explicitAcceptanceAtomicallyReplacesAnOversizedInvalidEntry() throws Exception {
        var cacheRoot = temp.resolve("oversized-replacement");
        var id = hash(4);
        Files.createDirectories(cacheRoot.resolve("previews"));
        try (var file = FileChannel.open(preview(cacheRoot, id),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            file.position(PreviewStore.MAX_STORED_BYTES);
            file.write(ByteBuffer.wrap(new byte[]{1}));
        }

        new PreviewStore(cacheRoot).accept(id, PNG);

        assertArrayEquals(PNG, Files.readAllBytes(preview(cacheRoot, id)));
    }

    private static Path preview(Path cacheRoot, Hash256 containerId) {
        return cacheRoot.resolve("previews").resolve(containerId + ".image");
    }

    private static Hash256 hash(int value) {
        var bytes = new byte[Hash256.SIZE];
        Arrays.fill(bytes, (byte) value);
        return new Hash256(bytes);
    }
}
