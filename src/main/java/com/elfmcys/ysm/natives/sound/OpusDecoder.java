package com.elfmcys.ysm.natives.sound;

import com.elfmcys.ysm.util.CleanerUtil;

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OpusDecoder implements AutoCloseable {
    public static final int ERROR = -1;
    public static final int NEED_INPUT = -2;

    private final long ptr;
    private final State state;
    private final Cleaner.Cleanable cleanable;
    private boolean inputEnded;

    public OpusDecoder(long expectedFrames) {
        if (expectedFrames < 0) {
            throw new IllegalArgumentException("expectedFrames must not be negative");
        }
        ptr = nCreate(expectedFrames);
        if (ptr == 0) {
            throw new OutOfMemoryError("Failed to allocate Opus decoder");
        }
        state = new State(ptr);
        cleanable = CleanerUtil.ref(this, state, State::clean);
    }

    public void feed(ByteBuffer data) {
        checkOpen();
        if (inputEnded) {
            throw new IllegalStateException("Opus input has ended");
        }
        if (!data.isDirect()) {
            throw new IllegalArgumentException("input is not direct data");
        }
        try {
            if (!nFeed(ptr, data.slice())) {
                throw new IllegalArgumentException("Native Opus decoder rejected input");
            }
        } finally {
            Reference.reachabilityFence(this);
            Reference.reachabilityFence(data);
        }
    }

    public void endInput() {
        checkOpen();
        if (!inputEnded) {
            try {
                nEndInput(ptr);
                inputEnded = true;
            } finally {
                Reference.reachabilityFence(this);
            }
        }
    }

    public int decode(ByteBuffer destination) {
        checkOpen();
        if (!destination.isDirect()) {
            throw new IllegalArgumentException("output is not direct data");
        }
        try {
            return nDecode(ptr, destination.slice());
        } finally {
            Reference.reachabilityFence(this);
            Reference.reachabilityFence(destination);
        }
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private void checkOpen() {
        if (!state.open.get()) {
            throw new IllegalStateException("Decoder is closed");
        }
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

    private static native long nCreate(long expectedFrames);

    private static native boolean nFeed(long ptr, ByteBuffer data);

    private static native void nEndInput(long ptr);

    private static native int nDecode(long ptr, ByteBuffer destination);

    private static native void nDestroy(long ptr);
}
