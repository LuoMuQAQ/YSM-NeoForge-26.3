package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.AssetPaths;

import java.nio.file.Path;
import java.util.Objects;

/** Process composition for management-owned model storage. */
public final class ModelStorageInfra {
    private final Path gameCacheRoot;
    private final AtomicSharedCache cache;
    private final ConvertedObjectStore objects;
    private final ConvertedSourceIndexStore indexes;
    private final ConvertedCacheCoordinator convertedConsumers;

    private ModelStorageInfra(Path gameCacheRoot, String fullModVersion) {
        this.gameCacheRoot = Objects.requireNonNull(gameCacheRoot, "gameCacheRoot");
        cache = new AtomicSharedCache(gameCacheRoot);
        objects = new ConvertedObjectStore(gameCacheRoot, cache);
        indexes = new ConvertedSourceIndexStore(gameCacheRoot, fullModVersion);
        convertedConsumers = new ConvertedCacheCoordinator(gameCacheRoot);
    }

    public static ModelStorageInfra openDefault(String fullModVersion) {
        return new ModelStorageInfra(AssetPaths.gameCacheRoot(), fullModVersion);
    }

    /** Game-local cache root shared by converted content and the independent preview cache. */
    public Path gameCacheRoot() {
        return gameCacheRoot;
    }

    /** Converted content inside the game-local cache root. */
    public Path convertedRoot() {
        return AssetPaths.convertedRoot(gameCacheRoot);
    }

    public AtomicSharedCache cache() {
        return cache;
    }

    public ConvertedObjectStore objects() {
        return objects;
    }

    public ConvertedSourceIndexStore indexes() {
        return indexes;
    }

    public ConvertedCacheCoordinator convertedConsumers() {
        return convertedConsumers;
    }
}
