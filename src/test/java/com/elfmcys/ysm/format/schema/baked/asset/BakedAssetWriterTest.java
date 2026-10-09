package com.elfmcys.ysm.format.schema.baked.asset;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.format.container.AssetContainerReader;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.container.InlineChunkReader;
import com.elfmcys.ysm.format.schema.file.AssetFileWriter;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.resource.client.ModelResourceFailureGate;
import com.elfmcys.ysm.model.storage.AtomicSharedCache;
import com.elfmcys.ysm.model.storage.ModelHashing;
import com.elfmcys.ysm.proto.baked.asset.BakedAnimationEntry;
import com.elfmcys.ysm.proto.baked.asset.BakedAssetManifest;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Animation;
import com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType;
import com.elfmcys.ysm.testutil.ProtobufJavaOracle;
import com.elfmcys.ysm.util.ProtoBytes;
import com.elfmcys.ysm.util.ProtoUtil;
import com.google.protobuf.DynamicMessage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakedAssetWriterTest {
    private static final Hash256 CONTAINER_ID = hash(1);
    private static final Hash256 INPUT_HASH = hash(2);
    private static final String TARGET_ID = "player";

    @Test
    void writesOneCanonicalZstdChunkPerAnimationInInputOrder(@TempDir Path temp)
            throws Exception {
        var source = List.of(animation("walk"), animation("idle"), animation("jump"));
        var file = writeAsset(temp.resolve("animations.ysm-cache"), source);

        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            var container = AssetContainerReader.read(channel);
            var manifest = readManifest(channel);
            var protobufManifest = readProtobufJava(
                    channel,
                    container.getChunkInfo(BakedAssetConstant.MANIFEST_CHUNK_NAME),
                    "ysm.baked.asset.BakedAssetManifest");
            assertEquals(source.size() + 2, container.getChunkTable().size());
            assertEquals(source.size(), manifest.animations().size());
            assertEquals(source.size(), protobufManifest.getRepeatedFieldCount(
                    ProtobufJavaOracle.field(
                            protobufManifest.getDescriptorForType(), "animations")));
            for (var index = 0; index < source.size(); index++) {
                var expectedChunk = BakedAssetConstant.ANIM_CHUNK_PREFIX
                        + "%06d".formatted(index);
                var entry = manifest.animations().get(index);
                var chunk = container.getChunkInfo(expectedChunk);
                assertEquals(source.get(index).name(), entry.name());
                assertEquals(expectedChunk, entry.chunkName());
                assertNotNull(chunk);
                assertEquals("zstd", chunk.encoding());
                assertTrue(chunk.size() > 0);
                assertTrue(chunk.decodeSize() > 0);
            }
            var protobufAnimation = readProtobufJava(
                    channel,
                    container.getChunkInfo(BakedAssetConstant.ANIM_CHUNK_PREFIX + "000000"),
                    "mixel.asset.model.data.Animation");
            assertEquals("walk", protobufAnimation.getField(
                    ProtobufJavaOracle.field(
                            protobufAnimation.getDescriptorForType(), "name")));
        }

        var view = open(file);
        assertTrue(view.matches(CONTAINER_ID, TARGET_ID, INPUT_HASH));
        assertFalse(view.matches(hash(3), TARGET_ID, INPUT_HASH));
        assertFalse(view.matches(CONTAINER_ID, "vehicle", INPUT_HASH));
        assertFalse(view.matches(CONTAINER_ID, TARGET_ID, hash(3)));
    }

    @Test
    void writesEmptyAnimationSetAsManifestOnlyAsset(@TempDir Path temp) throws Exception {
        var file = writeAsset(temp.resolve("empty.ysm-cache"), List.of());

        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            var container = AssetContainerReader.read(channel);
            assertEquals(2, container.getChunkTable().size());
            assertEquals(0, readManifest(channel).animations().size());
        }
        var store = open(file).createAnimationStore(
                () -> FileChannel.open(file, StandardOpenOption.READ), ignored -> null,
                null, ignored -> ModelResourceFailureGate.none());
        assertTrue(store.keySet().isEmpty());
        assertNull(store.state("missing"));
    }

    @Test
    void readsOnlyTheRequestedAnimationChunk(@TempDir Path temp) throws Exception {
        var file = writeAsset(temp.resolve("isolated.ysm-cache"),
                List.of(animation("walk"), animation("broken")));
        int brokenOffset;
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            brokenOffset = AssetContainerReader.read(channel)
                    .getChunkInfo(BakedAssetConstant.ANIM_CHUNK_PREFIX + "000001").offset();
        }
        corruptByte(file, brokenOffset);

        var view = open(file);
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            assertEquals("walk", view.readAnimation(channel, "walk").name());
        }
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            assertThrows(IOException.class, () -> view.readAnimation(channel, "broken"));
        }
    }

    @Test
    void rejectsMalformedAnimationIndexesAndIncompleteFiles(@TempDir Path temp) throws Exception {
        var first = animation("first");
        var second = animation("second");

        var missing = manifest(entry("first", "animation/000000", first));
        assertRejected(writeCustom(temp.resolve("missing.ysm-cache"), missing, List.of()));

        var duplicateReference = manifest(
                entry("first", "animation/000000", first),
                entry("second", "animation/000000", second));
        assertRejected(writeCustom(temp.resolve("duplicate.ysm-cache"), duplicateReference,
                List.of(new Chunk("animation/000000", first, 16))));

        assertRejected(writeCustom(temp.resolve("extra.ysm-cache"), manifest(),
                List.of(new Chunk("animation/000000", first, 16))));

        var raw = manifest(entry("first", "animation/000000", first));
        assertRejected(writeCustom(temp.resolve("raw.ysm-cache"), raw,
                List.of(new Chunk("animation/000000", first, 0))));

        var truncated = writeAsset(temp.resolve("truncated.ysm-cache"), List.of(first));
        try (var channel = FileChannel.open(truncated, StandardOpenOption.WRITE)) {
            channel.truncate(channel.size() - 1);
        }
        assertRejected(truncated);
    }

    @Test
    void rejectsAnimationPayloadWhoseNameDoesNotMatchItsIndex(@TempDir Path temp)
            throws Exception {
        var payload = animation("payload-name");
        var manifest = manifest(entry("index-name", "animation/000000", payload));
        var file = writeCustom(temp.resolve("name-mismatch.ysm-cache"), manifest,
                List.of(new Chunk("animation/000000", payload, 16)));

        var view = open(file);
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            assertThrows(IOException.class,
                    () -> view.readAnimation(channel, "index-name"));
        }
    }

    @Test
    void publishesOnlyAfterACompleteCandidateValidates(@TempDir Path temp) throws Exception {
        var cacheRoot = temp.resolve("cache");
        var cache = new AtomicSharedCache(cacheRoot);
        var target = cacheRoot.resolve("test").resolve("asset.ysm-cache");
        var targetWasHidden = new AtomicBoolean();

        cache.materialize("test", "asset", target,
                candidate -> open(candidate).matches(CONTAINER_ID, TARGET_ID, INPUT_HASH),
                candidate -> {
                    assertFalse(Files.exists(target));
                    writeAsset(candidate, List.of(animation("walk")));
                    targetWasHidden.set(!Files.exists(target));
                });

        assertTrue(targetWasHidden.get());
        assertTrue(Files.isRegularFile(target));
        assertTrue(open(target).matches(CONTAINER_ID, TARGET_ID, INPUT_HASH));

        var failedTarget = cacheRoot.resolve("test")
                .resolve("failed.ysm-cache");
        assertThrows(IOException.class, () -> cache.materialize(
                "test", "failed", failedTarget, ignored -> false, candidate -> {
                    writeAsset(candidate, List.of(animation("walk")));
                    throw new IOException("write failed before commit");
                }));
        assertFalse(Files.exists(failedTarget));
        try (var files = Files.list(failedTarget.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith(failedTarget.getFileName() + ".tmp-")));
        }
    }

    @Test
    void rejectsEmptyAndDuplicateAnimationNames(@TempDir Path temp) {
        assertThrows(IOException.class, () -> writeAsset(
                temp.resolve("empty-name.ysm-cache"), List.of(animation(""))));
        assertThrows(IOException.class, () -> writeAsset(
                temp.resolve("duplicate-name.ysm-cache"),
                List.of(animation("walk"), animation("walk"))));
    }

    private static Path writeAsset(Path file, List<Animation> animations)
            throws IOException {
        Files.createDirectories(file.getParent());
        try (var writer = new BakedAssetWriter();
             var channel = FileChannel.open(file, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writer.setData(CONTAINER_ID, TARGET_ID, INPUT_HASH, animations);
            writer.write(channel);
        }
        return file;
    }

    private static Path writeCustom(Path file, BakedAssetManifest manifest,
                                    List<Chunk> chunks) throws IOException {
        try (var writer = new TestAssetWriter();
             var channel = FileChannel.open(file, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for (var chunk : chunks) {
                writer.animation(chunk.name(), chunk.animation(), chunk.compression());
            }
            writer.manifest(manifest);
            writer.write(channel);
        }
        return file;
    }

    private static BakedAssetView open(Path file) throws IOException {
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return new BakedAssetView(channel);
        }
    }

    private static BakedAssetManifest readManifest(FileChannel channel)
            throws IOException {
        var chunk = AssetContainerReader.read(channel)
                .getChunkInfo(BakedAssetConstant.MANIFEST_CHUNK_NAME);
        try (var data = InlineChunkReader.readPayload(channel, chunk, BufferType.ARRAY)) {
            return BakedAssetManifest.parseFrom(
                    ProtoUtil.source((ArrayBuffer) data));
        }
    }

    private static DynamicMessage readProtobufJava(
            FileChannel channel, AssetContainerView.ChunkInfo chunk,
            String messageName) throws IOException {
        try (var data = InlineChunkReader.readPayload(channel, chunk, BufferType.ARRAY)) {
            return ProtobufJavaOracle.parse(messageName, ProtoBytes.copy(data.nio()));
        }
    }

    private static BakedAssetManifest manifest(
            BakedAnimationEntry... entries) {
        var manifest = BakedAssetManifest.newBuilder()
                .setRenderTargetId(TARGET_ID)
                .setContainerId(ProtoBytes.wrap(CONTAINER_ID))
                .setInputHash(ProtoBytes.wrap(INPUT_HASH));
        for (var entry : entries) {
            manifest.addAnimations(entry);
        }
        return manifest.build();
    }

    private static BakedAnimationEntry entry(
            String name, String chunkName, Animation source)
            throws IOException {
        var entry = BakedAnimationEntry.newBuilder()
                .setName(name).setChunkName(chunkName)
                .setSourceHash(ProtoBytes.wrap(
                        ModelHashing.blake3(ProtoUtil.serializeToArray(source))));
        return entry.build();
    }

    private static Animation animation(String name) {
        return Animation.newBuilder()
                .setName(name)
                .setLength(0)
                .setLoop(LoopType.LOOP_TYPE_UNSPECIFIED)
                .build();
    }

    private static Hash256 hash(int value) {
        var bytes = new byte[Hash256.SIZE];
        Arrays.fill(bytes, (byte) value);
        return new Hash256(bytes);
    }

    private static void corruptByte(Path file, int offset) throws IOException {
        try (var channel = FileChannel.open(file,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            var value = ByteBuffer.allocate(1);
            channel.position(offset);
            channel.read(value);
            value.flip();
            var corrupted = (byte) (value.get() ^ 0x7f);
            value.clear().put(corrupted).flip();
            channel.position(offset);
            channel.write(value);
        }
    }

    private static void assertRejected(Path file) {
        assertThrows(IOException.class, () -> open(file));
    }

    private record Chunk(String name, Animation animation, int compression) {
    }

    private static final class TestAssetWriter extends AssetFileWriter {
        private TestAssetWriter() {
            setSchemaId(BakedAssetConstant.SCHEMA_ID);
            setProperty(BakedAssetConstant.PROP_VERSION,
                    BakedAssetConstant.CURRENT_VERSION.toString());
        }

        private void animation(String name, Animation animation,
                               int compression) throws IOException {
            addProtoChunk(name, animation, compression);
        }

        private void manifest(BakedAssetManifest manifest) throws IOException {
            addProtoChunk(BakedAssetConstant.MANIFEST_CHUNK_NAME, manifest, 0);
        }
    }
}
