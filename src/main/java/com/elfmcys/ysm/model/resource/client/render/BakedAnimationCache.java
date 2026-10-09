package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.schema.baked.asset.BakedAssetView;
import com.elfmcys.ysm.format.schema.baked.asset.BakedAssetWriter;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.resource.client.AnimationStore;
import com.elfmcys.ysm.model.resource.client.ModelResourceFailureGate;
import com.elfmcys.ysm.model.storage.AtomicSharedCache;
import com.elfmcys.ysm.model.storage.ModelHashing;
import com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Process-shared immutable baked-animation cache; loaded bindings remain animation-set-scoped. */
public final class BakedAnimationCache {
    static final String CACHE_SUFFIX = ".anim.ysm-cache";
    static final String CACHE_ABI = "animation-0.3.0-unstable";
    private static final byte[] HASH_DOMAIN =
            ("ysm." + CACHE_ABI + "\0").getBytes(StandardCharsets.UTF_8);

    private final Path bakedRoot;
    private final AtomicSharedCache cache;

    public BakedAnimationCache(Path bakedRoot, AtomicSharedCache cache) {
        this.bakedRoot = bakedRoot;
        this.cache = cache;
    }

    static String profileKey() {
        return CACHE_ABI;
    }

    public AnimationStore loadOrBake(Hash256 containerId, String targetId,
                                     String animationSet, Hash256 definitionHash,
                                     Iterable<? extends Map.Entry<String,
                                             AnimationFile>> animationFiles,
                                     AnimationStore fallback,
                                     Function<String, ModelResourceFailureGate> failureGates) throws IOException {
        var animations = flatten(animationFiles);
        var inputHash = bakeInputHash(containerId, definitionHash, targetId, animationSet);
        var target = path(containerId, inputHash);
        try {
            cache.materialize("baked-animation", containerId + "/" + inputHash,
                    target, candidate -> validate(candidate, containerId, targetId, inputHash),
                    candidate -> write(candidate, containerId, targetId, inputHash, animations));
        } catch (AssetLoadException error) {
            throw error;
        } catch (IOException error) {
            throw AssetLoadException.access(
                    "Failed to materialize baked animation cache", error);
        }
        var view = open(target);
        if (!view.matches(containerId, targetId, inputHash)) {
            throw AssetLoadException.content("Baked animation cache identity mismatch");
        }
        return view.createAnimationStore(() -> FileChannel.open(target, StandardOpenOption.READ),
                AnimationProtoMapper::animation, fallback, failureGates);
    }

    public static AnimationStore bindResident(
            Iterable<? extends Map.Entry<String,
                    AnimationFile>> animationFiles,
            BoundAnimationObserver observer) throws IOException {
        var bound = new LinkedHashMap<String,
                com.elfmcys.ysm.geckolib3.core.builder.Animation>();
        for (var animation : flatten(animationFiles)) {
            final com.elfmcys.ysm.geckolib3.core.builder.Animation value;
            try {
                value = AnimationProtoMapper.animation(animation);
            } catch (RuntimeException error) {
                throw AssetLoadException.content(
                        "Invalid animation: " + animation.name(), error);
            }
            observer.accept(animation, value);
            bound.put(animation.name(), value);
        }
        return AnimationStore.eager(bound);
    }

    @FunctionalInterface
    public interface BoundAnimationObserver {
        void accept(com.elfmcys.ysm.proto.mixel.asset.model.data.Animation source,
                    com.elfmcys.ysm.geckolib3.core.builder.Animation bound)
                throws IOException;
    }

    Path path(Hash256 containerId, Hash256 inputHash) {
        return bakedRoot.resolve(CACHE_ABI).resolve(containerId.toString())
                .resolve(inputHash + CACHE_SUFFIX);
    }

    static ArrayList<com.elfmcys.ysm.proto.mixel.asset.model.data.Animation> flatten(
            Iterable<? extends Map.Entry<String,
                    AnimationFile>> animationFiles) throws IOException {
        var result = new LinkedHashMap<String,
                com.elfmcys.ysm.proto.mixel.asset.model.data.Animation>();
        for (var file : animationFiles) {
            for (var animation : file.getValue().animations()) {
                if (animation.name().isBlank()) {
                    throw AssetLoadException.content(
                            "Empty animation name in animation set: "
                                    + animation.name());
                }
                result.put(animation.name(), animation);
            }
        }
        return new ArrayList<>(result.values());
    }

    static Hash256 bakeInputHash(Hash256 containerId, Hash256 definitionHash,
                                 String targetId, String animationSet) {
        var target = targetId.getBytes(StandardCharsets.UTF_8);
        var setName = animationSet.getBytes(StandardCharsets.UTF_8);
        var input = ByteBuffer.allocate(HASH_DOMAIN.length + Hash256.SIZE * 2
                        + Integer.BYTES + target.length + Integer.BYTES + setName.length)
                .put(HASH_DOMAIN).put(containerId.bytes()).put(definitionHash.bytes())
                .putInt(target.length).put(target)
                .putInt(setName.length).put(setName);
        return ModelHashing.blake3(input.array());
    }

    private static void write(Path file, Hash256 containerId, String targetId,
                              Hash256 inputHash, Iterable<com.elfmcys.ysm.proto.mixel.asset.model.data.Animation> animations)
            throws IOException {
        try (var writer = new BakedAssetWriter();
             var channel = FileChannel.open(file, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writer.setData(containerId, targetId, inputHash, animations);
            writer.write(channel);
            channel.force(true);
        }
    }

    private static boolean validate(Path file, Hash256 containerId,
                                    String targetId, Hash256 inputHash) throws IOException {
        try {
            return open(file).matches(containerId, targetId, inputHash);
        } catch (AssetLoadException error) {
            if (error.reason() == AssetLoadException.Reason.ACCESS) {
                throw error;
            }
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static BakedAssetView open(Path file) throws IOException {
        final FileChannel opened;
        try {
            opened = FileChannel.open(file, StandardOpenOption.READ);
        } catch (IOException error) {
            throw AssetLoadException.access("Failed to open baked animation cache", error);
        }
        try (var channel = opened) {
            return new BakedAssetView(channel);
        } catch (IOException | RuntimeException error) {
            throw AssetLoadException.content("Invalid baked animation cache", error);
        }
    }
}
