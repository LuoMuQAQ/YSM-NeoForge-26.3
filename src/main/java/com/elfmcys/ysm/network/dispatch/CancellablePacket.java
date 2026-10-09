package com.elfmcys.ysm.network.dispatch;

/** Worker-owned packet that can stop at the next scheduling boundary. */
public interface CancellablePacket extends LogicalPacket {
    void cancel();

    /** Reports a fatal fragment-production failure after worker ownership is released. */
    void productionFailed(RuntimeException failure);
}
