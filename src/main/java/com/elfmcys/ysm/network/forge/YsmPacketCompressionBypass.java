// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.network.NetworkHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class YsmPacketCompressionBypass {
    public static final String MARKER_HANDLER = "ysm_compression_marker";
    private static final ThreadLocal<Boolean> BYPASS_COMPRESSION = new ThreadLocal<>();

    private YsmPacketCompressionBypass() {
    }

    public static final class Marker extends ChannelOutboundHandlerAdapter {
        private final Identifier channelName;

        public Marker() {
            this(NetworkHandler.CHANNEL_NAME);
        }

        Marker(Identifier channelName) {
            this.channelName = channelName;
        }

        @Override
        public void write(ChannelHandlerContext context, Object message,
                          ChannelPromise promise) throws Exception {
            var payload = message instanceof ClientboundCustomPayloadPacket clientbound
                    ? clientbound.payload() : message instanceof ServerboundCustomPayloadPacket serverbound
                    ? serverbound.payload() : null;
            var bypass = payload != null && channelName.equals(payload.type().id());
            var previous = BYPASS_COMPRESSION.get();
            BYPASS_COMPRESSION.set(bypass);
            try {
                context.write(message, promise);
            } finally {
                if (previous == null) {
                    BYPASS_COMPRESSION.remove();
                } else {
                    BYPASS_COMPRESSION.set(previous);
                }
            }
        }
    }

    public static final class Encoder extends CompressionEncoder {
        public Encoder(int threshold) {
            super(threshold);
        }

        @Override
        protected void encode(ChannelHandlerContext context, ByteBuf source, ByteBuf target) {
            if (Boolean.TRUE.equals(BYPASS_COMPRESSION.get())) {
                var output = new FriendlyByteBuf(target);
                output.writeVarInt(0);
                output.writeBytes(source);
                return;
            }
            super.encode(context, source, target);
        }
    }
}
