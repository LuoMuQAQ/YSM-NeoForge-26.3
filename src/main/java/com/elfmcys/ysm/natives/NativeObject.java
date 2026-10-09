package com.elfmcys.ysm.natives;

import com.elfmcys.ysm.util.CleanerUtil;
import com.elfmcys.ysm.util.Closeable;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public class NativeObject implements Closeable {
    private final State state;
    private final Cleaner.Cleanable cleanable;

    public NativeObject(long ptr) {
        state = new State(ptr);
        cleanable = CleanerUtil.ref(this, state, State::clean);
    }

    public long get() {
        if (!state.open.get()) {
            throw new IllegalStateException("Native object is closed");
        }
        return state.ptr;
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private static final class State {
        private final long ptr;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private State(long ptr) {
            this.ptr = ptr;
        }

        private void clean() {
            if (open.compareAndSet(true, false)) {
                nDestroy(ptr);
            }
        }
    }

    private static native void nDestroy(long ptr);
}
