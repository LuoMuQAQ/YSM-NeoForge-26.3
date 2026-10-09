package com.elfmcys.ysm.model.service;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.AssetLoadException;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.schema.file.ChunkDataSource;
import com.elfmcys.ysm.format.schema.model.ModelFileView;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.ReloadableModelCatalog;
import com.elfmcys.ysm.model.catalog.builtin.BuiltinModelCatalog;
import com.elfmcys.ysm.model.catalog.builtin.BuiltinModelIndex;
import com.elfmcys.ysm.model.catalog.builtin.BuiltinModelMaterializer;
import com.elfmcys.ysm.model.catalog.client.ClientCatalogManager;
import com.elfmcys.ysm.model.catalog.content.DefaultAnimationKey;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.catalog.source.ModelCatalogSource;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.domain.RenderTargetIds;
import com.elfmcys.ysm.model.resource.RuntimeContentStore;
import com.elfmcys.ysm.model.resource.client.AnimationStore;
import com.elfmcys.ysm.model.resource.client.ClientModelRenderTargetManager;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.resource.client.PlayerModelResources;
import com.elfmcys.ysm.model.resource.client.RenderTargetResources;
import com.elfmcys.ysm.model.resource.client.asset.ClientAssetRepository;
import com.elfmcys.ysm.model.resource.client.render.BakedAnimationCache;
import com.elfmcys.ysm.model.resource.client.render.BakedModelCache;
import com.elfmcys.ysm.model.resource.client.render.DefaultAnimationRuntime;
import com.elfmcys.ysm.model.resource.client.render.ModelRenderTargetLoader;
import com.elfmcys.ysm.model.storage.AtomicSharedCache;
import com.elfmcys.ysm.model.storage.ManagedContainer;
import com.elfmcys.ysm.model.storage.ModelStorageInfra;
import com.elfmcys.ysm.model.storage.PreviewStore;
import com.elfmcys.ysm.util.UnsafeUtil;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelBootstrapTest {
    @TempDir
    static Path fixtureTemp;

    @TempDir
    Path temp;

    private static Path defaultContainer;
    private static ManagedContainer defaultFixture;
    private static BuiltinModelIndex defaultContract;

    @BeforeAll
    static void createDefaultFixture() throws Exception {
        var manifest = ClientModelBootstrapTest.class.getResource(
                "/assets/ysm/builtin/default/ysm.json");
        var source = Path.of(Objects.requireNonNull(manifest).toURI()).getParent();
        try (var directory = new Directory(source)) {
            defaultContainer = ModelParser.parseBuiltinDefault(
                    directory, Files.createDirectories(fixtureTemp.resolve("model")));
        }
        defaultFixture = ManagedContainer.openResidentDefault(
                Files.readAllBytes(defaultContainer), defaultLocation());
        var materialized = BuiltinModelMaterializer.materialize(defaultFixture);
        defaultContract = BuiltinModelIndex.of(
                Map.of(new ModelPath("default"), materialized.modelHash()),
                materialized.animationHashes(), Map.of());
    }

    @AfterEach
    void clearServiceReference() {
        var current = ClientModelService.current().orElse(null);
        setInstance(null);
        if (current != null) {
            current.close();
        }
    }

    @Test
    void completeRequiredProductionStagesPrecedeEveryExternalServiceReference()
            throws Exception {
        try (var fixture = BootstrapFixture.open(temp, Stage.SUCCESS)) {
            var requiredComplete = new AtomicBoolean();
            var result = ClientModelService.publishRequired(
                    () -> fixture.service,
                    service -> {
                        assertExternalReferencesEmpty();
                        fixture.required.join();
                        assertSame(fixture.manager.defaultRenderTarget(),
                                service.defaultRenderTarget());
                        assertFalse(service.catalog().models().isEmpty());
                        requiredComplete.set(true);
                    },
                    service -> {
                        assertExternalReferencesEmpty();
                        assertTrue(requiredComplete.get());
                        setInstance(service);
                    });

            assertSame(fixture.service, result);
            assertSame(result, ClientModelService.current().orElseThrow());
            assertSame(result, ClientModelService.instance());
            assertSame(fixture.manager.defaultRenderTarget(), result.defaultRenderTarget());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Stage.class, names = "SUCCESS", mode = EnumSource.Mode.EXCLUDE)
    void eachRequiredProductionStageFailurePropagatesExactlyWithoutPublication(Stage stage)
            throws Exception {
        try (var fixture = BootstrapFixture.open(temp, stage)) {
            assertExternalReferencesEmpty();
            var stageCause = terminalCause(fixture.required);
            assertStageCause(stage, stageCause);
            if (stage == Stage.ANIMATIONS) {
                var player = defaultFixture.view()
                        .requireRenderTarget(RenderTargetIds.PLAYER).descriptor();
                assertNull(fixture.animations.fallback(
                        DefaultAnimationKey.domain(player, "main")));
                assertSame(fixture.conflictingAnimation, fixture.animations.fallback(
                        DefaultAnimationKey.domain(player, "fp_arm")));
            }
            var published = new AtomicBoolean();

            var thrown = assertThrows(Throwable.class, () ->
                    ClientModelService.publishRequired(
                            () -> fixture.service,
                            ignored -> fixture.required.join(),
                            ignored -> published.set(true)));

            assertSame(stageCause, thrown);
            assertFalse(published.get());
            assertExternalReferencesEmpty();
            assertTrue(fixture.workers.isTerminated());
            assertEquals(1, fixture.workers.shutdownCalls.get());
            assertThrows(IllegalStateException.class,
                    fixture.manager::defaultRenderTarget);
        }
    }

    private static void assertStageCause(Stage stage, Throwable cause) {
        switch (stage) {
            case PARSE -> {
                assertTrue(cause instanceof AssetLoadException);
                assertEquals("required parse failed", cause.getMessage());
            }
            case BAKE -> {
                assertTrue(cause instanceof AssetLoadException);
                assertEquals("required bake failed", cause.getMessage());
            }
            case DEFAULT_TARGET -> {
                assertTrue(cause instanceof IllegalStateException);
                assertEquals("Builtin default model contains no player render target",
                        cause.getMessage());
            }
            case ANIMATIONS -> {
                assertTrue(cause instanceof IllegalStateException);
                assertTrue(cause.getMessage().startsWith(
                        "Conflicting default animation domain:"));
            }
            case SUCCESS -> throw new AssertionError("Success has no failure cause");
        }
    }

    private static Throwable terminalCause(CompletableFuture<Void> terminal) {
        var error = assertThrows(CompletionException.class, terminal::join);
        return unwrap(error);
    }

    private static void assertExternalReferencesEmpty() {
        assertTrue(ClientModelService.current().isEmpty());
        assertThrows(IllegalStateException.class, ClientModelService::instance);
    }

    private static Throwable unwrap(Throwable error) {
        var current = error;
        while ((current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static CatalogModelLocation defaultLocation() {
        return new CatalogModelLocation(
                CatalogRootKind.BUILTIN, new ModelPath("default"));
    }

    private static void setInstance(ClientModelService service) {
        setField(ClientModelService.class, null, "INSTANCE", service);
    }

    private static void setField(Object owner, String name, Object value) {
        setField(owner.getClass(), owner, name, value);
    }

    private static void setField(Class<?> type, Object owner, String name, Object value) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private enum Stage {
        SUCCESS,
        PARSE,
        BAKE,
        DEFAULT_TARGET,
        ANIMATIONS
    }

    private static final class BootstrapFixture implements AutoCloseable {
        private final Stage stage;
        private final CountingExecutor workers;
        private final BuiltinModelCatalog builtins;
        private final ClientModelRenderTargetManager manager;
        private final ClientModelService service;
        private final CompletableFuture<Void> required;
        private final AtomicInteger targetCloses;
        private final DefaultAnimationRuntime animations;
        private final AnimationStore conflictingAnimation;
        private final int requiredTargetCount;

        private BootstrapFixture(Stage stage, CountingExecutor workers,
                                 BuiltinModelCatalog builtins,
                                 ClientModelRenderTargetManager manager,
                                 ClientModelService service,
                                 CompletableFuture<Void> required,
                                 AtomicInteger targetCloses,
                                 DefaultAnimationRuntime animations,
                                 AnimationStore conflictingAnimation,
                                 int requiredTargetCount) {
            this.stage = stage;
            this.workers = workers;
            this.builtins = builtins;
            this.manager = manager;
            this.service = service;
            this.required = required;
            this.targetCloses = targetCloses;
            this.animations = animations;
            this.conflictingAnimation = conflictingAnimation;
            this.requiredTargetCount = requiredTargetCount;
        }

        private static BootstrapFixture open(Path root, Stage stage) throws Exception {
            return open(root, stage, Runnable::run);
        }

        private static BootstrapFixture open(
                Path root, Stage stage, Executor ownerThread) throws Exception {
            var modelRoot = Files.createDirectories(root.resolve("default"));
            var handle = ManagedContainer.openResidentDefault(
                    Files.readAllBytes(defaultContainer),
                    defaultLocation());
            var gameCacheRoot = root.resolve("cache");
            var storage = storage(gameCacheRoot);
            var builtins = builtins(handle, modelRoot);
            var workers = new CountingExecutor();
            workers.setRemoveOnCancelPolicy(true);
            ReloadableModelCatalog catalogOwner = null;
            ClientModelService service = null;
            try {
                var sources = List.of(
                        new ModelCatalogSource(CatalogRootKind.CUSTOM,
                                root.resolve("custom"), true),
                        new ModelCatalogSource(CatalogRootKind.AUTH,
                                root.resolve("auth"), true));
                catalogOwner = new ReloadableModelCatalog(
                        storage, sources, builtins);
                var catalogs = new ClientCatalogManager(catalogOwner);
                var animations = new DefaultAnimationRuntime(defaultContract);
                AnimationStore conflictingAnimation = null;
                if (stage == Stage.ANIMATIONS) {
                    var player = handle.view().requireRenderTarget(
                            RenderTargetIds.PLAYER).descriptor();
                    conflictingAnimation = AnimationStore.lazy(List.of("conflict"),
                            ignored -> null, ignored -> null);
                    animations.publish(DefaultAnimationKey.domain(player, "fp_arm"),
                            conflictingAnimation);
                }
                var bakedRoot = root.resolve("baked");
                var cache = new AtomicSharedCache(bakedRoot);
                var loader = new ModelRenderTargetLoader(
                        new BakedModelCache(bakedRoot, cache),
                        new BakedAnimationCache(bakedRoot, cache),
                        workers, animations);
                var targetCloses = new AtomicInteger();
                installBuildStage(
                        loader, stage, handle.representation().modelId(), targetCloses);
                if (stage == Stage.PARSE) {
                    var cause = AssetLoadException.content("required parse failed");
                    setField(handle, "chunks", new FailingChunks(cause));
                } else if (stage == Stage.DEFAULT_TARGET) {
                    removePlayerTarget(handle.view());
                }
                var contentStore = new RuntimeContentStore(
                        ignored -> { });
                var manager = new ClientModelRenderTargetManager(
                        catalogs, loader, animations, workers, Runnable::run, contentStore);
                catalogs.setListener(manager::catalogChanged);
                service = service(workers, catalogs,
                        new ClientAssetRepository(catalogs, contentStore), manager,
                        root.resolve("remote"));
                var required = manager.startRequired(catalogs.initialize());
                var requiredTargetCount = handle.view().getRenderTargets().stream()
                        .mapToInt(target -> target.getTextureNames().size())
                        .sum();
                return new BootstrapFixture(
                        stage, workers, builtins, manager, service, required, targetCloses,
                        animations, conflictingAnimation, requiredTargetCount);
            } catch (Throwable error) {
                if (service != null) {
                    service.close();
                } else {
                    if (catalogOwner != null) {
                        catalogOwner.close();
                    }
                    workers.shutdownNow();
                }
                builtins.close();
                throw error;
            }
        }

        private static ModelStorageInfra storage(Path gameCacheRoot)
                throws Exception {
            var constructor = ModelStorageInfra.class.getDeclaredConstructor(
                    Path.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(gameCacheRoot, "bootstrap-test");
        }

        private static BuiltinModelCatalog builtins(
                ManagedContainer handle, Path temporaryDirectory) throws Exception {
            var catalog = (BuiltinModelCatalog) UnsafeUtil.getUnsafe()
                    .allocateInstance(BuiltinModelCatalog.class);
            setField(catalog, "contract", defaultContract);
            setField(catalog, "defaultHandle", handle);
            return catalog;
        }

        private static ClientModelService service(
                CountingExecutor workers,
                ClientCatalogManager catalogs,
                ClientAssetRepository assets,
                ClientModelRenderTargetManager manager,
                Path remoteRoot) throws Exception {
            var constructor = ClientModelService.class.getDeclaredConstructor(
                    ScheduledThreadPoolExecutor.class,
                    ClientCatalogManager.class,
                    ClientAssetRepository.class,
                    ClientModelRenderTargetManager.class,
                    Path.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    workers, catalogs, assets, manager, remoteRoot);
        }

        private static void installBuildStage(
                ModelRenderTargetLoader loader, Stage stage,
                Hash256 modelHash, AtomicInteger closes) throws Exception {
            var buildStageType = Class.forName(
                    ModelRenderTargetLoader.class.getName() + "$BuildStage");
            var buildStage = Proxy.newProxyInstance(
                    buildStageType.getClassLoader(), new Class<?>[]{buildStageType},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("build")) {
                            return switch (method.getName()) {
                                case "toString" -> "RequiredBootstrapBuildStage";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> throw new AssertionError(method);
                            };
                        }
                        if (stage == Stage.BAKE) {
                            throw AssetLoadException.content("required bake failed");
                        }
                        var input = args[1];
                        var requestMethod = input.getClass().getDeclaredMethod("request");
                        requestMethod.setAccessible(true);
                        var request = requestMethod.invoke(input);
                        var targetIdMethod = request.getClass().getDeclaredMethod("targetId");
                        targetIdMethod.setAccessible(true);
                        var targetId = (String) targetIdMethod.invoke(request);
                        return candidate(target(modelHash, targetId,
                                (stage == Stage.SUCCESS || stage == Stage.ANIMATIONS)
                                        && targetId.equals(RenderTargetIds.PLAYER), closes));
                    });
            setField(loader, "buildStage", buildStage);
        }

        private static Object candidate(ModelRenderTarget target) throws Exception {
            var candidateType = Class.forName(
                    ModelRenderTargetLoader.class.getName() + "$ModelCandidate");
            var testing = candidateType.getDeclaredMethod("testing", ModelRenderTarget.class);
            testing.setAccessible(true);
            return testing.invoke(null, target);
        }

        private static ModelRenderTarget target(
                Hash256 modelHash, String targetId, boolean player,
                AtomicInteger closes) throws Exception {
            var target = (ModelRenderTarget) UnsafeUtil.getUnsafe()
                    .allocateInstance(ModelRenderTarget.class);
            setField(target, "modelHash", modelHash);
            setField(target, "renderTargetId", targetId);
            setField(target, "targetResources",
                    player ? playerResources() : new EmptyResources());
            setField(target, "cleanable", (Cleaner.Cleanable) closes::incrementAndGet);
            return target;
        }

        private static PlayerModelResources playerResources() throws Exception {
            var resources = (PlayerModelResources) UnsafeUtil.getUnsafe()
                    .allocateInstance(PlayerModelResources.class);
            setField(resources, "animations", new AnimationStore());
            setField(resources, "fpArmAnimations", new AnimationStore());
            return resources;
        }

        private static void removePlayerTarget(ModelFileView view) {
            var targets = view.getRenderTargets().stream()
                    .filter(target -> !target.id().equals(RenderTargetIds.PLAYER))
                    .toList();
            var byId = new LinkedHashMap<String, Object>();
            targets.forEach(target -> byId.put(target.id(), target));
            setField(view, "renderTargets", targets);
            setField(view, "renderTargetsById", Map.copyOf(byId));
        }

        @Override
        public void close() {
            service.close();
            builtins.close();
            assertTrue(workers.isTerminated());
            assertEquals(1, workers.shutdownCalls.get());
            if (stage != Stage.SUCCESS) {
                assertEquals(0, targetCloses.get());
            }
        }
    }

    private static final class CountingExecutor extends ScheduledThreadPoolExecutor {
        private final AtomicInteger shutdownCalls = new AtomicInteger();

        private CountingExecutor() {
            super(2);
        }

        @Override
        public void shutdown() {
            shutdownCalls.incrementAndGet();
            super.shutdown();
        }
    }

    private record EmptyResources() implements RenderTargetResources {
    }

    private record FailingChunks(AssetLoadException cause) implements ChunkDataSource {
        @Override
        public UniBuffer readPayload(
                AssetContainerView.ChunkInfo chunk, BufferType bufferType) throws IOException {
            throw cause;
        }

        @Override
        public UniBuffer readStoredVerified(
                AssetContainerView.ChunkInfo chunk, BufferType bufferType) throws IOException {
            throw cause;
        }
    }

    private static final class StageFailure extends RuntimeException {
        private StageFailure(String message) {
            super(message);
        }
    }
}
