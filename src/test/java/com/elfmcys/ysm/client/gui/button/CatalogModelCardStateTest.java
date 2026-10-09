package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.model.resource.client.AcquireResult;
import com.elfmcys.ysm.model.resource.client.BakeProfile;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.model.resource.client.ResourceRequest;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.util.UnsafeUtil;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CatalogModelCardStateTest {
    private static final ResourceRequest REQUEST = new ResourceRequest(
            hash(1), "player", "default", new BakeProfile("test"));

    @Test
    void emptyAndCancellationRemainOrdinaryTwoDimensionalState() {
        var state = new CatalogModelCardState.TargetState(REQUEST);

        state.accept(Optional.empty(), null);
        state.accept(null, new CompletionException(new CancellationException("closed")));

        assertNull(state.failure());
        assertNull(state.renderTarget());
    }

    @Test
    void staleAndLateReadyLeasesRemainCleanerManaged() throws Exception {
        var staleCloses = new AtomicInteger();
        var stale = new TestLease(target(), false, staleCloses);
        var state = new CatalogModelCardState.TargetState(REQUEST);
        state.accept(Optional.of(stale), null);
        assertEquals(0, staleCloses.get());
        assertNull(state.failure());

        var lateCloses = new AtomicInteger();
        var late = new TestLease(target(), true, lateCloses);
        state.close();
        state.accept(Optional.of(late), null);
        assertEquals(0, lateCloses.get());
        assertNull(state.failure());
    }

    @Test
    void readyLeaseIsInstalledAndDroppedWithoutExplicitRelease() throws Exception {
        var closes = new AtomicInteger();
        var target = target();
        var lease = new TestLease(target, true, closes);
        var state = new CatalogModelCardState.TargetState(REQUEST);
        var applied = new AtomicReference<ModelRenderTarget>();

        state.accept(Optional.of(lease), null);
        assertSame(target, state.poll(applied::set));
        assertSame(target, applied.get());

        state.close();
        state.close();
        assertEquals(0, closes.get());
        assertNull(state.renderTarget());
    }

    @Test
    void pendingLeaseIsCancelledWhenStateCloses() throws Exception {
        var cancellations = new AtomicInteger();
        var lease = TestLease.pending(cancellations);
        var state = new CatalogModelCardState.TargetState(REQUEST);

        state.accept(Optional.of(lease), null);
        state.close();

        assertEquals(1, cancellations.get());
    }

    @Test
    void endingHoverCancelsOnlyThePendingInterest() throws Exception {
        var cancellations = new AtomicInteger();
        var lease = TestLease.pending(cancellations);
        var state = new CatalogModelCardState.TargetState(REQUEST);

        state.accept(Optional.of(lease), null);
        state.cancelPending();

        assertEquals(1, cancellations.get());
        assertNull(state.renderTarget());
        assertNull(state.failure());
    }

    private static ModelRenderTarget target() throws InstantiationException {
        return (ModelRenderTarget) UnsafeUtil.getUnsafe().allocateInstance(ModelRenderTarget.class);
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) marker;
        return new Hash256(bytes);
    }

    private static final class TestLease implements ResourceLease {
        private final boolean current;
        private final AtomicInteger closes;
        private final AtomicInteger pendingCancellations;
        private final AcquireResult result;
        private boolean closed;

        private TestLease(ModelRenderTarget target, boolean current, AtomicInteger closes) {
            this(new AcquireResult.Ready(target), current, closes, new AtomicInteger());
        }

        private TestLease(AcquireResult result, boolean current, AtomicInteger closes,
                          AtomicInteger pendingCancellations) {
            this.current = current;
            this.closes = closes;
            this.pendingCancellations = pendingCancellations;
            this.result = result;
        }

        private static TestLease pending(AtomicInteger cancellations) throws Exception {
            return new TestLease(new AcquireResult.Pending(), true, new AtomicInteger(),
                    cancellations);
        }

        @Override
        public AcquireResult poll() {
            requireOpen();
            return result;
        }

        @Override
        public boolean isCurrent(ResourceRequest request) {
            requireOpen();
            return current && REQUEST.equals(request);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                closes.incrementAndGet();
            }
        }

        @Override
        public void cancelPending() {
            if (!closed && result instanceof AcquireResult.Pending) {
                closed = true;
                pendingCancellations.incrementAndGet();
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("closed");
            }
        }
    }
}
