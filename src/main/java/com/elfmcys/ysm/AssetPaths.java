// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Roots of every asset and model store the mod owns.
 *
 * <p>This class declares the game/config roots, the mod-owned model directories, the game-local
 * cache root with its store directories, the platform-level cache root shared by the remote and
 * baked caches together with its game-local fallback, the export root, and the resolve rules
 * between them. It stops at the store root: directories inside one store, the partitioning below them and
 * the individual file names stay with the store that writes and reads them. Layout-taking methods
 * accept their root so callers can keep injecting a temporary root instead of reaching for the
 * process default.
 *
 * <p>Paths that are not asset storage stay with their own owner; native library extraction, for
 * example, is defined by {@link com.elfmcys.ysm.util.NativeLibUtil}.
 */
public final class AssetPaths {
    /** Directory of the mod inside the game and config directories. */
    private static final String MOD_DIRECTORY = YesSteveModel.MOD_ID;

    private static final String MODEL_DIRECTORY = "ysm";
    private static final String EXPORT_DIRECTORY = "export";
    private static final String CUSTOM_MODEL_DIRECTORY = "custom";
    private static final String AUTH_MODEL_DIRECTORY = "auth";
    private static final String BUILTIN_MODEL_DIRECTORY = "builtin";

    private static final String BAKE_DIRECTORY = "bake";
    private static final String REMOTE_DIRECTORY = "remote";

    /** Cache directory below the mod-owned directory; both cache roots end in {@code ysm/cache}. */
    private static final String CACHE_DIRECTORY = "cache";
    private static final String CONVERTED_DIRECTORY = "converted";
    private static final String BUILTIN_INDEX_FILE = "builtin-index.json";

    /** Jar filesystems opened for catalog walks. Left open for the process. */
    private static final ConcurrentHashMap<Path, FileSystem> OPEN_JARS = new ConcurrentHashMap<>();

    private AssetPaths() {
    }

    // ---------------------------------------------------------------- roots

    /** Forge game directory. */
    public static Path gameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    /** Forge config directory. */
    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    /** Mod-owned directory inside the game directory. */
    public static Path gameModelsRoot() {
        return gameModelsRoot(gameDirectory());
    }

    /** Mod-owned directory inside the given game directory. */
    public static Path gameModelsRoot(Path gameDirectory) {
        return gameDirectory.resolve(MOD_DIRECTORY);
    }

    /** Mod-owned directory inside the config directory; owns the export root. */
    public static Path configRoot() {
        return configDirectory().resolve(MOD_DIRECTORY);
    }

    /** Game-local cache root of the current instance; owns the converted store. */
    public static Path gameCacheRoot() {
        return gameCacheRoot(gameDirectory());
    }

    /** Game-local cache root of one game directory: {@code <game-dir>/ysm/cache}. */
    public static Path gameCacheRoot(Path gameDirectory) {
        return gameModelsRoot(gameDirectory).resolve(CACHE_DIRECTORY);
    }

    /** Root of explicit direct-container export. */
    public static Path exportRoot() {
        return configRoot().resolve(EXPORT_DIRECTORY);
    }

    // -------------------------------------------------------- local catalog

    /** User-supplied model sources. */
    public static Path customModelsRoot() {
        return customModelsRoot(gameModelsRoot());
    }

    /** User-supplied model sources under the given mod-owned directory. */
    public static Path customModelsRoot(Path gameModelsRoot) {
        return gameModelsRoot.resolve(CUSTOM_MODEL_DIRECTORY);
    }

    /** Server-issued model sources. */
    public static Path authModelsRoot() {
        return authModelsRoot(gameModelsRoot());
    }

    /** Server-issued model sources under the given mod-owned directory. */
    public static Path authModelsRoot(Path gameModelsRoot) {
        return gameModelsRoot.resolve(AUTH_MODEL_DIRECTORY);
    }

    /** Builtin models embedded in the mod jar. */
    public static Path builtinModelsResource() {
        return modResource(BUILTIN_MODEL_DIRECTORY);
    }

    /** Builtin model index embedded in the mod jar. */
    public static Path builtinIndexResource() {
        return modResource(BUILTIN_INDEX_FILE);
    }

    /**
     * Resolves {@code assets/<mod-id>/<relative>} inside the loaded mod file.
     * A directory root is used directly. A jar root is mounted and left open so later
     * catalog walks can keep the returned path.
     */
    private static Path modResource(String relativeFile) {
        var relative = "assets/" + MOD_DIRECTORY + "/" + relativeFile;
        var modFile = ModList.get().getModFileById(MOD_DIRECTORY);
        if (modFile == null) {
            throw new IllegalStateException("Mod file is not loaded: " + MOD_DIRECTORY);
        }
        var contents = modFile.getFile().getContents();
        for (Path root : contents.getContentRoots()) {
            var candidate = resolveResource(root, relative);
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }
        var fallback = resolveResource(contents.getPrimaryPath(), relative);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No content root for " + relative);
    }

    private static Path resolveResource(Path root, String relative) {
        try {
            if (Files.isDirectory(root)) {
                return root.resolve(relative);
            }
            if (!Files.isRegularFile(root)) {
                return null;
            }
            return jarFileSystem(root).getPath(relative);
        } catch (IOException failure) {
            return null;
        }
    }

    private static FileSystem jarFileSystem(Path jar) throws IOException {
        var key = jar.toAbsolutePath().normalize();
        var open = OPEN_JARS.get(key);
        if (open != null && open.isOpen()) {
            return open;
        }
        synchronized (OPEN_JARS) {
            open = OPEN_JARS.get(key);
            if (open != null && open.isOpen()) {
                return open;
            }
            var uri = URI.create("jar:" + key.toUri());
            try {
                open = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException alreadyOpen) {
                open = FileSystems.getFileSystem(uri);
            }
            OPEN_JARS.put(key, open);
            return open;
        }
    }

    // --------------------------------------------------------- game cache

    /** Converted store inside the game-local cache root. */
    public static Path convertedRoot(Path gameCacheRoot) {
        return gameCacheRoot.resolve(CONVERTED_DIRECTORY);
    }

    // ---------------------------------------------------- platform cache

    /**
     * Platform-level cache root shared by remote and baked content, or {@code null} when the
     * platform has no such location.
     *
     * <p>It is the {@code ysm/cache} directory of the platform application data location:
     * {@code %LOCALAPPDATA%} on Windows, the user Library Application Support on macOS and
     * {@code ~/.local/share} elsewhere.
     */
    public static Path platformCacheRoot() {
        var home = System.getProperty("user.home");
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            var local = System.getenv("LOCALAPPDATA");
            return local == null || local.isBlank()
                    ? null
                    : Path.of(local, MODEL_DIRECTORY, CACHE_DIRECTORY);
        }
        if (home == null || home.isBlank()) {
            return null;
        }
        return os.contains("mac")
                ? Path.of(home, "Library", "Application Support", MODEL_DIRECTORY, CACHE_DIRECTORY)
                : Path.of(home, ".local", "share", MODEL_DIRECTORY, CACHE_DIRECTORY);
    }

    /**
     * Remote cache root: the platform-level location when it can be created and written, game-local
     * otherwise.
     */
    public static Path remoteRoot(Path gameDirectory) {
        return sharedContentRoot(gameDirectory, REMOTE_DIRECTORY, platformCacheRoot());
    }

    /** Game-local baked render cache root: {@code <game-dir>/ysm/cache/bake}. */
    public static Path bakedRoot(Path gameDirectory) {
        return gameCacheRoot(gameDirectory).resolve(BAKE_DIRECTORY);
    }

    /**
     * Resolves one shared content directory under {@code platformRoot}, falling back to the
     * game-local cache root {@code <game-dir>/ysm/cache}. The platform directory is created to
     * prove it is usable.
     */
    static Path sharedContentRoot(Path gameDirectory, String contentDirectory, Path platformRoot) {
        if (platformRoot != null) {
            var preferred = platformRoot.resolve(contentDirectory);
            if (isWritableDirectory(preferred)) {
                return preferred;
            }
        }
        return gameCacheRoot(gameDirectory).resolve(contentDirectory);
    }

    private static boolean isWritableDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.isWritable(directory);
        } catch (IOException | RuntimeException unusable) {
            return false;
        }
    }
}
