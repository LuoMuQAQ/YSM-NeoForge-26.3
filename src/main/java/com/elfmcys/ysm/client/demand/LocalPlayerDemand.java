package com.elfmcys.ysm.client.demand;

import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.resource.client.AcquireResult;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.model.resource.client.ResourceRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/** Owns the local player's current selection and resource effects for one client session. */
public final class LocalPlayerDemand implements AutoCloseable {
    public interface ResourceAccess {
        ResourceRequest request(Hash256 modelId, String textureId);

        CompletableFuture<Optional<ResourceLease>> getOrStartOffline(ResourceRequest request);

        ResourceLease getOrStart(ResourceRequest request);
    }

    public interface SelectionEffect {
        boolean available();

        void send(@Nullable Hash256 modelId, String textureId);
    }

    private final LongSupplier clock;
    private final ResourceAccess resources;
    private final SelectionEffect selections;
    private final ContinuousDemand<SelectionKey> timeline = new ContinuousDemand<>();

    private SelectionKey current;
    private ResourceRequest request;
    private CompletableFuture<Optional<ResourceLease>> offline;
    private ResourceLease online;
    private boolean offlineMiss;
    private boolean resourceComplete;
    private boolean resourceFinished;
    private boolean selectionSent;

    public LocalPlayerDemand(LongSupplier clock, ResourceAccess resources,
                             SelectionEffect selections) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.selections = Objects.requireNonNull(selections, "selections");
    }

    public void select(@Nullable Hash256 modelId, String textureId) {
        var next = new SelectionKey(modelId, textureId);
        if (current != null && current.equals(next)) {
            tick();
            return;
        }
        clearCurrent();
        if (!selections.available()) {
            timeline.reset();
            return;
        }
        current = next;
        timeline.observe(next, clock.getAsLong());
        if (modelId == null) {
            resourceComplete = true;
            resourceFinished = true;
        } else {
            try {
                request = resources.request(modelId, textureId);
                offline = resources.getOrStartOffline(request);
            } catch (RuntimeException failure) {
                request = null;
                resourceFinished = true;
            }
        }
        tick();
    }

    public void tick() {
        if (current == null) {
            return;
        }
        if (!selections.available()) {
            reset();
            return;
        }
        var nowMillis = clock.getAsLong();
        finishOffline();
        finishOnline();

        var missing = !resourceComplete;
        if (!selectionSent && timeline.effectEligible(missing, nowMillis,
                ContinuousDemand.SWITCH_DWELL_MILLIS)) {
            selectionSent = true;
            try {
                selections.send(current.modelId, current.textureId);
            } catch (RuntimeException ignored) {
                // Resource demand remains independently eligible.
            }
        }

        if (request == null || resourceComplete || resourceFinished || online != null) {
            return;
        }
        if (offline != null) {
            if (!timeline.requiresDwell(true, ContinuousDemand.SWITCH_DWELL_MILLIS)
                    || !timeline.elapsedStrictlyExceeds(nowMillis,
                    ContinuousDemand.SWITCH_DWELL_MILLIS)) {
                return;
            }
            offline.cancel(false);
            offline = null;
            offlineMiss = true;
        }
        if (offlineMiss && timeline.effectEligible(true, nowMillis,
                ContinuousDemand.SWITCH_DWELL_MILLIS)) {
            try {
                online = resources.getOrStart(request);
            } catch (RuntimeException failure) {
                resourceFinished = true;
            }
        }
    }

    public void reset() {
        clearCurrent();
        timeline.reset();
    }

    @Override
    public void close() {
        reset();
    }

    private void finishOffline() {
        var currentOffline = offline;
        if (currentOffline == null || !currentOffline.isDone()) {
            return;
        }
        offline = null;
        try {
            var acquired = currentOffline.join();
            var lease = acquired.orElse(null);
            if (lease == null) {
                offlineMiss = true;
                return;
            }
            try {
                var result = lease.poll();
                if (result instanceof AcquireResult.Ready) {
                    resourceComplete = true;
                    resourceFinished = true;
                    lease.close();
                } else if (result instanceof AcquireResult.Pending) {
                    online = lease;
                } else {
                    resourceFinished = true;
                    lease.close();
                }
            } catch (RuntimeException failure) {
                lease.close();
                resourceFinished = true;
            }
        } catch (CompletionException | CancellationException failure) {
            resourceFinished = true;
        }
    }

    private void finishOnline() {
        var currentOnline = online;
        if (currentOnline == null) {
            return;
        }
        final AcquireResult result;
        try {
            result = currentOnline.poll();
        } catch (RuntimeException failure) {
            online = null;
            resourceFinished = true;
            currentOnline.close();
            return;
        }
        if (result instanceof AcquireResult.Pending) {
            return;
        }
        online = null;
        resourceComplete = result instanceof AcquireResult.Ready;
        resourceFinished = true;
        currentOnline.close();
    }

    private void clearCurrent() {
        if (offline != null) {
            cancelOffline(offline);
            offline = null;
        }
        if (online != null) {
            online.close();
            online = null;
        }
        current = null;
        request = null;
        offlineMiss = false;
        resourceComplete = false;
        resourceFinished = false;
        selectionSent = false;
    }

    private static void cancelOffline(
            CompletableFuture<Optional<ResourceLease>> current) {
        current.whenComplete((acquired, failure) -> {
            if (acquired != null) {
                acquired.ifPresent(ResourceLease::cancelPending);
            }
        });
        current.cancel(false);
    }

    private record SelectionKey(@Nullable Hash256 modelId, String textureId) {
        private SelectionKey {
            textureId = Objects.requireNonNullElse(textureId, "");
        }
    }
}
