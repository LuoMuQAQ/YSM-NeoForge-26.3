package com.elfmcys.ysm.format.schema.baked.asset;

import com.elfmcys.ysm.format.schema.file.AssetFileWriter;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.storage.ModelHashing;
import com.elfmcys.ysm.proto.baked.asset.BakedAnimationEntry;
import com.elfmcys.ysm.proto.baked.asset.BakedAssetManifest;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Animation;
import com.elfmcys.ysm.util.ProtoBytes;
import com.elfmcys.ysm.util.ProtoUtil;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;

/** Writes one immutable baked target asset with one zstd chunk per animation. */
public final class BakedAssetWriter extends AssetFileWriter {
    public BakedAssetWriter() {
        setSchemaId(BakedAssetConstant.SCHEMA_ID);
        setProperty(BakedAssetConstant.PROP_VERSION, BakedAssetConstant.CURRENT_VERSION.toString());
    }

    public void setData(Hash256 containerId, String renderTargetId, Hash256 inputHash,
                        Iterable<Animation> animations)
            throws IOException {
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(inputHash, "inputHash");
        Objects.requireNonNull(animations, "animations");
        if (renderTargetId == null || renderTargetId.isBlank()) {
            throw new IOException("Baked asset render target id is empty");
        }
        var manifest = BakedAssetManifest.newBuilder()
                .setRenderTargetId(renderTargetId)
                .setContainerId(ProtoBytes.wrap(containerId))
                .setInputHash(ProtoBytes.wrap(inputHash));
        var names = new HashSet<String>();
        var index = 0;
        for (var animation : animations) {
            if (animation == null || animation.name().isBlank()
                    || !names.add(animation.name())) {
                throw new IOException("Duplicate or empty animation name in render target: "
                        + (animation == null ? "null" : animation.name()));
            }
            var chunkName = BakedAssetConstant.ANIM_CHUNK_PREFIX + "%06d".formatted(index++);
            var sourceHash = ModelHashing.blake3(ProtoUtil.serializeToArray(animation));
            var entry = BakedAnimationEntry.newBuilder()
                    .setName(animation.name())
                    .setChunkName(chunkName)
                    .setSourceHash(ProtoBytes.wrap(sourceHash))
                    .build();
            manifest.addAnimations(entry);
            addProtoChunk(chunkName, animation, 16);
        }
        addProtoChunk(BakedAssetConstant.MANIFEST_CHUNK_NAME, manifest.build(), 0);
    }
}
