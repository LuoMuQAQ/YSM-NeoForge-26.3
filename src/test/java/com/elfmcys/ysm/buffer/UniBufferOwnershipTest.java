package com.elfmcys.ysm.buffer;

import com.elfmcys.ysm.util.CleanerUtil;
import org.junit.jupiter.api.Test;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniBufferOwnershipTest {
    @Test
    void arrayAliasesShareOneOwnerWhileAcquireHasIndependentOwnership() {
        var owner = ArrayBuffer.allocate(2048);
        var alias = owner.slice(4, 16);
        var acquired = owner.acquire();

        owner.close();
        owner.close();

        assertThrows(IllegalStateException.class, alias::size);
        assertEquals(2048, acquired.size());
        acquired.close();
    }

    @Test
    void directAliasesShareOneOwnerWhileAcquireHasIndependentOwnership() {
        var owner = NativeBuffer.move(ByteBuffer.allocateDirect(32));
        var alias = owner.slice(4, 16);
        var acquired = owner.acquire();

        owner.close();
        owner.close();

        assertThrows(IllegalStateException.class, owner::size);
        assertEquals(32, acquired.size());
        acquired.close();
    }

    @Test
    void borrowedViewsRemainUsableWithoutManualCleanup() {
        var array = new byte[16];
        var borrowedArray = ArrayBuffer.borrow(array);
        assertEquals(16, borrowedArray.size());

        var direct = ByteBuffer.allocateDirect(16);
        var borrowedNative = NativeBuffer.borrow(direct);
        assertEquals(16, borrowedNative.size());
    }

    @Test
    void manualAndCleanerPathsShareExactlyOnceAction() {
        var cleanupCount = new AtomicInteger();
        var owner = new TestOwner(cleanupCount);

        owner.close();
        owner.close();

        assertEquals(1, cleanupCount.get());
    }

    private static final class TestOwner {
        private final Cleaner.Cleanable cleanable;

        private TestOwner(AtomicInteger cleanupCount) {
            cleanable = CleanerUtil.ref(this, cleanupCount, AtomicInteger::incrementAndGet);
        }

        private void close() {
            cleanable.clean();
        }
    }
}
