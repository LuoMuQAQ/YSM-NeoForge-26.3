// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.dispatch.SendResult;
import com.elfmcys.ysm.network.dispatch.TransportPort;
import com.elfmcys.ysm.network.frame.OutboundFrame;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

/** Netty-specific pressure and submission details kept behind TransportPort. */
public final class ForgeTransportPort implements TransportPort {
    private final ServerPlayer player;

    public ForgeTransportPort(ServerPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public boolean isOpen() {
        return player.connection != null && player.connection.getConnection().isConnected();
    }

    @Override
    public boolean isWritable() {
        return isOpen() && player.connection.getConnection().channel().isWritable();
    }

    @Override
    public long highWatermarkBytes() {
        return player.connection.getConnection().channel().config().getWriteBufferHighWaterMark();
    }

    @Override
    public long pendingBytes() {
        var outbound = player.connection.getConnection().channel().unsafe().outboundBuffer();
        return outbound == null ? 0 : outbound.totalPendingWriteBytes();
    }

    @Override
    public SendResult trySend(OutboundFrame frame) {
        if (!isOpen()) {
            return SendResult.FAILED;
        }
        try {
            NetworkHandler.sendFrameToPlayer(player, frame);
            return SendResult.SUCCESS;
        } catch (RuntimeException failure) {
            return SendResult.FAILED;
        }
    }
}
