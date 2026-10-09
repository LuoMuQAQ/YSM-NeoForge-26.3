package com.elfmcys.ysm.network.dispatch;

import com.elfmcys.ysm.network.frame.OutboundFrame;

public interface LogicalPacket extends AutoCloseable {
    int fragmentCount();

    long estimateFrameBytes(int index);

    OutboundFrame buildFragment(int index);

    boolean isCancelled();

    @Override
    void close();
}
