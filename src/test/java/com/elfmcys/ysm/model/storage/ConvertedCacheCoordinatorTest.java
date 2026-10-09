package com.elfmcys.ysm.model.storage;

import com.elfmcys.ysm.AssetPaths;
import com.elfmcys.ysm.model.domain.Hash256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertedCacheCoordinatorTest {
    @TempDir
    Path temp;

    @Test
    void activePeerMakesTheOnlyPruneOpportunityASafeSkip() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var retained = object(cacheRoot, 1, 11);
        var stale = object(cacheRoot, 2, 12);
        try (var first = new ConvertedCacheCoordinator(cacheRoot);
             var peer = new ConvertedCacheCoordinator(cacheRoot)) {
            first.register();
            peer.register();

            assertEquals(ConvertedCacheCoordinator.PruneResult.SKIPPED_ACTIVE_CONSUMER,
                    first.pruneOnce(Set.of(retained)));
            assertTrue(Files.isRegularFile(retained));
            assertTrue(Files.isRegularFile(stale));
        }
    }

    @Test
    void soleConsumerDeletesOnlyUnretainedConvertedObjects() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var retained = object(cacheRoot, 3, 13);
        var stale = object(cacheRoot, 4, 14);
        var unrelated = Files.writeString(
                AssetPaths.convertedRoot(cacheRoot).resolve("unrelated.txt"), "keep");
        try (var coordinator = new ConvertedCacheCoordinator(cacheRoot)) {
            coordinator.register();

            assertEquals(ConvertedCacheCoordinator.PruneResult.COMPLETED,
                    coordinator.pruneOnce(Set.of(retained)));
            assertTrue(Files.isRegularFile(retained));
            assertFalse(Files.exists(stale));
            assertTrue(Files.isRegularFile(unrelated));
        }
    }

    @Test
    void pruneOpportunityCannotBeRetriedAfterAnyTerminal() throws Exception {
        var cacheRoot = temp.resolve("cache");
        try (var coordinator = new ConvertedCacheCoordinator(cacheRoot)) {
            coordinator.register();
            assertEquals(ConvertedCacheCoordinator.PruneResult.COMPLETED,
                    coordinator.pruneOnce(Set.of()));
            assertThrows(IllegalStateException.class,
                    () -> coordinator.pruneOnce(Set.of()));
        }
    }

    @Test
    void registrationIsIdempotentAndCloseRemovesItsRegistration() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var coordinator = new ConvertedCacheCoordinator(cacheRoot);
        coordinator.register();
        coordinator.register();
        assertTrue(coordinator.registered());

        coordinator.close();
        coordinator.close();

        assertFalse(coordinator.registered());
        try (var files = Files.list(cacheRoot.resolve("locks/converted-consumers"))) {
            assertEquals(0, files.filter(path -> path.getFileName().toString()
                    .endsWith(".consumer")).count());
        }
    }

    @Test
    void pruneDoesNotFollowDirectoriesOutsideTheConvertedRoot() throws Exception {
        var cacheRoot = temp.resolve("cache");
        var outside = Files.createDirectories(temp.resolve("outside"));
        var outsideObject = Files.write(outside.resolve("outside.mxc"),
                new byte[]{7, 8, 9});
        Files.createDirectories(AssetPaths.convertedRoot(cacheRoot));
        Files.createSymbolicLink(AssetPaths.convertedRoot(cacheRoot).resolve("linked"), outside);
        try (var coordinator = new ConvertedCacheCoordinator(cacheRoot)) {
            coordinator.register();

            assertEquals(ConvertedCacheCoordinator.PruneResult.COMPLETED,
                    coordinator.pruneOnce(Set.of()));
            assertTrue(Files.isRegularFile(outsideObject));
        }
    }

    private static Path object(Path cacheRoot, int model, int container)
            throws Exception {
        var path = new ConvertedObjectStore(cacheRoot)
                .objectPath(hash(model), hash(container));
        Files.createDirectories(path.getParent());
        return Files.write(path, new byte[]{1, 2, 3});
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) marker;
        return new Hash256(bytes);
    }
}
