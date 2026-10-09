package com.elfmcys.ysm.model.catalog;

import com.elfmcys.ysm.format.parser.DefaultAnimationFilter;
import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.schema.file.ChunkDataSource;
import com.elfmcys.ysm.format.schema.model.ModelFileIdentityReader;
import com.elfmcys.ysm.format.schema.model.ModelFileView;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.content.CatalogContentBinding;
import com.elfmcys.ysm.model.catalog.content.ContentBinding;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogAccess;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogCandidate;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogEntry;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogIndexEntry;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogIndexSnapshot;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogPresentation;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogRecord;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.catalog.source.ModelCatalogSource;
import com.elfmcys.ysm.model.catalog.source.ModelSourceResolver;
import com.elfmcys.ysm.model.catalog.source.SourceChangeSet;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.HierarchyPath;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.domain.ModelRepresentation;
import com.elfmcys.ysm.model.domain.ModelScanReport;
import com.elfmcys.ysm.model.storage.ConvertedObjectStore;
import com.elfmcys.ysm.model.storage.ConvertedSourceIndexStore;
import com.elfmcys.ysm.model.storage.ManagedContainer;
import com.elfmcys.ysm.model.storage.ModelExporter;
import com.elfmcys.ysm.model.storage.TestPreviews;
import com.elfmcys.ysm.util.CleanerUtil;
import java.lang.ref.Cleaner;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadableModelCatalogTest {
    @TempDir
    static Path fixtureTemp;

    @TempDir
    Path temp;

    private static Path defaultFixture;
    private static Path firstFixture;
    private static Path secondFixture;

    @BeforeAll
    static void createFixtures() throws Exception {
        defaultFixture = parseFixture(
                "/assets/ysm/builtin/default/ysm.json", "default", true);
        firstFixture = parseFixture(
                "/assets/ysm/builtin/misc/1_alex/ysm.json", "first", false);
        secondFixture = parseFixture(
                "/assets/ysm/builtin/misc/2_steve/ysm.json", "second", false);
    }

    @Test
    void scanningRequiresActivationAndBusyRequestsAreIgnored() throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        Files.copy(firstFixture, root.resolve("first.mxc"));
        try (var fixture = open(root)) {
            assertEquals(ReloadStatus.FAILED,
                    fixture.catalog().reload().join().status());

            var first = fixture.catalog().startScanning();
            assertEquals(ReloadStatus.BUSY,
                    fixture.catalog().reload().join().status());
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (fixture.catalog().current().byModelId().size() != 2
                    && System.nanoTime() < deadline) {
                fixture.catalog().tick();
                Thread.onSpinWait();
            }
            assertEquals(2, fixture.catalog().current().byModelId().size());
            fixture.catalog().watcherChanged(new SourceChangeSet(
                    Set.of(root.resolve("unrelated")), false));

            assertEquals(ReloadStatus.COMMITTED, fixture.finish(first).status());
            assertEquals(2, fixture.catalog().current().byModelId().size());
            assertEquals(1, fixture.catalog().index().entries().size());
            Files.copy(secondFixture, root.resolve("second.mxc"));
            assertEquals(ReloadStatus.COMMITTED,
                    fixture.finish(fixture.catalog().reload()).status());
            assertEquals(3, fixture.catalog().current().byModelId().size());
            assertEquals(2, fixture.catalog().index().entries().size());
        }
    }

    @Test
    void successfulItemsPublishBeforeTheScanTerminal() throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        Files.copy(firstFixture, root.resolve("first.mxc"));
        try (var fixture = open(root)) {
            var completion = fixture.catalog().startScanning();
            var sawPublishedWhileBusy = false;
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!completion.isDone() && System.nanoTime() < deadline) {
                fixture.catalog().tick();
                if (fixture.catalog().busy()
                        && fixture.catalog().current().byModelId().size() == 2) {
                    sawPublishedWhileBusy = true;
                }
                Thread.onSpinWait();
            }

            assertTrue(sawPublishedWhileBusy);
            assertEquals(ReloadStatus.COMMITTED, completion.join().status());
            assertEquals(2, fixture.catalog().current().byModelId().size());
            assertEquals(1, fixture.catalog().index().entries().size());
        }
    }

    @Test
    void conflictingCandidatesPublishOnlyTheAcceptedCandidateInEveryIndex()
            throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        Files.copy(firstFixture, root.resolve("a.mxc"));
        Files.copy(firstFixture, root.resolve("b.mxc"));
        try (var fixture = open(root)) {
            var result = fixture.finish(fixture.catalog().startScanning());

            assertEquals(ReloadStatus.COMMITTED, result.status());
            assertEquals(2, fixture.catalog().current().byModelId().size());
            assertEquals(1, fixture.catalog().index().entries().size());
            var indexed = fixture.catalog().index().entries().get(0);
            var published = fixture.catalog().current().byModelId()
                    .get(indexed.modelId());
            assertEquals(indexed.location(), published.location());
            assertEquals(indexed.identity(),
                    published.binding().content().representation().identity());
            assertEquals(1, fixture.catalog().current().report().errorCount());
        }
    }

    @Test
    void customCandidateCannotEnterTheIndexUnderTheIntrinsicDefaultIdentity()
            throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        Files.copy(defaultFixture, root.resolve("replacement.mxc"));
        try (var fixture = open(root)) {
            var result = fixture.finish(fixture.catalog().startScanning());

            assertEquals(ReloadStatus.COMMITTED, result.status());
            assertEquals(1, fixture.catalog().current().byModelId().size());
            assertTrue(fixture.catalog().index().entries().isEmpty());
            assertEquals(1, fixture.catalog().current().report().errorCount());
        }
    }

    @Test
    void watcherOnlySubmitsReloadFromTheCatalogOwnerTick() throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        try (var fixture = open(root)) {
            assertEquals(ReloadStatus.COMMITTED,
                    fixture.finish(fixture.catalog().startScanning()).status());
            Files.copy(firstFixture, root.resolve("first.mxc"));

            fixture.catalog().watcherChanged(new SourceChangeSet(
                    Set.of(root.resolve("first.mxc")), false));
            assertFalse(fixture.catalog().busy());
            assertEquals(1, fixture.catalog().current().byModelId().size());

            fixture.catalog().tick();
            assertTrue(fixture.catalog().busy());
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (fixture.catalog().busy() && System.nanoTime() < deadline) {
                fixture.catalog().tick();
                Thread.onSpinWait();
            }
            assertFalse(fixture.catalog().busy());
            assertEquals(2, fixture.catalog().current().byModelId().size());
        }
    }

    @Test
    void completeInventoryRetainsObservedPacksAtScanTerminal() throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        Files.writeString(root.resolve("ysm-pack.json"),
                "{\"name\":\"Observed pack\",\"description\":\"kept\"}");
        try (var fixture = open(root)) {
            var completion = fixture.catalog().startScanning();

            assertEquals(ReloadStatus.COMMITTED, fixture.finish(completion).status());
            assertEquals(1, fixture.catalog().current().packs().size());
            assertEquals("Observed pack", fixture.catalog().current().packs().get(0).name());
        }
    }

    @Test
    void failedRootInventoryKeepsPublishedAuthorityAndDoesNotQueueWatcherWork()
            throws Exception {
        var root = Files.writeString(temp.resolve("models"), "not a directory");
        try (var fixture = open(root)) {
            var observations = new AtomicInteger();
            fixture.catalog().subscribe(ignored -> observations.incrementAndGet());
            var scan = fixture.catalog().startScanning();
            fixture.catalog().watcherChanged(new SourceChangeSet(
                    Set.of(root), false));

            assertEquals(ReloadStatus.FAILED, fixture.finish(scan).status());
            assertEquals(1, fixture.catalog().current().byModelId().size());
            assertFalse(fixture.catalog().busy());
            assertTrue(observations.get() >= 1);
        }
    }

    @Test
    void closeDiscardsLateCandidateAndFailsTheAcceptedScan() throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        Files.copy(firstFixture, root.resolve("first.mxc"));
        try (var fixture = open(root)) {
            var observations = new AtomicInteger();
            fixture.catalog().subscribe(ignored -> observations.incrementAndGet());
            var continuation = fixture.catalog().startScanning();

            fixture.catalog().close();
            fixture.catalog().close();

            assertEquals(ReloadStatus.FAILED, continuation.join().status());
            assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(thread ->
                    thread.isAlive() && thread.getName().startsWith("YSM Catalog Scan ")));
            assertTrue(fixture.catalog().current().byModelId().isEmpty());
            assertEquals(1, observations.get());
            assertEquals(ReloadStatus.FAILED, fixture.catalog().reload().join().status());
        }
    }

    @Test
    void observerFailureCannotRollbackCommitOrBlockAnotherObserver() throws Exception {
        var root = Files.createDirectories(temp.resolve("models"));
        try (var fixture = open(root)) {
            assertEquals(ReloadStatus.COMMITTED,
                    fixture.finish(fixture.catalog().startScanning()).status());
            fixture.catalog().subscribe(ignored -> {
                throw new IllegalStateException("observer failed");
            });
            var observations = new AtomicInteger();
            fixture.catalog().subscribe(ignored -> observations.incrementAndGet());
            Files.copy(firstFixture, root.resolve("first.mxc"));

            var reload = fixture.catalog().reload();
            fixture.finish(reload);

            assertEquals(ReloadStatus.COMMITTED, reload.join().status());
            assertEquals(2, fixture.catalog().current().byModelId().size());
            assertEquals(2, observations.get());
        }
    }

    @Test
    void bindingViewKeepsExactOldContentUntilDeterministicCleanupEvent() {
        assertFalse(AutoCloseable.class.isAssignableFrom(ContentBinding.class));
        assertFalse(AutoCloseable.class.isAssignableFrom(ModelContent.class));

        var cleaned = new AtomicInteger();
        TestContent first = new TestContent(hash(3), cleaned);
        var second = new TestContent(hash(3), new AtomicInteger());
        CatalogSnapshot firstSnapshot = snapshot(first, "same");
        var current = new AtomicReference<>(candidate(firstSnapshot));
        ModelContent resourceReference = current.get().snapshot().binding(first.modelId())
                .orElseThrow().content();

        assertSame(first, resourceReference);
        current.set(candidate(snapshot(second, "same")));
        var currentContent = current.get().snapshot().binding(second.modelId())
                .orElseThrow().content();
        assertSame(second, currentContent);
        assertNotSame(resourceReference, currentContent);
        assertEquals(first.modelId(), resourceReference.modelId());
        assertEquals(0, cleaned.get());

        var cleanupEvent = first.cleanupEvent();
        resourceReference = null;
        firstSnapshot = null;
        first = null;
        cleanupEvent.clean();

        assertEquals(1, cleaned.get());
    }

    private CatalogFixture open(Path root) throws Exception {
        var cacheRoot = temp.resolve("cache");
        var resolver = new ModelSourceResolver(new RawModelImporter(
                DefaultAnimationFilter.keepAll()),
                new ConvertedSourceIndexStore(cacheRoot, "catalog-owner-test"),
                new ConvertedObjectStore(cacheRoot));
        var intrinsicDefault = openIndexed(defaultFixture,
                new CatalogModelLocation(CatalogRootKind.BUILTIN,
                        new ModelPath("default")));
        var reconciler = new CatalogReconciler(List.of(new ModelCatalogSource(
                CatalogRootKind.CUSTOM, root, false)), resolver,
                Set.of(intrinsicDefault.modelId()), intrinsicDefault,
                CatalogIndexSnapshot.empty());
        var catalog = new ReloadableModelCatalog(
                reconciler, reconciler.materializeBuiltins());
        return new CatalogFixture(catalog);
    }

    private static Path parseFixture(String resource, String name, boolean builtin)
            throws Exception {
        var manifest = ReloadableModelCatalogTest.class.getResource(resource);
        var source = Path.of(Objects.requireNonNull(manifest).toURI()).getParent();
        try (var vfs = new Directory(source)) {
            var output = Files.createDirectories(fixtureTemp.resolve(name));
            var parsed = builtin
                    ? ModelParser.parseBuiltinDefault(vfs, output)
                    : ModelParser.parse(vfs, output, DefaultAnimationFilter.keepAll());
            if (builtin) return parsed;
            var location = new CatalogModelLocation(CatalogRootKind.CUSTOM,
                    new ModelPath(name));
            var content = ManagedContainer.openDirect(parsed, location);
            try {
                return ModelExporter.export(content,
                        TestPreviews.blank(),
                        output.resolve(name + "-portable.mxc"), null);
            } finally {
                content.representation().close();
            }
        }
    }

    private static ManagedContainer openIndexed(Path file, CatalogModelLocation location)
            throws Exception {
        return ManagedContainer.openIndexed(new CatalogIndexEntry(
                identity(file), location, file));
    }

    private static ModelFileIdentity identity(Path file)
            throws Exception {
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return ModelFileIdentityReader.read(channel);
        }
    }

    private static CatalogSnapshot snapshot(TestContent content, String path) {
        var location = new CatalogModelLocation(
                CatalogRootKind.CUSTOM, new ModelPath(path));
        var binding = new CatalogContentBinding(content.modelId(), content);
        return new CatalogSnapshot(Map.of(content.modelId(),
                new CatalogRecord(new CatalogEntry(content.modelId(),
                        new HierarchyPath(path),
                        CatalogAccess.PUBLIC, CatalogPresentation.empty(path)),
                        location, binding)), List.of(), ModelScanReport.empty());
    }

    private static CatalogCandidate candidate(CatalogSnapshot snapshot) {
        return new CatalogCandidate(new CatalogIndexSnapshot(
                List.of(), snapshot.packs(), snapshot.report()), snapshot);
    }

    private static Hash256 hash(int seed) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) seed;
        return new Hash256(bytes);
    }

    private record CatalogFixture(ReloadableModelCatalog catalog)
            implements AutoCloseable {
        private ReloadResult finish(CompletableFuture<ReloadResult> completion)
                throws Exception {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!completion.isDone() && System.nanoTime() < deadline) {
                catalog.tick();
                Thread.onSpinWait();
            }
            return completion.get(1, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            catalog.close();
        }
    }

    private static final class TestContent implements ModelContent {
        private final Hash256 modelId;
        private final Cleaner.Cleanable cleanupEvent;

        private TestContent(Hash256 modelId, AtomicInteger cleaned) {
            this.modelId = modelId;
            cleanupEvent = CleanerUtil.ref(this, cleaned,
                    value -> value.incrementAndGet());
        }

        @Override
        public Hash256 modelId() {
            return modelId;
        }

        @Override
        public ModelFileView modelFile() {
            return null;
        }

        @Override
        public ModelRepresentation representation() {
            return null;
        }

        @Override
        public ChunkDataSource chunks() {
            return null;
        }

        private Cleaner.Cleanable cleanupEvent() {
            return cleanupEvent;
        }
    }
}
