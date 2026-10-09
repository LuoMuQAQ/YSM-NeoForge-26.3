package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.model.resource.client.ResourceLease;

import java.util.ArrayList;
import java.util.List;

/** Owns the process-required default target leases until client runtime shutdown. */
public final class RenderTargetLeaseOwnership implements AutoCloseable {
    private final Object lock = new Object();
    private final ArrayList<ResourceLease> required = new ArrayList<>();
    private boolean closed;

    public void addRequired(ResourceLease lease) {
        synchronized (lock) {
            if (closed) {
                lease.cancelPending();
                throw new IllegalStateException("Render-target lease ownership is closed");
            }
            required.add(lease);
        }
    }

    public void removeRequired(ResourceLease lease) {
        synchronized (lock) {
            required.remove(lease);
        }
        lease.cancelPending();
    }

    @Override
    public void close() {
        final List<ResourceLease> previousRequired;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            previousRequired = List.copyOf(required);
            required.clear();
        }
        cancelPending(previousRequired);
    }

    private static void cancelPending(Iterable<ResourceLease> leases) {
        if (leases == null) {
            return;
        }
        for (var lease : leases) {
            lease.cancelPending();
        }
    }
}
