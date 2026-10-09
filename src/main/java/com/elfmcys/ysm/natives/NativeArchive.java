package com.elfmcys.ysm.natives;

import com.elfmcys.ysm.buffer.NativeBuffer;
import com.elfmcys.ysm.buffer.annotation.Borrowed;
import com.elfmcys.ysm.format.vfs.VirtualFileSystem;
import com.elfmcys.ysm.util.CleanerUtil;
import com.elfmcys.ysm.util.Closeable;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

// 目前支持 zip、7z、旧版 ysm
public class NativeArchive implements VirtualFileSystem, Closeable {
    private final long ptr;
    private final State state;
    private final Cleaner.Cleanable cleanable;

    public NativeArchive(String path) {
        ptr = nCreate(path);
        if (ptr == 0) {
            throw new IllegalArgumentException("Could not open archive: " + path);
        }
        state = new State(ptr);
        cleanable = CleanerUtil.ref(this, state, State::clean);
    }

    private void checkClosed() {
        if (!state.open.get()) {
            throw new IllegalStateException("Already Closed");
        }
    }

    @Override
    public String[] listFiles(@Nullable String path) {
        checkClosed();
        final String[] result;
        try {
            result = nList(ptr, path, 0);
        } finally {
            Reference.reachabilityFence(this);
        }
        if (result == null) {
            throw new IllegalStateException("Failed to list files");
        }
        return result;
    }

    @Override
    public String[] listDirectories(@Nullable String path) {
        checkClosed();
        final String[] result;
        try {
            result = nList(ptr, path, 1);
        } finally {
            Reference.reachabilityFence(this);
        }
        if (result == null) {
            throw new IllegalStateException("Failed to list directories");
        }
        return result;
    }

    @Override
    public boolean hasFile(String fileName) {
        checkClosed();
        // 随便返回个东西
        try {
            return nGetFile(ptr, fileName, true) != null;
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Borrowed
    @Override
    public @Nullable NativeBuffer getFile(String fileName) {
        checkClosed();
        final Object buf;
        try {
            buf = nGetFile(ptr, fileName, false);
        } finally {
            Reference.reachabilityFence(this);
        }
        if (buf == null) {
            return null;
        }
        return NativeBuffer.borrow((ByteBuffer) buf);
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

    private static native long nCreate(String path);
    private static native void nDestroy(long ptr);
    private static native String[] nList(long ptr, @Nullable String path, int type);
    private static native Object nGetFile(long ptr, String fileName, boolean dryRun);
}
