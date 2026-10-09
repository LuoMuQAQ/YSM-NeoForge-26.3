package com.elfmcys.ysm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPathsTest {
    @TempDir
    Path temp;

    @Test
    void gameCacheRootOwnsTheConvertedStore() {
        var gameCacheRoot = temp.resolve("game/ysm/cache");

        assertEquals(gameCacheRoot.resolve("converted"),
                AssetPaths.convertedRoot(gameCacheRoot));
    }

    @Test
    void localCatalogDirectoriesShareOneModOwnedDirectory() {
        var gameModelsRoot = temp.resolve("game/dir/ysm");

        assertEquals(gameModelsRoot.resolve("custom"),
                AssetPaths.customModelsRoot(gameModelsRoot));
        assertEquals(gameModelsRoot.resolve("auth"),
                AssetPaths.authModelsRoot(gameModelsRoot));
    }

    @Test
    void managementRootsKeepTheirDocumentedPlacement() {
        var gameDirectory = temp.resolve("game");

        assertEquals(gameDirectory.resolve("ysm"),
                AssetPaths.gameModelsRoot(gameDirectory));
        assertEquals(gameDirectory.resolve("ysm/cache"),
                AssetPaths.gameCacheRoot(gameDirectory));
        assertEquals(gameDirectory.resolve("ysm/cache/bake"),
                AssetPaths.bakedRoot(gameDirectory));
    }

    @Test
    void sharedContentPrefersTheWritablePlatformRoot() {
        var gameDirectory = temp.resolve("game");
        var platformRoot = temp.resolve("platform");

        var resolved = AssetPaths.sharedContentRoot(
                gameDirectory, "remote", platformRoot);

        assertEquals(platformRoot.resolve("remote"), resolved);
        assertTrue(Files.isDirectory(resolved));
    }

    @Test
    void sharedContentFallsBackToTheGameCacheWhenThePlatformRootIsUnusable() throws Exception {
        var gameDirectory = temp.resolve("game");
        var blocked = Files.createDirectories(temp.resolve("platform"));
        Files.writeString(blocked.resolve("remote"), "not a directory");

        assertEquals(gameDirectory.resolve("ysm/cache/remote"),
                AssetPaths.sharedContentRoot(gameDirectory, "remote", blocked));
    }
}
