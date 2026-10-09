package com.elfmcys.ysm.client.demand;

import com.elfmcys.ysm.model.session.server.state.Selection;

import com.elfmcys.ysm.model.resource.client.AcquireResult;
import com.elfmcys.ysm.model.resource.client.BakeProfile;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.model.resource.client.ResourceRequest;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.util.UnsafeUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlayerDemandTest {
    private static final BakeProfile PROFILE = new BakeProfile("test");

    @Test
    void shortPreviousSelectionAndRemoteRequestUseStrictCurrentDwell() {
        var clock = new AtomicLong();
        var resources = new FakeResources();
        var selections = new FakeSelections();
        resources.offline.add(CompletableFuture.completedFuture(Optional.of(
                FakeLease.ready(hash(1)))));
        var delayedMiss = new CompletableFuture<Optional<ResourceLease>>();
        resources.offline.add(delayedMiss);
        resources.online.add(FakeLease.pending(hash(2)));
        var demand = new LocalPlayerDemand(clock::get, resources, selections);

        demand.select(hash(1), "first");
        assertEquals(1, selections.sent.size());
        assertEquals(0, resources.onlineStarts.get());

        clock.set(700);
        demand.select(hash(2), "second");
        delayedMiss.complete(Optional.empty());
        clock.set(1_400);
        demand.tick();
        assertEquals(1, selections.sent.size());
        assertEquals(0, resources.onlineStarts.get());

        clock.set(1_401);
        demand.tick();
        assertEquals(2, selections.sent.size());
        assertEquals(hash(2), selections.sent.get(1).modelId());
        assertEquals(1, resources.onlineStarts.get());
    }

    @Test
    void completeLocalHitBypassesSwitchDwellAndPendingLocalWorkContinues() {
        var clock = new AtomicLong();
        var resources = new FakeResources();
        var selections = new FakeSelections();
        resources.offline.add(CompletableFuture.completedFuture(Optional.of(
                FakeLease.ready(hash(3)))));
        var pending = FakeLease.pending(hash(4));
        resources.offline.add(CompletableFuture.completedFuture(Optional.of(pending)));
        var demand = new LocalPlayerDemand(clock::get, resources, selections);

        demand.select(hash(3), "first");
        clock.set(100);
        demand.select(hash(4), "second");
        assertEquals(1, selections.sent.size());
        assertFalse(pending.closed);

        pending.result = new AcquireResult.Ready(target());
        demand.tick();
        assertEquals(2, selections.sent.size());
        assertTrue(pending.closed);
        assertEquals(0, resources.onlineStarts.get());
    }

    @Test
    void selectionAndResourceFailuresDoNotWaitForEachOther() {
        var clock = new AtomicLong();
        var resources = new FakeResources();
        var selections = new FakeSelections();
        resources.offline.add(CompletableFuture.completedFuture(Optional.of(
                FakeLease.ready(hash(5)))));
        resources.offline.add(CompletableFuture.completedFuture(Optional.empty()));
        selections.failure = new IllegalStateException("selection unavailable");
        resources.online.add(FakeLease.pending(hash(6)));
        var demand = new LocalPlayerDemand(clock::get, resources, selections);

        demand.select(hash(5), "first");
        clock.set(100);
        demand.select(hash(6), "second");
        clock.set(801);
        demand.tick();

        assertEquals(2, selections.sent.size());
        assertEquals(1, resources.onlineStarts.get());
    }

    @Test
    void switchAndSessionResetCancelOnlyCurrentUnsentIntent() {
        var clock = new AtomicLong();
        var resources = new FakeResources();
        var selections = new FakeSelections();
        resources.offline.add(CompletableFuture.completedFuture(Optional.of(
                FakeLease.ready(hash(7)))));
        var abandoned = new CompletableFuture<Optional<ResourceLease>>();
        var current = new CompletableFuture<Optional<ResourceLease>>();
        resources.offline.add(abandoned);
        resources.offline.add(current);
        resources.onlineFailure = new IllegalStateException("resource unavailable");
        var demand = new LocalPlayerDemand(clock::get, resources, selections);

        demand.select(hash(7), "first");
        clock.set(100);
        demand.select(hash(8), "abandoned");
        clock.set(200);
        demand.select(hash(9), "reset");
        assertTrue(abandoned.isCancelled());
        clock.set(901);
        demand.tick();
        assertTrue(current.isCancelled());
        assertEquals(2, selections.sent.size());
        assertEquals(hash(9), selections.sent.get(1).modelId());
        demand.reset();

        clock.set(2_000);
        demand.tick();
        assertEquals(2, selections.sent.size());
        assertEquals(1, resources.onlineStarts.get());
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) marker;
        return new Hash256(bytes);
    }

    private static ModelRenderTarget target() {
        try {
            return (ModelRenderTarget) UnsafeUtil.getUnsafe()
                    .allocateInstance(ModelRenderTarget.class);
        } catch (InstantiationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class FakeResources implements LocalPlayerDemand.ResourceAccess {
        private final ArrayDeque<CompletableFuture<Optional<ResourceLease>>> offline =
                new ArrayDeque<>();
        private final ArrayDeque<ResourceLease> online = new ArrayDeque<>();
        private final AtomicInteger onlineStarts = new AtomicInteger();
        private RuntimeException onlineFailure;

        @Override
        public ResourceRequest request(Hash256 modelId, String textureId) {
            return new ResourceRequest(modelId, "player", textureId, PROFILE);
        }

        @Override
        public CompletableFuture<Optional<ResourceLease>> getOrStartOffline(
                ResourceRequest request) {
            return offline.removeFirst();
        }

        @Override
        public ResourceLease getOrStart(ResourceRequest request) {
            onlineStarts.incrementAndGet();
            if (onlineFailure != null) {
                throw onlineFailure;
            }
            return online.removeFirst();
        }
    }

    private static final class FakeSelections implements LocalPlayerDemand.SelectionEffect {
        private final ArrayList<Selection> sent = new ArrayList<>();
        private boolean available = true;
        private RuntimeException failure;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public void send(Hash256 modelId, String textureId) {
            sent.add(new Selection(modelId, textureId));
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record Selection(Hash256 modelId, String textureId) {
    }

    private static final class FakeLease implements ResourceLease {
        private final Hash256 modelId;
        private AcquireResult result;
        private boolean closed;

        private FakeLease(Hash256 modelId, AcquireResult result) {
            this.modelId = modelId;
            this.result = result;
        }

        private static FakeLease ready(Hash256 modelId) {
            return new FakeLease(modelId, new AcquireResult.Ready(target()));
        }

        private static FakeLease pending(Hash256 modelId) {
            return new FakeLease(modelId, new AcquireResult.Pending());
        }

        @Override
        public AcquireResult poll() {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            return result;
        }

        @Override
        public boolean isCurrent(ResourceRequest request) {
            return !closed && modelId.equals(request.modelId());
        }

        @Override
        public void cancelPending() {
            close();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
