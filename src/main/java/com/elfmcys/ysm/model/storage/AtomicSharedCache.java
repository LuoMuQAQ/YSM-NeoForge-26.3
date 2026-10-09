package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.format.AssetLoadException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cross-process, immutable-object writer used by the preview, converted and baked caches. One
 * instance guards one physical store root: targets must stay inside it and its key locks live
 * under {@code <root>/locks}, so caches in different roots never share a lock directory.
 */
public final class AtomicSharedCache {
    private static final ConcurrentHashMap<Path, JvmLock> JVM_LOCKS =
            new ConcurrentHashMap<>();
    private static final String LOCK_SUFFIX = ".lck";

    /** Key-lock directory inside one shared-cache root. */
    static final String LOCK_DIRECTORY = "locks";

    private final Path cacheRoot;
    private final AtomicMove atomicMove;

    public AtomicSharedCache(Path cacheRoot) {
        this(cacheRoot, AtomicSharedCache::moveCommitted);
    }

    AtomicSharedCache(Path cacheRoot, AtomicMove atomicMove) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot")
                .toAbsolutePath().normalize();
        this.atomicMove = Objects.requireNonNull(atomicMove, "atomicMove");
    }

    /** Key-lock directory of one shared-cache root. */
    static Path locksRoot(Path cacheRoot) {
        return cacheRoot.resolve(LOCK_DIRECTORY);
    }

    public void materialize(String namespace, String key, Path target,
                            CacheValidator validator, CacheWriter writer) throws IOException {
        materialize(namespace, key, target, validator, writer, true);
    }

    /** Keeps an old exact object addressable until its verified replacement can commit. */
    public void materializeReplacing(String namespace, String key, Path target,
                                     CacheValidator validator, CacheWriter writer)
            throws IOException {
        materialize(namespace, key, target, validator, writer, false);
    }

    private void materialize(String namespace, String key, Path target,
                             CacheValidator validator, CacheWriter writer,
                             boolean quarantineInvalid) throws IOException {
        target = checkedTarget(target);
        if (isValid(target, validator)) {
            YesSteveModel.LOGGER.debug("Shared cache hit namespace={} key={} target={}",
                    namespace, key, target);
            return;
        }

        var checked = target;
        withKeyLock(namespace, key, () -> {
                if (isValid(checked, validator)) {
                    YesSteveModel.LOGGER.debug(
                            "Shared cache hit after lock namespace={} key={} target={}",
                            namespace, key, checked);
                    return null;
                }
                if (quarantineInvalid && quarantineInvalid(checked)) {
                    YesSteveModel.LOGGER.debug(
                            "Quarantined invalid shared cache object namespace={} key={} target={}",
                            namespace, key, checked);
                }
                Files.createDirectories(checked.getParent());
                var temporary = checked.resolveSibling(checked.getFileName() + ".tmp-"
                        + ProcessHandle.current().pid() + "-" + UUID.randomUUID());
                var startedAt = System.nanoTime();
                try {
                    writer.write(temporary);
                    if (!isValid(temporary, validator)) {
                        throw AssetLoadException.content(
                                "Cache writer produced an invalid object: " + checked);
                    }
                    atomicMove.move(temporary, checked);
                    YesSteveModel.LOGGER.debug(
                            "Materialized shared cache object namespace={} key={} target={} elapsedMs={}",
                            namespace, key, checked,
                            Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return null;
        });
    }

    public <T> T withKeyLock(String namespace, String key, LockedOperation<T> operation)
            throws IOException {
        var lockFile = lockPath(namespace, key);
        Files.createDirectories(lockFile.getParent());
        var jvmLock = acquireJvmLock(lockFile);
        try {
            try (var lockChannel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = lockChannel.lock()) {
                return operation.run();
            }
        } finally {
            releaseJvmLock(lockFile, jvmLock);
        }
    }

    Path checkedTarget(Path target) {
        var checked = target.toAbsolutePath().normalize();
        if (!checked.startsWith(cacheRoot)) {
            throw new IllegalArgumentException("Shared cache target escapes its root: " + target);
        }
        var current = cacheRoot;
        if (Files.isSymbolicLink(current)) {
            throw new IllegalArgumentException("Shared cache root must not be a link: " + current);
        }
        for (var part : cacheRoot.relativize(checked)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(
                        "Shared cache target traverses a link: " + current);
            }
        }
        return checked;
    }

    private Path lockPath(String namespace, String key) {
        var digest = securityDigest((namespace + "\0" + key).getBytes(StandardCharsets.UTF_8));
        return locksRoot(cacheRoot).resolve(sanitize(namespace))
                .resolve(HexFormat.of().formatHex(digest) + LOCK_SUFFIX);
    }

    private static boolean isValid(Path path, CacheValidator validator) throws IOException {
        return Files.isRegularFile(path) && validator.validate(path);
    }

    static boolean quarantineInvalid(Path target) throws IOException {
        if (!Files.exists(target)) {
            return false;
        }
        var quarantine = target.resolveSibling(target.getFileName() + ".corrupt-"
                + Instant.now().toEpochMilli());
        Files.move(target, quarantine, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public static void moveCommitted(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @FunctionalInterface
    public interface CacheValidator {
        boolean validate(Path path) throws IOException;
    }

    @FunctionalInterface
    public interface CacheWriter {
        void write(Path path) throws IOException;
    }

    @FunctionalInterface
    public interface LockedOperation<T> {
        T run() throws IOException;
    }

    @FunctionalInterface
    interface AtomicMove {
        void move(Path temporary, Path target) throws IOException;
    }

    private static byte[] securityDigest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static JvmLock acquireJvmLock(Path path) {
        var value = JVM_LOCKS.compute(path, (ignored, current) -> {
            var result = current == null ? new JvmLock() : current;
            result.users++;
            return result;
        });
        value.lock.lock();
        return value;
    }

    private static void releaseJvmLock(Path path, JvmLock value) {
        value.lock.unlock();
        JVM_LOCKS.compute(path, (ignored, current) -> {
            if (current != value || value.users <= 0) {
                throw new IllegalStateException("Unbalanced shared-cache JVM lock");
            }
            value.users--;
            return value.users == 0 ? null : value;
        });
    }

    private static final class JvmLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }
}
