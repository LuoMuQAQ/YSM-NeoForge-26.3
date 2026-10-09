package com.elfmcys.ysm.model.service;

import com.elfmcys.ysm.AssetPaths;
import com.elfmcys.ysm.format.schema.model.ModelFileConstant;
import com.elfmcys.ysm.model.ModelRuntime;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.ModelCatalogSources;
import com.elfmcys.ysm.model.catalog.content.ModelContent;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.storage.ManagedContainer;
import com.elfmcys.ysm.model.storage.ModelExporter;
import com.elfmcys.ysm.model.storage.PreviewStore;
import com.elfmcys.ysm.util.Closeable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Routes export requests to the tick owner for the active physical side. */
public final class ModelExportService {
    private static final Object EXPORT_LOCK = new Object();
    private static ExportRunner exportRunner;

    private ModelExportService() {
    }

    public static Closeable registerExporter(ExportRunner runner) {
        Objects.requireNonNull(runner, "runner");
        synchronized (EXPORT_LOCK) {
            if (exportRunner != null) {
                throw new IllegalStateException("Client export runner is already registered");
            }
            exportRunner = runner;
        }
        return () -> {
            synchronized (EXPORT_LOCK) {
                if (exportRunner == runner) {
                    exportRunner = null;
                }
            }
        };
    }

    public static CompletableFuture<Path> export(String requestedPath, String extra) {
        Objects.requireNonNull(requestedPath, "requestedPath");
        var client = currentExporter();
        if (client != null) {
            try {
                return Objects.requireNonNull(client.export(requestedPath, extra),
                        "Client export runner returned no operation");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        var server = ServerModelService.current().orElse(null);
        if (server == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No model service owns export operations"));
        }
        return server.export(requestedPath, extra);
    }

    private static ExportRunner currentExporter() {
        synchronized (EXPORT_LOCK) {
            return exportRunner;
        }
    }

    /** Performs one complete worker-safe source/probe/export preparation chain. */
    public static PreparedExport prepare(String requestedPath, String extra) throws Exception {
        Objects.requireNonNull(requestedPath, "requestedPath");
        var path = new ModelPath(requestedPath);
        var operation = openSource(path);
        try {
            var system = ModelRuntime.system();
            var previews = new PreviewStore(system.storage().gameCacheRoot(),
                    system.storage().cache());
            var preview = embedded(operation.source());
            if (preview == null) {
                preview = previews.load(operation.source().representation().containerId())
                        .orElse(null);
            }
            var output = outputPath(path);
            var completed = preview == null
                    ? null
                    : ModelExporter.export(operation.source(), preview, output, extra);
            return new PreparedExport(operation, previews, output, completed, extra);
        } catch (Throwable failure) {
            failure = operation.closeOwned(failure);
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(failure);
        }
    }

    /** Completes encode persistence and artifact export in the caller's finite worker task. */
    public static Path finish(PreparedExport prepared,
                              PreviewStore.EncodedPreview generated) throws Exception {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(generated, "generated");
        var accepted = prepared.store.accept(
                prepared.operation.source().representation().containerId(),
                generated.bytes());
        return ModelExporter.export(prepared.operation.source(), accepted,
                prepared.output, prepared.extra);
    }

    private static SourceOperation openSource(ModelPath path) throws IOException {
        var published = ServerModelService.instance().catalog().orElseThrow()
                .findPath(path.value()).orElse(null);
        return published == null
                ? new SourceOperation(openRejectedDirect(path), true)
                : new SourceOperation(published, false);
    }

    private static Path outputPath(ModelPath path) throws IOException {
        var exportRoot = AssetPaths.exportRoot().toAbsolutePath().normalize();
        var output = exportRoot.resolve(withDirectSuffix(path.value())).normalize();
        if (!output.startsWith(exportRoot)) {
            throw new IOException("Export path escapes its root");
        }
        return output;
    }

    static String withDirectSuffix(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".mxc")
                ? path
                : path + ".mxc";
    }

    private static PreviewStore.EncodedPreview embedded(ManagedContainer source)
            throws IOException {
        var view = source.modelFile();
        var chunk = view.getFileView().getAssetView().getChunkInfo(
                ModelFileConstant.THUMB_BUTTON_CHUNK_NAME);
        var image = view.thumbnailSource(source.chunks());
        if (image == null) {
            return null;
        }
        if (chunk == null) {
            throw new IOException("Model preview chunk is unavailable");
        }
        return PreviewStore.read(image, chunk.size());
    }

    private static ManagedContainer openRejectedDirect(ModelPath path) throws IOException {
        var candidates = new ArrayList<ManagedContainer>();
        try {
            for (var root : ModelCatalogSources.reloadableSources()) {
                var base = root.path().toAbsolutePath().normalize();
                var candidate = base.resolve(directInputPath(path.value())).normalize();
                if (!candidate.startsWith(base)
                        || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                candidates.add(ManagedContainer.openDirect(candidate,
                        new CatalogModelLocation(root.rootKind(), path)));
            }
            if (candidates.size() != 1) {
                throw new IOException(candidates.isEmpty()
                        ? "Model does not exist: " + path.value()
                        : "Model path is ambiguous: " + path.value());
            }
            return candidates.remove(0);
        } finally {
            candidates.forEach(value -> value.representation().close());
        }
    }

    static String directInputPath(String path) {
        var lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mxc") || lower.endsWith(".ysm")
                ? path
                : path + ".mxc";
    }

    @FunctionalInterface
    public interface ExportRunner {
        CompletableFuture<Path> export(String requestedPath, String extra);
    }

    public static final class PreparedExport {
        private final SourceOperation operation;
        private final PreviewStore store;
        private final Path output;
        private final Path completed;
        private final String extra;

        private PreparedExport(SourceOperation operation, PreviewStore store,
                               Path output, Path completed, String extra) {
            this.operation = operation;
            this.store = store;
            this.output = output;
            this.completed = completed;
            this.extra = extra;
        }

        public ModelContent content() {
            return operation.source();
        }

        public Path completed() {
            return completed;
        }

        public Throwable closeOwned(Throwable failure) {
            return operation.closeOwned(failure);
        }
    }

    private static final class SourceOperation {
        private final ManagedContainer source;
        private final boolean owned;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SourceOperation(ManagedContainer source, boolean owned) {
            this.source = source;
            this.owned = owned;
        }

        private ManagedContainer source() {
            return source;
        }

        private Throwable closeOwned(Throwable failure) {
            if (owned && closed.compareAndSet(false, true)) {
                try {
                    source.representation().close();
                } catch (Throwable closeFailure) {
                    if (failure == null) {
                        return closeFailure;
                    }
                    failure.addSuppressed(closeFailure);
                }
            }
            return failure;
        }
    }

    /** Server-tick owner for dedicated-host exports that need no client renderer. */
    static final class ServerRuntime implements Closeable {
        private static final int MAX_ACTIVE = 8;
        private static final int PENDING_CAPACITY = 16;
        private static final int ADMISSIONS_PER_TICK = 4;
        private static final AtomicLong NEXT_ID = new AtomicLong();

        private final Executor workers;
        private final ArrayBlockingQueue<ServerSubmission> submissions =
                new ArrayBlockingQueue<>(PENDING_CAPACITY);
        private final Map<Long, ServerOperation> active = new LinkedHashMap<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        ServerRuntime(Executor workers) {
            this.workers = Objects.requireNonNull(workers, "workers");
        }

        CompletableFuture<Path> submit(String requestedPath, String extra) {
            var output = new CompletableFuture<Path>();
            var submission = new ServerSubmission(
                    NEXT_ID.incrementAndGet(), requestedPath, extra, output);
            if (closed.get()) {
                output.completeExceptionally(
                        new CancellationException("Server export owner is closed"));
            } else if (!submissions.offer(submission)) {
                output.completeExceptionally(new RejectedExecutionException(
                        "Server export request capacity is exhausted"));
            } else if (closed.get() && submissions.remove(submission)) {
                output.completeExceptionally(
                        new CancellationException("Server export owner is closed"));
            }
            return output;
        }

        synchronized void tick() {
            if (closed.get()) {
                return;
            }
            for (var operation : new ArrayList<>(active.values())) {
                var fact = operation.slot.poll();
                if (fact == null) {
                    continue;
                }
                active.remove(operation.submission.id());
                operation.slot.close();
                if (operation.submission.output().isDone()) {
                    continue;
                }
                if (fact.failure() == null) {
                    operation.submission.output().complete(fact.output());
                } else {
                    operation.submission.output().completeExceptionally(fact.failure());
                }
            }
            for (var admitted = 0;
                 admitted < ADMISSIONS_PER_TICK && active.size() < MAX_ACTIVE;
                 admitted++) {
                var submission = submissions.poll();
                if (submission == null) {
                    break;
                }
                if (submission.output().isDone()) {
                    admitted--;
                    continue;
                }
                var operation = new ServerOperation(submission);
                active.put(submission.id(), operation);
                try {
                    workers.execute(() -> operation.slot.publish(run(submission)));
                } catch (Throwable failure) {
                    active.remove(submission.id());
                    operation.slot.close();
                    submission.output().completeExceptionally(failure);
                }
            }
        }

        private static ServerFact run(ServerSubmission submission) {
            PreparedExport prepared = null;
            Path output = null;
            Throwable failure = null;
            try {
                prepared = prepare(submission.requestedPath(), submission.extra());
                output = prepared.completed();
                if (output == null) {
                    throw new IOException(
                            "A client renderer is required to generate the missing preview");
                }
            } catch (Throwable operationFailure) {
                failure = operationFailure;
            } finally {
                if (prepared != null) {
                    failure = prepared.closeOwned(failure);
                }
            }
            return new ServerFact(output, failure);
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            ServerSubmission submission;
            while ((submission = submissions.poll()) != null) {
                submission.output().completeExceptionally(
                        new CancellationException("Server export owner is closed"));
            }
            for (var operation : active.values()) {
                operation.slot.close();
                operation.submission.output().cancel(false);
            }
            active.clear();
        }
    }

    private record ServerSubmission(long id, String requestedPath, String extra,
                                    CompletableFuture<Path> output) {
    }

    private record ServerFact(Path output, Throwable failure) {
    }

    private static final class ServerOperation {
        private final ServerSubmission submission;
        private final ServerFactSlot slot = new ServerFactSlot();

        private ServerOperation(ServerSubmission submission) {
            this.submission = submission;
        }
    }

    private static final class ServerFactSlot {
        private static final Object WAITING = new Object();
        private static final Object CONSUMED = new Object();
        private static final Object CLOSED = new Object();
        private final AtomicReference<Object> state = new AtomicReference<>(WAITING);

        private void publish(ServerFact fact) {
            state.compareAndSet(WAITING, fact);
        }

        private ServerFact poll() {
            var value = state.get();
            if (value instanceof ServerFact fact
                    && state.compareAndSet(value, CONSUMED)) {
                return fact;
            }
            return null;
        }

        private void close() {
            state.set(CLOSED);
        }
    }
}
