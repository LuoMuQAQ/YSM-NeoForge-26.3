package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.AssetPaths;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Process-lifetime converted consumer registration and one-shot prune admission. */
public final class ConvertedCacheCoordinator implements AutoCloseable {
    private static final String GATE_FILE = "admission.lck";
    private static final String CONSUMER_SUFFIX = ".consumer";

    /** Cross-process registration directory for live converted consumers. */
    private static final String CONSUMER_DIRECTORY = "converted-consumers";

    private final Path gameCacheRoot;
    private final Path coordinationRoot;
    private final Path gateFile;
    private FileChannel registrationChannel;
    private FileLock registrationLock;
    private Path registrationFile;
    private boolean pruneAttempted;

    public ConvertedCacheCoordinator(Path gameCacheRoot) {
        this.gameCacheRoot = Objects.requireNonNull(gameCacheRoot, "gameCacheRoot");
        coordinationRoot = AtomicSharedCache.locksRoot(gameCacheRoot).resolve(CONSUMER_DIRECTORY);
        gateFile = coordinationRoot.resolve(GATE_FILE);
    }

    /** Waits only for a finite prune window, then registers until process shutdown. */
    public synchronized void register() throws IOException {
        if (registrationLock != null) {
            return;
        }
        Files.createDirectories(coordinationRoot);
        try (var gate = FileChannel.open(gateFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE);
             var ignored = gate.lock(0, Long.MAX_VALUE, true)) {
            var file = coordinationRoot.resolve(UUID.randomUUID() + CONSUMER_SUFFIX);
            var channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            try {
                var lock = channel.lock();
                registrationFile = file;
                registrationChannel = channel;
                registrationLock = lock;
            } catch (IOException | RuntimeException | Error failure) {
                channel.close();
                Files.deleteIfExists(file);
                throw failure;
            }
        }
    }

    public synchronized boolean registered() {
        return registrationLock != null && registrationLock.isValid();
    }

    /**
     * Tries the only legal prune opportunity. Active peer consumers make the attempt a
     * permanent skip; stale registrations are removed while admission is closed.
     */
    public synchronized PruneResult pruneOnce(Set<Path> retainedObjects) {
        Objects.requireNonNull(retainedObjects, "retainedObjects");
        if (!registered()) {
            throw new IllegalStateException("Converted consumer is not registered");
        }
        if (pruneAttempted) {
            throw new IllegalStateException("Converted prune opportunity was already consumed");
        }
        pruneAttempted = true;
        var retained = retainedObjects.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
        try {
            Files.createDirectories(coordinationRoot);
            try (var gate = FileChannel.open(gateFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = gate.lock()) {
                if (hasActivePeer()) {
                    return PruneResult.SKIPPED_ACTIVE_CONSUMER;
                }
                pruneObjects(retained);
                return PruneResult.COMPLETED;
            }
        } catch (IOException | RuntimeException failure) {
            return PruneResult.FAILED;
        }
    }

    private boolean hasActivePeer() throws IOException {
        try (var registrations = Files.list(coordinationRoot)) {
            for (var file : registrations
                    .filter(path -> path.getFileName().toString()
                            .endsWith(CONSUMER_SUFFIX))
                    .sorted().toList()) {
                if (file.equals(registrationFile)) {
                    continue;
                }
                try (var channel = FileChannel.open(file,
                        StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    FileLock lock = null;
                    try {
                        lock = channel.tryLock();
                    } catch (OverlappingFileLockException activeInThisJvm) {
                        return true;
                    }
                    if (lock == null) {
                        return true;
                    }
                    lock.close();
                }
                Files.deleteIfExists(file);
            }
        }
        return false;
    }

    private void pruneObjects(Set<Path> retained) throws IOException {
        var root = AssetPaths.convertedRoot(gameCacheRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return;
        }
        var convertedTemporary = root.resolve(ConvertedObjectStore.TEMPORARY_DIRECTORY);
        try (var modelDirectories = Files.list(root)) {
            for (var directory : modelDirectories
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                if (directory.equals(convertedTemporary)) {
                    continue;
                }
                try (var objects = Files.list(directory)) {
                    for (var object : objects
                            .filter(path -> Files.isRegularFile(
                                    path, LinkOption.NOFOLLOW_LINKS))
                            .filter(path -> path.getFileName().toString()
                                    .endsWith(ConvertedObjectStore.CONTAINER_SUFFIX))
                            .sorted(Comparator.comparing(Path::toString)).toList()) {
                        var checked = object.toAbsolutePath().normalize();
                        if (checked.getParent().getParent().equals(root)
                                && !retained.contains(checked)) {
                            Files.deleteIfExists(checked);
                        }
                    }
                }
                try (var remaining = Files.list(directory)) {
                    if (remaining.findAny().isEmpty()) {
                        Files.deleteIfExists(directory);
                    }
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        var lock = registrationLock;
        var channel = registrationChannel;
        var file = registrationFile;
        registrationLock = null;
        registrationChannel = null;
        registrationFile = null;
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
        }
    }

    public enum PruneResult {
        COMPLETED,
        SKIPPED_ACTIVE_CONSUMER,
        FAILED
    }
}
