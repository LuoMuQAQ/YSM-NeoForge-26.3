package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.schema.file.FileChunkDataSource;
import com.elfmcys.ysm.format.schema.file.ResidentChunkDataSource;
import com.elfmcys.ysm.format.schema.model.ModelFileConstant;
import com.elfmcys.ysm.format.schema.model.ModelFileIdentityReader;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogIndexEntry;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.domain.ModelRepresentation;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedContainerResidencyTest {
    @TempDir
    static Path fixtureTemp;

    @TempDir
    Path temp;

    private static Path fixture;

    @BeforeAll
    static void createFixture() throws Exception {
        var manifest = ManagedContainerResidencyTest.class.getResource(
                "/assets/ysm/builtin/default/ysm.json");
        var source = Path.of(Objects.requireNonNull(manifest).toURI()).getParent();
        try (var vfs = new Directory(source)) {
            fixture = ModelParser.parseBuiltinDefault(
                    vfs, Files.createDirectories(fixtureTemp.resolve("model")));
        }
    }

    @Test
    void defaultContainerOwnsAllChunkBytesWithoutAFileBacking()
            throws Exception {
        var handle = ManagedContainer.openResidentDefault(
                Files.readAllBytes(fixture), location("default"));
        var capturedChunks = handle.chunks();
        assertInstanceOf(ResidentChunkDataSource.class, capturedChunks);
        assertTrue(handle.residentOnly());
        assertThrows(IllegalStateException.class, handle::file);

        var target = handle.view().getRenderTargets().stream()
                .filter(value -> !value.getTextureNames().isEmpty())
                .findFirst()
                .orElseThrow();
        var textureName = target.getTextureNames().iterator().next();
        var capturedTexture = target.textureSources(capturedChunks, textureName).uv();
        assertSame(capturedChunks, handle.chunks());
        assertTrue(handle.representation().hasMetadataPrefix());

        try (var image = capturedTexture.open()) {
            assertTrue(image.data().size() > 0);
        }
        for (var chunk : handle.view().getFileView().getAssetView()
                .getChunkTable().values()) {
            try (var ignored = capturedChunks.readStoredVerified(
                    chunk, BufferType.ARRAY)) {
                assertTrue(ignored.size() > 0);
            }
        }
    }

    @Test
    void ordinaryFileBackedHandlesRemainExactFileSources()
            throws Exception {
        var direct = openIndexed(copyFixture("direct.mxc"), location("direct"));
        var converted = openIndexed(
                copyFixture("converted.mxc"), location("converted"));

        for (var handle : new ManagedContainer[]{direct, converted}) {
            assertInstanceOf(FileChunkDataSource.class, handle.chunks());
            assertFalse(handle.residentOnly());
            assertTrue(Files.isSameFile(handle.file(),
                    handle.location().path().value().equals("direct")
                            ? temp.resolve("direct.mxc") : temp.resolve("converted.mxc")));
        }
    }

    @Test
    void fileRepresentationOwnsTheExactMetadataPrefix() throws Exception {
        var file = copyFixture("prefix.mxc");
        var handle = openIndexed(file, location("prefix"));
        var representation = handle.representation();
        var manifest = representation.view().getFileView().getAssetView().getChunkInfo(
                ModelFileConstant.MANIFEST_CHUNK_NAME);
        var expected = Arrays.copyOfRange(
                Files.readAllBytes(file), 0, manifest.offset() + manifest.size());

        try (var prefix = representation.metadataPrefix().orElseThrow();
             var actual = prefix.acquireArray()) {
            assertArrayEquals(expected, Arrays.copyOfRange(
                    actual.array(), actual.arrayOffset(),
                    actual.arrayOffset() + actual.size()));
        }
    }

    @Test
    void representationRejectsMismatchedModelAndContainerIds() throws Exception {
        var handle = openIndexed(copyFixture("identity.mxc"), location("identity"));
        var representation = handle.representation();
        try (var prefix = representation.metadataPrefix().orElseThrow()) {
            assertThrows(IllegalArgumentException.class, () -> new ModelRepresentation(
                    new ModelFileIdentity(different(representation.modelId()),
                            representation.containerId()), prefix, representation.view()));
            assertThrows(IllegalArgumentException.class, () -> new ModelRepresentation(
                    new ModelFileIdentity(representation.modelId(),
                            different(representation.containerId())),
                    prefix, representation.view()));
        }
    }

    private Path copyFixture(String name) throws Exception {
        return Files.copy(fixture, temp.resolve(name));
    }

    private static CatalogModelLocation location(String path) {
        return new CatalogModelLocation(
                CatalogRootKind.BUILTIN, new ModelPath(path));
    }

    private static ManagedContainer openIndexed(Path file, CatalogModelLocation location)
            throws Exception {
        final ModelFileIdentity identity;
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            identity = ModelFileIdentityReader.read(channel);
        }
        return ManagedContainer.openIndexed(new CatalogIndexEntry(
                identity, location, file));
    }

    private static Hash256 different(Hash256 value) {
        var bytes = value.bytes();
        bytes[0] ^= 1;
        return new Hash256(bytes);
    }
}
