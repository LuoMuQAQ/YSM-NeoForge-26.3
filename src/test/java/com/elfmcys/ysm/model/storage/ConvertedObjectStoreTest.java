package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.parser.RawCompileResult;
import com.elfmcys.ysm.format.schema.model.ModelFileIdentityReader;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPath;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedObjectStoreTest {
    @TempDir
    static Path fixtureTemp;

    @TempDir
    Path temp;

    private static Path fixture;
    private static ModelFileIdentity identity;

    @BeforeAll
    static void createFixture() throws Exception {
        var manifest = ConvertedObjectStoreTest.class.getResource(
                "/assets/ysm/builtin/default/ysm.json");
        var source = Path.of(Objects.requireNonNull(manifest).toURI()).getParent();
        try (var vfs = new Directory(source)) {
            fixture = ModelParser.parseBuiltinDefault(
                    vfs, Files.createDirectories(fixtureTemp.resolve("model")));
        }
        try (var channel = FileChannel.open(fixture, StandardOpenOption.READ)) {
            identity = ModelFileIdentityReader.read(channel);
        }
    }

    @Test
    void convertedStoreKeepsItsDocumentedLayout() {
        var cacheRoot = temp.resolve("cache");
        var store = new ConvertedObjectStore(cacheRoot);

        assertEquals(cacheRoot.resolve("converted/.tmp"), store.temporaryRoot());
        assertEquals(cacheRoot.resolve("converted").resolve(identity.modelId().toString())
                        .resolve(identity.containerId() + ".mxc"),
                store.objectPath(identity.modelId(), identity.containerId()));
    }

    @Test
    void cacheProbeReadsOnlyExactIdentityPreamble() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var target = container(cacheRoot, identity.modelId(), identity.containerId());
        Files.createDirectories(target.getParent());
        var bytes = Files.readAllBytes(fixture);
        bytes[bytes.length - 1] ^= 1;
        Files.write(target, bytes);
        var entry = new ConvertedSourceIndex(identity,
                "custom/raw", "test-version");

        assertTrue(new ConvertedObjectStore(cacheRoot).findIdentity(entry).isPresent());
        assertTrue(Files.exists(target));
    }

    @Test
    void tupleMismatchIsAMissWithoutDeletingTheExactObject() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var expectedModel = different(identity.modelId());
        var expectedContainer = different(identity.containerId());
        var target = container(cacheRoot, expectedModel, expectedContainer);
        var sibling = target.resolveSibling("keep.mxc");
        Files.createDirectories(target.getParent());
        Files.copy(fixture, target);
        Files.writeString(sibling, "keep");
        var entry = new ConvertedSourceIndex(
                new ModelFileIdentity(expectedModel, expectedContainer),
                "custom/raw", "test-version");
        var original = Files.readAllBytes(target);

        assertTrue(new ConvertedObjectStore(cacheRoot).findIdentity(entry).isEmpty());
        assertArrayEquals(original, Files.readAllBytes(target));
        assertTrue(Files.exists(sibling));
    }

    @Test
    void atomicCommitFailureKeepsThePreviousObjectAndCleansItsTemporary() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var target = container(cacheRoot, identity.modelId(), identity.containerId());
        Files.createDirectories(target.getParent());
        var previous = new byte[]{9, 8, 7};
        Files.write(target, previous);
        var cache = new AtomicSharedCache(cacheRoot, (temporary, ignored) -> {
            throw new IOException("injected atomic move failure");
        });
        var store = new ConvertedObjectStore(cacheRoot, cache);

        assertThrows(IOException.class,
                () -> store.commit(new RawCompileResult(identity.modelId(), fixture),
                        new CatalogModelLocation(
                                CatalogRootKind.CUSTOM,
                                new ModelPath("fixture"))));

        assertArrayEquals(previous, Files.readAllBytes(target));
        try (var files = Files.list(target.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString()
                    .startsWith(target.getFileName() + ".tmp-")));
        }
    }

    @Test
    void concurrentQualifiedWritersLeaveACompletelyVerifiedObject() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var compiled = new RawCompileResult(identity.modelId(), fixture);
        var location = new CatalogModelLocation(
                CatalogRootKind.CUSTOM,
                new ModelPath("fixture"));
        var first = CompletableFuture.runAsync(() ->
                commit(new ConvertedObjectStore(cacheRoot), compiled, location));
        var second = CompletableFuture.runAsync(() ->
                commit(new ConvertedObjectStore(cacheRoot), compiled, location));

        CompletableFuture.allOf(first, second).join();

        var target = container(cacheRoot, identity.modelId(), identity.containerId());
        try (var verified = ManagedContainer.verifyFile(target)) {
            assertEquals(identity, verified.identity());
        }
    }

    @Test
    void missingStagedBackingDoesNotPublishAConvertedObject() {
        var cacheRoot = temp.resolve("cache");
        var target = container(cacheRoot, identity.modelId(), identity.containerId());

        assertThrows(IOException.class,
                () -> new ConvertedObjectStore(cacheRoot).commit(
                        new RawCompileResult(identity.modelId(), temp.resolve("missing.ysm")),
                        new CatalogModelLocation(
                                CatalogRootKind.CUSTOM,
                                new ModelPath("missing"))));
        assertTrue(Files.notExists(target));
    }

    private static Path container(Path cacheRoot, Hash256 modelId, Hash256 containerId) {
        return new ConvertedObjectStore(cacheRoot).objectPath(modelId, containerId);
    }

    private static Hash256 different(Hash256 value) {
        var bytes = value.bytes();
        bytes[0] ^= 1;
        return new Hash256(bytes);
    }

    private static void commit(ConvertedObjectStore store, RawCompileResult compiled,
                               CatalogModelLocation location) {
        try {
            store.commit(compiled, location);
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
    }
}
