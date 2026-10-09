package com.elfmcys.ysm.network;

import com.elfmcys.ysm.network.frame.FrameCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded immutable transport bytes, owned independently of the native outbound frame. */
public record YsmFramePayload(byte[] bytes) implements CustomPacketPayload {
    private static final int MAX_WIRE_BYTES = FrameCodec.MAX_BODY_BYTES + 14;
    public static final Type<YsmFramePayload> TYPE = new Type<>(NetworkHandler.CHANNEL_NAME);
    public static final StreamCodec<RegistryFriendlyByteBuf, YsmFramePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.bytes),
            buffer -> {
                int count = buffer.readableBytes();
                if (count < 1 || count > MAX_WIRE_BYTES) {
                    throw new IllegalArgumentException("YSM frame exceeds its wire bound");
                }
                var bytes = new byte[count];
                buffer.readBytes(bytes);
                return new YsmFramePayload(bytes);
            });

    public YsmFramePayload {
        if (bytes.length < 1 || bytes.length > MAX_WIRE_BYTES) {
            throw new IllegalArgumentException("Invalid YSM frame length");
        }
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<YsmFramePayload> type() { return TYPE; }
}
