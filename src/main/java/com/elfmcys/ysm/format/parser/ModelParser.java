package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.format.vfs.VirtualFileSystem;
import com.elfmcys.ysm.model.domain.Hash256;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** Coordinates raw source access and model assembly. The caller retains ownership of the VFS. */
public final class ModelParser {
    public static final String CANONICALIZER_PROFILE_VERSION = "2";
    public static final String PARSER_PROFILE_VERSION = "1";
    public static final String IMAGE_POLICY_PROFILE_VERSION = "2";

    private ModelParser() {
    }

    public static CapturedModel capture(VirtualFileSystem source) {
        var data = new CapturedSourceData(Objects.requireNonNull(source, "source"));
        try {
            var scan = parse(data, null, true, DefaultAnimationFilter.keepAll(), false);
            data.freeze();
            return new CapturedModel(scan.modelHash(), data, scan.diagnostics());
        } catch (RuntimeException | Error failure) {
            try {
                data.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public static Path parse(VirtualFileSystem vfs, Path outputDirectory,
                             DefaultAnimationFilter defaultAnimations) {
        return compile(vfs, outputDirectory, defaultAnimations).stagedContainer();
    }

    public static RawCompileResult compile(VirtualFileSystem vfs, Path outputDirectory,
                                           DefaultAnimationFilter defaultAnimations) {
        var result = parse(vfs, Objects.requireNonNull(outputDirectory, "outputDirectory"),
                false, defaultAnimations, true);
        return new RawCompileResult(result.modelHash(),
                Objects.requireNonNull(result.output(), "output"), result.diagnostics());
    }

    /** Generates the builtin default without applying the default-animation filter to itself. */
    public static Path parseBuiltinDefault(VirtualFileSystem vfs, Path outputDirectory) {
        return Objects.requireNonNull(parse(vfs,
                Objects.requireNonNull(outputDirectory, "outputDirectory"),
                false, DefaultAnimationFilter.keepAll(), false).output(),
                "output");
    }

    /** Builds the required default as one complete in-memory container. */
    public static MemoryContainer parseBuiltinDefaultMemory(VirtualFileSystem vfs) {
        final Path temporary;
        try {
            temporary = Files.createTempDirectory("ysm-builtin-default-");
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "Failed to build the builtin default in memory", failure);
        }
        try {
            var result = parse(vfs, temporary, false, DefaultAnimationFilter.keepAll(), false);
            return new MemoryContainer(result.modelHash(),
                    ArrayBuffer.move(Files.readAllBytes(
                            Objects.requireNonNull(result.output(), "output"))));
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "Failed to build the builtin default in memory", failure);
        } finally {
            try (var paths = Files.walk(temporary)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // A failed best-effort cleanup must not mask the parse result.
                    }
                });
            } catch (IOException ignored) {
                // A failed best-effort cleanup must not mask the parse result.
            }
        }
    }

    /** Scans the parse resource set without decoding assets or writing a model container. */
    public static Hash256 scanModelHash(VirtualFileSystem vfs) {
        return parse(vfs, null, true, DefaultAnimationFilter.keepAll(), false)
                .modelHash();
    }

    public record MemoryContainer(Hash256 modelHash, ArrayBuffer bytes)
            implements AutoCloseable {
        public MemoryContainer {
            Objects.requireNonNull(modelHash, "modelHash");
            Objects.requireNonNull(bytes, "bytes");
        }

        @Override
        public ArrayBuffer bytes() {
            return bytes.acquire();
        }

        @Override
        public void close() {
            bytes.close();
        }
    }

    private static RawModelAssembler.Result parse(VirtualFileSystem vfs, Path outputDirectory,
                                                  boolean dryRun,
                                                  DefaultAnimationFilter defaultAnimations,
                                                  boolean recompressImages) {
        var source = new RawModelSource(Objects.requireNonNull(vfs, "vfs"));
        try (var assembler = new RawModelAssembler(source, outputDirectory, dryRun,
                Objects.requireNonNull(defaultAnimations, "defaultAnimations"), recompressImages)) {
            return assembler.parse();
        }
    }
}
