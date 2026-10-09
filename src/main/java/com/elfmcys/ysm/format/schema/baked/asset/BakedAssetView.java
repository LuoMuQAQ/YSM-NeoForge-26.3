package com.elfmcys.ysm.format.schema.baked.asset;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.format.container.AssetContainerConstant;
import com.elfmcys.ysm.format.container.AssetContainerReader;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.container.ChunkDecoding;
import com.elfmcys.ysm.format.container.InlineChunkReader;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.resource.client.AnimationStore;
import com.elfmcys.ysm.model.resource.client.ModelResourceFailureGate;
import com.elfmcys.ysm.model.storage.ModelHashing;
import com.elfmcys.ysm.proto.baked.asset.BakedAnimationEntry;
import com.elfmcys.ysm.proto.baked.asset.BakedAssetManifest;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Animation;
import com.elfmcys.ysm.util.ProtoBytes;
import com.elfmcys.ysm.util.ProtoUtil;
import com.elfmcys.ysm.version.VersionCompatibility;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

/** Metadata view for a baked render target. Animation payloads remain unopened. */
public final class BakedAssetView {
    private final AssetContainerView assetView;
    private final BakedAssetManifest manifest;
    private final Map<String, BakedAnimationEntry> animations;

    public BakedAssetView(SeekableByteChannel file) throws IOException {
        assetView = AssetContainerReader.read(file);
        validateCompleteFile(file, assetView);
        if (!BakedAssetConstant.SCHEMA_ID.equals(assetView.getSchema())) {
            throw new IOException("Schema ID mismatch: " + assetView.getSchema());
        }
        var version = Objects.requireNonNull(assetView.getSchemaProperty(
                BakedAssetConstant.PROP_VERSION), "Version property not found");
        if (!VersionCompatibility.isCompatible(BakedAssetConstant.CURRENT_VERSION.toString(),
                version, (current, candidate) -> new DefaultArtifactVersion(current)
                        .equals(new DefaultArtifactVersion(candidate)))) {
            throw new UnsupportedEncodingException("Unsupported baked asset version: \"" + version + "\"");
        }
        var manifestChunk = assetView.getChunkInfo(BakedAssetConstant.MANIFEST_CHUNK_NAME);
        if (manifestChunk == null) {
            throw new IOException("Baked asset contains no manifest");
        }
        if (!manifestChunk.encoding().isEmpty() || manifestChunk.decodeSize() != 0
                || manifestChunk.size() == 0) {
            throw new IOException("Invalid baked asset manifest chunk");
        }
        try (var data = InlineChunkReader.readPayload(file, manifestChunk, BufferType.ARRAY)) {
            if (data.size() == 0) {
                throw new IOException("Baked asset contains no manifest");
            }
            if (!(data instanceof ArrayBuffer arrayBuffer)) {
                throw new IOException("Baked asset manifest is not array-backed");
            }
            manifest = BakedAssetManifest.parseFrom(ProtoUtil.source(arrayBuffer));
        }
        validateHash(manifest.containerId(), "container id");
        validateHash(manifest.inputHash(), "input hash");
        if (manifest.renderTargetId().isBlank()) {
            throw new IOException("Baked asset contains no render target id");
        }
        var index = new LinkedHashMap<String, BakedAnimationEntry>();
        var expectedChunks = new HashSet<String>();
        expectedChunks.add(AssetContainerConstant.VERIFICATION_CHUNK_TYPE);
        expectedChunks.add(BakedAssetConstant.MANIFEST_CHUNK_NAME);
        var animationIndex = 0;
        for (var entry : manifest.animations()) {
            validateHash(entry.sourceHash(), "animation source hash");
            var expectedChunkName = BakedAssetConstant.ANIM_CHUNK_PREFIX
                    + "%06d".formatted(animationIndex++);
            var chunk = assetView.getChunkInfo(entry.chunkName());
            if (entry.name().isBlank() || !entry.chunkName().equals(expectedChunkName)
                    || chunk == null || !ChunkDecoding.isZstd(chunk)
                    || chunk.size() == 0 || chunk.decodeSize() == 0
                    || !expectedChunks.add(entry.chunkName())
                    || index.putIfAbsent(entry.name(), entry) != null) {
                throw new IOException("Invalid baked animation index entry: " + entry.name());
            }
        }
        if (assetView.getChunkTable().size() != expectedChunks.size()
                || !assetView.getChunkTable().keySet().containsAll(expectedChunks)) {
            throw new IOException("Baked asset chunk table does not match its animation index");
        }
        animations = Map.copyOf(index);
    }

    public boolean matches(Hash256 containerId, String targetId, Hash256 inputHash) {
        return ProtoBytes.equals(containerId, manifest.containerId())
                && targetId.equals(manifest.renderTargetId())
                && ProtoBytes.equals(inputHash, manifest.inputHash());
    }

    @Nullable
    public Animation readAnimation(SeekableByteChannel file, String animationName)
            throws IOException {
        var entry = animations.get(animationName);
        if (entry == null) {
            return null;
        }
        var chunk = assetView.getChunkInfo(entry.chunkName());
        if (chunk == null) {
            throw new IOException("Baked animation chunk is missing: " + entry.chunkName());
        }
        try (var data = InlineChunkReader.readPayload(file, chunk, BufferType.ARRAY)) {
            if (data.size() == 0) {
                throw new IOException("Baked animation chunk is missing: " + entry.chunkName());
            }
            if (!(data instanceof ArrayBuffer arrayBuffer)) {
                throw new IOException("Baked animation is not array-backed");
            }
            if (!ProtoBytes.equals(ModelHashing.blake3(arrayBuffer), entry.sourceHash())) {
                throw new IOException("Baked animation source hash mismatch: " + animationName);
            }
            var animation = Animation.parseFrom(ProtoUtil.source(arrayBuffer));
            if (!animationName.equals(animation.name())) {
                throw new IOException("Baked animation name mismatch: " + animationName);
            }
            return animation;
        }
    }

    public AnimationStore createAnimationStore(ChannelSource channels,
                                                AnimationStore.AnimationBinder binder,
                                                AnimationStore fallback,
                                                Function<String, ModelResourceFailureGate> failureGates) {
        return AnimationStore.lazy(animations.keySet(), name -> {
            try (var channel = channels.open()) {
                return readAnimation(channel, name);
            }
        }, binder, fallback, failureGates);
    }

    private static void validateHash(ByteBuffer value, String name) throws IOException {
        if (value.remaining() != Hash256.SIZE) {
            throw new IOException("Invalid baked asset " + name);
        }
    }

    private static void validateCompleteFile(SeekableByteChannel file, AssetContainerView view)
            throws IOException {
        var fileSize = file.size();
        long lastByte = 0;
        for (var chunk : view.getChunkTable().values()) {
            var end = (long) chunk.offset() + chunk.size();
            if (end > fileSize) {
                throw new IOException("Baked asset chunk exceeds file boundary: " + chunk.type());
            }
            lastByte = Math.max(lastByte, end);
        }
        if (lastByte != fileSize) {
            throw new IOException("Baked asset contains trailing or missing chunk data");
        }
    }

    @FunctionalInterface
    public interface ChannelSource {
        SeekableByteChannel open() throws IOException;
    }
}
