package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.natives.image.Image;
import com.elfmcys.ysm.natives.image.ImageEncoder;
import com.elfmcys.ysm.natives.image.ImageSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Independently replaceable preview images, weakly associated by container id only. */
public final class PreviewStore {
    public static final int MAX_STORED_BYTES = 16 * 1024 * 1024;
    public static final int MAX_DIMENSION = 4_096;
    public static final long MAX_PIXELS = 16L * 1024 * 1024;

    private static final String PREVIEW_SUFFIX = ".image";

    /** Independent preview images; weakly associated by container id. */
    private static final String PREVIEW_DIRECTORY = "previews";

    private final Path cacheRoot;
    private final AtomicSharedCache cache;

    public PreviewStore(Path cacheRoot, AtomicSharedCache cache) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public PreviewStore(Path cacheRoot) {
        this(cacheRoot, new AtomicSharedCache(cacheRoot));
    }

    private Path previewPath(Hash256 containerId) {
        return cacheRoot.resolve(PREVIEW_DIRECTORY).resolve(containerId + PREVIEW_SUFFIX);
    }

    public Optional<EncodedPreview> load(Hash256 containerId) {
        Objects.requireNonNull(containerId, "containerId");
        var path = previewPath(containerId);
        try {
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.of(decode(readBounded(path)));
        } catch (IOException | RuntimeException invalid) {
            YesSteveModel.LOGGER.debug("Ignoring invalid preview cache entry {}", path, invalid);
            return Optional.empty();
        }
    }

    /** Returns the valid in-memory image even when optional cache persistence fails. */
    public EncodedPreview accept(Hash256 containerId, byte[] bytes) throws IOException {
        return accept(containerId, ArrayBuffer.borrow(bytes));
    }

    /** Returns the valid in-memory image even when optional cache persistence fails. */
    public EncodedPreview accept(Hash256 containerId, UniBuffer bytes) throws IOException {
        Objects.requireNonNull(containerId, "containerId");
        var preview = decode(bytes);
        var expected = preview.bytes();
        var target = previewPath(containerId);
        try {
            cache.materializeReplacing("preview", containerId.toString(), target,
                    path -> {
                        try {
                            return Arrays.equals(readBounded(path), expected);
                        } catch (IOException invalid) {
                            return false;
                        }
                    }, path -> Files.write(path, expected));
        } catch (IOException | RuntimeException failure) {
            YesSteveModel.LOGGER.warn(
                    "Failed to persist usable preview cache entry {}", target, failure);
        }
        return preview;
    }

    public static EncodedPreview read(ImageSource source, int encodedSize) throws IOException {
        Objects.requireNonNull(source, "source");
        validateStoredSize(encodedSize);
        try (var image = source.open()) {
            validateDimensions(image.width(), image.height());
            try (var ignored = image.decodeToBuffer()) {
                // Full decode is the image-validity boundary.
            }
            if (image.format() == Image.Format.RGBA) {
                try (var pixels = image.data().acquireNative();
                     var encoded = ImageEncoder.encodeLossless(
                             pixels, image.width(), image.height())) {
                    return encoded(encoded);
                }
            }
            return encoded(image);
        }
    }

    public static EncodedPreview decode(byte[] bytes) throws IOException {
        return decode(ArrayBuffer.borrow(Objects.requireNonNull(bytes, "bytes")));
    }

    public static EncodedPreview decode(UniBuffer bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        validateStoredSize(bytes.size());
        try (var image = Image.probe(bytes)) {
            if (image.format() == Image.Format.RGBA) {
                throw new IOException("Standalone preview must use a self-describing encoding");
            }
            validateDimensions(image.width(), image.height());
            try (var ignored = image.decodeToBuffer()) {
                // Probe alone does not prove that the complete image is decodable.
            }
            return new EncodedPreview(bytes.acquire(), image.format(),
                    image.width(), image.height());
        }
    }

    private static EncodedPreview encoded(Image image) throws IOException {
        validateStoredSize(image.data().size());
        return new EncodedPreview(image.data().acquire(), image.format(),
                image.width(), image.height());
    }

    private static void validateDimensions(int width, int height) throws IOException {
        final long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException overflow) {
            throw new IOException("Preview dimensions overflow", overflow);
        }
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || pixels > MAX_PIXELS) {
            throw new IOException("Preview dimensions exceed their bound: "
                    + width + "x" + height);
        }
    }

    private static void validateStoredSize(int size) throws IOException {
        if (size <= 0 || size > MAX_STORED_BYTES) {
            throw new IOException("Preview encoded size exceeds its bound");
        }
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            var bytes = input.readNBytes(MAX_STORED_BYTES + 1);
            validateStoredSize(bytes.length);
            return bytes;
        }
    }

    public static final class EncodedPreview implements ImageSource {
        private final UniBuffer bytes;
        private final Image.Format format;
        private final int width;
        private final int height;

        private EncodedPreview(UniBuffer bytes, Image.Format format, int width, int height) {
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            this.format = Objects.requireNonNull(format, "format");
            this.width = width;
            this.height = height;
        }

        public byte[] bytes() {
            var result = new byte[bytes.size()];
            bytes.nio().get(result);
            return result;
        }

        public Image.Format format() {
            return format;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        @Override
        public Image open() {
            return new Image(format, width, height, bytes.acquire());
        }
    }
}
