package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.NativeBuffer;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.container.AssetContainerConstant;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.container.ByteArraySeekableChannel;
import com.elfmcys.ysm.format.schema.file.ChunkDataSource;
import com.elfmcys.ysm.format.schema.file.FileChunkDataSource;
import com.elfmcys.ysm.format.schema.file.ResidentChunkDataSource;
import com.elfmcys.ysm.format.schema.model.ModelFileView;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogIndexEntry;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelRepresentation;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public final class ManagedContainer implements ModelContent {
    private final @Nullable Path file;
    private final ChunkDataSource chunks;
    private final ModelRepresentation representation;
    private final CatalogModelLocation location;

    private ManagedContainer(@Nullable Path file, CatalogModelLocation location,
                             ModelRepresentation representation,
                             ChunkDataSource chunks) {
        this.file = file;
        this.location = location;
        this.representation = representation;
        this.chunks = chunks;
    }

    public static ManagedContainer openIndexed(CatalogIndexEntry entry) throws IOException {
        final boolean exists;
        try {
            exists = RegularFileProbe.exists(entry.backingFile());
        } catch (IOException | SecurityException failure) {
            throw AssetLoadException.access(
                    "Failed to inspect indexed model container: " + entry.backingFile(),
                    failure);
        }
        if (!exists) {
            throw AssetLoadException.content(
                    "Indexed model container is missing: " + entry.backingFile());
        }
        var representation = verifyFile(entry.backingFile());
        if (!representation.identity().equals(entry.identity())) {
            representation.close();
            throw new IOException("Indexed container identity changed: " + entry.backingFile());
        }
        return new ManagedContainer(entry.backingFile(), entry.location(), representation,
                new FileChunkDataSource(entry.backingFile(),
                        expectedFileSize(representation.view())));
    }

    public static ManagedContainer openDirect(
            Path file, CatalogModelLocation location) throws IOException {
        file = file.toAbsolutePath().normalize();
        var representation = verifyFile(file);
        try {
            return new ManagedContainer(file, location, representation,
                    new FileChunkDataSource(file,
                            expectedFileSize(representation.view())));
        } catch (RuntimeException | Error failure) {
            representation.close();
            throw failure;
        }
    }

    public static ManagedContainer openResidentDefault(
            byte[] container, CatalogModelLocation location)
            throws IOException {
        try (var buffer = ArrayBuffer.move(
                Objects.requireNonNull(container, "container").clone())) {
            return openResidentDefault(buffer, location);
        }
    }

    public static ManagedContainer openResidentDefault(
            UniBuffer container, CatalogModelLocation location)
            throws IOException {
        final ModelRepresentation representation;
        try (var array = container.acquireArray();
             var channel = new ByteArraySeekableChannel(
                     array.array(), array.arrayOffset(), array.size())) {
            representation = readRepresentation(channel, "builtin default memory");
        }
        try {
            var source = ResidentChunkDataSource.fromContainer(
                    container, representation.view().getFileView().getAssetView());
            return new ManagedContainer(null, location, representation, source);
        } catch (IOException | RuntimeException | Error failure) {
            representation.close();
            throw failure;
        }
    }

    private static ModelRepresentation readRepresentation(Path file) throws IOException {
        final FileChannel channel;
        try {
            channel = FileChannel.open(file, StandardOpenOption.READ);
        } catch (IOException failure) {
            throw AssetLoadException.access(
                    "Failed to open model container: " + file, failure);
        }
        Throwable loadFailure = null;
        try {
            return readRepresentation(channel, file.toString());
        } catch (IOException | RuntimeException | Error failure) {
            loadFailure = failure;
            throw failure;
        } finally {
            try {
                channel.close();
            } catch (IOException failure) {
                var access = AssetLoadException.access(
                        "Failed to close model container: " + file, failure);
                if (loadFailure == null) {
                    throw access;
                }
                loadFailure.addSuppressed(access);
            }
        }
    }

    private static ModelRepresentation readRepresentation(
            SeekableByteChannel channel, String source) throws IOException {
        var acceptedSize = channelSize(channel);
        var view = new ModelFileView(channel);
        if (expectedFileSize(view) != acceptedSize) {
            throw AssetLoadException.content(
                    "Model file length does not match its chunk table: " + source);
        }
        try (var prefix = readMetadataPrefix(channel, view)) {
            verifyFileSize(channel, acceptedSize, source);
            return new ModelRepresentation(
                    new ModelFileIdentity(
                            view.getModelHash(),
                            view.getFileView().getAssetView().getContainerId()),
                    prefix, view);
        }
    }

    public static ModelRepresentation verifyFile(Path file) throws IOException {
        file = file.toAbsolutePath().normalize();
        var before = FileStamp.capture(file);
        var representation = readRepresentation(file);
        try {
            verifyChunks(file, representation);
            var after = FileStamp.capture(file);
            if (!before.equals(after)) {
                throw new IOException("Model file changed while it was being verified: " + file);
            }
            return representation;
        } catch (IOException | RuntimeException | Error failure) {
            representation.close();
            throw failure;
        }
    }

    private static void verifyChunks(Path file, ModelRepresentation representation) throws IOException {
        var source = new FileChunkDataSource(file, expectedFileSize(representation.view()));
        var chunks = representation.view().getFileView().getAssetView().getChunkTable().values().stream()
                .sorted(Comparator.comparingInt(
                        AssetContainerView.ChunkInfo::offset))
                .toList();
        for (var chunk : chunks) {
            if (chunk.type().equals(AssetContainerConstant.VERIFICATION_CHUNK_TYPE)) {
                continue;
            }
            try (var ignored = source.readStoredVerified(chunk, BufferType.ARRAY)) {
                // Reading is the validation.
            }
        }
    }

    private static NativeBuffer readMetadataPrefix(
            SeekableByteChannel channel, ModelFileView view)
            throws IOException {
        var manifest = ModelFileView.requireMetadataLayout(
                view.getFileView().getAssetView());
        var size = Math.addExact(manifest.offset(), manifest.size());
        var result = NativeBuffer.allocate(size);
        var target = result.nio();
        try {
            channel.position(0);
            while (target.hasRemaining()) {
                if (channel.read(target) < 0) {
                    throw AssetLoadException.content(
                            "Model file changed while reading its metadata prefix");
                }
            }
            return result;
        } catch (AssetLoadException failure) {
            result.close();
            throw failure;
        } catch (IOException failure) {
            result.close();
            throw AssetLoadException.access(
                    "Failed to read model metadata prefix", failure);
        } catch (RuntimeException | Error failure) {
            result.close();
            throw failure;
        }
    }

    private static long expectedFileSize(ModelFileView view) {
        return view.getFileView().getAssetView().getChunkTable().values().stream()
                .mapToLong(chunk -> (long) chunk.offset() + chunk.size())
                .max()
                .orElseThrow(() -> new IllegalStateException("Model container has no chunks"));
    }

    private static long channelSize(SeekableByteChannel channel) throws IOException {
        try {
            return channel.size();
        } catch (IOException failure) {
            throw AssetLoadException.access("Failed to inspect model container length", failure);
        }
    }

    private static void verifyFileSize(
            SeekableByteChannel channel, long expected, Object source)
            throws IOException {
        if (channelSize(channel) != expected) {
            throw AssetLoadException.content(
                    "Model file length changed while reading metadata: " + source);
        }
    }

    public Path file() {
        if (file == null) {
            throw new IllegalStateException("Resident model has no file backing");
        }
        return file;
    }

    public ModelFileView view() {
        return representation.view();
    }

    public ChunkDataSource chunks() {
        return chunks;
    }

    public boolean residentOnly() {
        return chunks instanceof ResidentChunkDataSource;
    }

    @Override
    public ModelRepresentation representation() {
        return representation;
    }

    public CatalogModelLocation location() {
        return location;
    }

    private record FileStamp(Object fileKey, long size, FileTime modified) {
        private static FileStamp capture(Path file) throws IOException {
            try {
                var attributes = Files.readAttributes(file, BasicFileAttributes.class);
                return new FileStamp(attributes.fileKey(), attributes.size(),
                        attributes.lastModifiedTime());
            } catch (IOException | SecurityException failure) {
                throw AssetLoadException.access(
                        "Failed to inspect model container: " + file, failure);
            }
        }
    }
}
