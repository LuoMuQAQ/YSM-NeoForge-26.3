package com.elfmcys.ysm.natives;

import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.natives.buffer.BufferArgument;

import java.lang.ref.Reference;

public class Blake3 {
    public static final int HASH_SIZE = 32;

    private static final int OP_COMPARE = 1;
    private static final int OP_COMPUTE = 2;

    public static boolean validateHash(UniBuffer source, byte[] hash) {
        var args = BufferArgument.packInput(source);
        final int result;
        try {
            result = nBlake3(args.obj(), args.flags(), hash, OP_COMPARE);
        } finally {
            Reference.reachabilityFence(source);
        }
        if (result == -1) {
            throw new IllegalArgumentException();
        }
        return result != 0;
    }

    public static byte[] computeHash(UniBuffer source) {
        var hash = new byte[HASH_SIZE];
        computeHash(source, hash);
        return hash;
    }

    public static void computeHash(UniBuffer source, byte[] hash) {
        var args = BufferArgument.packInput(source);
        final boolean result;
        try {
            result = nBlake3(args.obj(), args.flags(), hash, OP_COMPUTE) == -1;
        } finally {
            Reference.reachabilityFence(source);
        }
        if (result) {
            throw new IllegalArgumentException();
        }
    }

    private static native int nBlake3(Object source, long sourceFlags, byte[] hash, int op);
}
