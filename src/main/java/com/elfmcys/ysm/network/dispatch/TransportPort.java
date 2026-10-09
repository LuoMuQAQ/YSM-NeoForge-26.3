package com.elfmcys.ysm.network.dispatch;

import com.elfmcys.ysm.network.frame.OutboundFrame;

public interface TransportPort {
    boolean isOpen();

    boolean isWritable();

    long highWatermarkBytes();

    long pendingBytes();

    SendResult trySend(OutboundFrame frame);
}
