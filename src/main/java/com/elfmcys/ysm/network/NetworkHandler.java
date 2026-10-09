// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.network;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.buffer.UniBufferIO;
import com.elfmcys.ysm.network.forge.ClientSessionRuntime;
import com.elfmcys.ysm.network.frame.FrameCodec;
import com.elfmcys.ysm.network.frame.OutboundFrame;
import com.elfmcys.ysm.network.protocol.MessageDirection;
import com.elfmcys.ysm.network.protocol.ProtocolMessages;
import com.elfmcys.ysm.network.protocol.ProtocolVersion;
import java.util.function.Consumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import us.hebi.quickbuf.ProtoMessage;

/** NeoForge payload transport; typed frame/session authority remains in the domain handlers. */
public final class NetworkHandler {
    public static final String VERSION = ProtocolVersion.TRANSPORT_VERSION;
    public static final Identifier CHANNEL_NAME = Identifier.fromNamespaceAndPath(
            YesSteveModel.MOD_ID, ProtocolVersion.CHANNEL_PATH);

    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).optional().executesOn(HandlerThread.NETWORK)
                .playBidirectional(YsmFramePayload.TYPE, YsmFramePayload.CODEC,
                        (payload, context) -> receive(payload, context, MessageDirection.CLIENT_TO_SERVER),
                        (payload, context) -> receive(payload, context, MessageDirection.SERVER_TO_CLIENT));
    }

    public static boolean isPlayerChannelPresent(ServerPlayer player) {
        return player.connection != null && isChannelPresent(player.connection.getConnection());
    }

    public static boolean isRemoteChannelPresent() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && isChannelPresent(listener.getConnection());
    }

    public static boolean isChannelPresent(@Nullable Connection connection) {
        return connection != null && connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, CHANNEL_NAME);
    }

    public static void sendToServer(ProtoMessage<?> message) { sendToServer(payload(message)); }

    public static void sendToServer(NetworkPayload<?> payload) {
        if (!isRemoteChannelPresent()) { payload.close(); return; }
        send(MessageDirection.CLIENT_TO_SERVER, ClientPacketDistributor::sendToServer, payload);
    }

    public static void sendToClientPlayer(ProtoMessage<?> message, Player player) {
        sendToClientPlayer(payload(message), player);
    }

    public static void sendToClientPlayer(NetworkPayload<?> payload, Player player) {
        var serverPlayer = (ServerPlayer) player;
        if (!isPlayerChannelPresent(serverPlayer)) { payload.close(); return; }
        send(MessageDirection.SERVER_TO_CLIENT,
                wire -> PacketDistributor.sendToPlayer(serverPlayer, wire), payload);
    }

    public static void broadcastToAllPlayers(ProtoMessage<?> message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) { return; }
        sendToRecipients(message, server.getPlayerList().getPlayers());
    }

    public static void broadcastToVisiblePlayers(ProtoMessage<?> message, Entity entity) {
        sendToRecipients(message, trackingPlayers(entity));
    }

    public static void broadcastToVisiblePlayersAndSelf(ProtoMessage<?> message, Player self) {
        var recipients = new java.util.ArrayList<>(trackingPlayers(self));
        if (self instanceof ServerPlayer player && !recipients.contains(player)) { recipients.add(player); }
        sendToRecipients(message, recipients);
    }

    private static List<ServerPlayer> trackingPlayers(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("YSM broadcasts require a server level");
        }
        return level.getChunkSource().chunkMap.getPlayersWatching(entity);
    }

    private static void sendToRecipients(ProtoMessage<?> message, List<ServerPlayer> players) {
        send(MessageDirection.SERVER_TO_CLIENT, wire -> {
            for (var player : players) {
                if (isPlayerChannelPresent(player)) { PacketDistributor.sendToPlayer(player, wire); }
            }
        }, payload(message));
    }

    public static void sendFrameToPlayer(ServerPlayer player, OutboundFrame frame) {
        if (!isPlayerChannelPresent(player)) {
            throw new IllegalStateException("YSM payload unavailable on this connection");
        }
        PacketDistributor.sendToPlayer(player, copyFrame(frame));
    }

    private static YsmFramePayload copyFrame(OutboundFrame frame) {
        var bytes = new byte[frame.size()];
        // Own the bytes before returning to the dispatcher. Netty may encode asynchronously.
        UniBufferIO.copy(frame.borrow(), 0, ArrayBuffer.borrow(bytes), 0, bytes.length);
        return new YsmFramePayload(bytes);
    }

    private static void send(MessageDirection direction, Consumer<YsmFramePayload> target,
                             NetworkPayload<?> payload) {
        try (payload; var protobuf = ProtocolBuffer.serialize(payload.protobuf());
             var attachment = payload.raw().map(UniBuffer::borrow)
                     .orElseGet(() -> ArrayBuffer.allocate(0))) {
            var spec = ProtocolMessages.REGISTRY.find(payload.protobuf().getClass())
                    .orElseThrow(() -> new IllegalArgumentException("Unregistered protocol message"));
            if (spec.direction() != direction) {
                throw new IllegalArgumentException("Protocol message has the wrong network direction");
            }
            spec.attachmentPolicy().validate(attachment.size());
            try (var frame = FrameCodec.encode(spec.id(), protobuf, attachment)) {
                target.accept(copyFrame(frame));
            }
        }
    }

    private static void receive(YsmFramePayload payload, IPayloadContext context,
                                MessageDirection direction) {
        try (var wire = ArrayBuffer.borrow(payload.bytes());
             var frame = FrameCodec.decode(wire, id -> ProtocolInbound.accepts(id, direction))) {
            var spec = ProtocolMessages.REGISTRY.find(frame.messageId()).orElseThrow();
            ProtocolInbound.dispatch(frame, spec, () -> context);
        } catch (RuntimeException error) {
            YesSteveModel.LOGGER.warn("Rejected invalid YSM frame", error);
            if (direction == MessageDirection.SERVER_TO_CLIENT) {
                var connection = context.connection();
                context.enqueueWork(() -> {
                    try { ClientSessionRuntime.failProtocolSession(connection); }
                    catch (IllegalStateException ignored) {}
                });
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static NetworkPayload<?> payload(ProtoMessage<?> message) {
        return NetworkPayload.protobuf((ProtoMessage) message);
    }
}
