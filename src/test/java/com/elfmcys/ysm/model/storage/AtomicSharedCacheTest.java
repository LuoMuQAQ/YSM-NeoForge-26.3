package com.elfmcys.ysm.model.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicSharedCacheTest {
    @TempDir
    Path temp;

    @Test
    void relativeRootsAreResolvedBeforeContainmentChecks() {
        var cacheRoot = Path.of("build", "relative-cache-root");
        var target = cacheRoot.resolve("objects/value.bin");

        var checked = new AtomicSharedCache(cacheRoot).checkedTarget(target);

        assertFalse(cacheRoot.isAbsolute());
        assertEquals(target.toAbsolutePath().normalize(), checked);
        assertThrows(IllegalArgumentException.class, () ->
                new AtomicSharedCache(cacheRoot).checkedTarget(Path.of("outside.bin")));
    }

    @Test
    void keyLocksLiveUnderTheCacheRootsOwnLockDirectory() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var observed = new AtomicReference<Path>();

        new AtomicSharedCache(cacheRoot).withKeyLock("namespace", "key", () -> {
            try (var files = Files.walk(cacheRoot.resolve("locks"))) {
                observed.set(files.filter(Files::isRegularFile).findFirst().orElseThrow());
            }
            return null;
        });

        assertEquals(cacheRoot.resolve("locks").resolve("namespace"), observed.get().getParent());
        assertTrue(observed.get().getFileName().toString().endsWith(".lck"));
    }
}
