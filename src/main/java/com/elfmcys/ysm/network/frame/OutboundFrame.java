package com.elfmcys.ysm.network.frame;

import com.elfmcys.ysm.buffer.UniBuffer;

import java.util.Objects;

/** Owns one encoded frame until the synchronous transport call returns. */
public final class OutboundFrame implements AutoCloseable {
    private final UniBuffer data;

    public OutboundFrame(UniBuffer data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    public int size() {
        return data.size();
    }

    public UniBuffer borrow() {
        return data.borrow();
    }

    public byte[] bytes() {
        try (var array = data.acquireArray()) {
            var result = new byte[array.size()];
            System.arraycopy(array.array(), array.arrayOffset(), result, 0, result.length);
            return result;
        }
    }

    public boolean contentEquals(OutboundFrame other) {
        return other != null && data.nio().equals(other.data.nio());
    }

    @Override
    public void close() {
        data.close();
    }
}
