// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.ModelInfoCapabilityProvider;
import com.elfmcys.ysm.capability.ModelSelectionService;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.StarModelsCapabilityProvider;
import com.elfmcys.ysm.client.animation.molang.CustomMolangParser;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.TlmCommonCompat;
import com.elfmcys.ysm.event.CapabilityEvent;
import com.elfmcys.ysm.geckolib3.core.molang.value.IValue;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.service.ServerModelService;
import com.elfmcys.ysm.molang.parser.ParseException;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.protocol.StarredModelSnapshots;
import com.elfmcys.ysm.proto.network.*;
import com.elfmcys.ysm.proto.network.EmitMolangSync;
import com.elfmcys.ysm.proto.network.EntityAnimationActionRequest;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.ExecuteMolangEvent;
import com.elfmcys.ysm.proto.network.Hand;
import com.elfmcys.ysm.proto.network.MolangSyncEvent;
import com.elfmcys.ysm.proto.network.StarredModelOperation;
import com.elfmcys.ysm.proto.network.StarredModelsSnapshot;
import com.elfmcys.ysm.proto.network.SubmitRouletteExpressionRequest;
import com.elfmcys.ysm.proto.network.SwingHandRequest;
import com.elfmcys.ysm.proto.network.UpdateStarredModelRequest;
import com.elfmcys.ysm.util.ProtoBytes;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ControlHandler {
    private static final int MAX_MOLANG_TARGETS = 1024;
    private static final int MAX_MOLANG_ARGUMENTS = 16;
    private static final int MAX_EXPRESSION_LENGTH = 4096;

    private ControlHandler() {
    }

    public static void handleStarredModels(StarredModelsSnapshot message,
                                           Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        var hashes = readHashSet(message.modelHashes());
        var connection = context.connection();
        if (hashes != null) context.enqueueWork(() ->
                ClientSessionRuntime.runIfCurrent(connection, () -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        StarModelsCapabilityProvider.get(player)
                                .ifPresent(capability -> capability.setStarModels(hashes));
                    }
                }));
    }

    public static void handleUpdateStar(UpdateStarredModelRequest message,
                                        Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        var sender = (net.minecraft.server.level.ServerPlayer) context.player();
        if (sender != null && message.modelHash().remaining() == Hash256.SIZE
                && message.operation() != StarredModelOperation.STARRED_MODEL_OPERATION_UNSPECIFIED) {
            var hash = new Hash256(ProtoBytes.copy(message.modelHash()));
            enqueueServer(context, sender, () -> StarModelsCapabilityProvider.get(sender)
                    .ifPresent(capability -> {
                        if (message.operation() == StarredModelOperation.STARRED_MODEL_OPERATION_ADD) {
                            capability.addModel(hash);
                        } else {
                            capability.removeModel(hash);
                        }
                    }));
        }
    }

    public static void handleEntityAnimation(EntityAnimationActionRequest message,
                                             Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        var sender = (net.minecraft.server.level.ServerPlayer) context.player();
        if (sender != null && validNonPlayerTarget(message.target())) {
            enqueueServer(context, sender, () -> {
                var entity = sender.level().getEntity(message.target().entityId());
                if (!TlmCommonCompat.canControlMaid(entity, sender)) return;
                if (message.hasStop() && message.stop()) {
                    TlmCommonCompat.setRouletteAnim(entity, "", -1);
                } else if (message.hasPlay() && message.play().classificationId().length() <= 128) {
                    TlmCommonCompat.setRouletteAnim(entity, message.play().classificationId(),
                            message.play().animationIndex());
                }
            });
        }
    }

    public static void handleExecuteMolang(ExecuteMolangEvent message,
                                           Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        if (message.targets().size() <= MAX_MOLANG_TARGETS
                && message.expression().length() <= MAX_EXPRESSION_LENGTH) {
            var connection = context.connection();
            context.enqueueWork(() -> ClientSessionRuntime.runIfCurrent(
                    connection, () -> executeMolang(message)));
        }
    }

    public static void handleSubmitRoulette(SubmitRouletteExpressionRequest message,
                                            Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        var sender = (net.minecraft.server.level.ServerPlayer) context.player();
        if (sender != null && message.hasTarget() && !message.target().orElseThrow().hasPlayerId()
                && message.expression().length() <= MAX_EXPRESSION_LENGTH) {
            enqueueServer(context, sender, () -> {
                var entity = sender.level().getEntity(message.target().orElseThrow().entityId());
                if (entity != sender && !TlmCommonCompat.canControlMaid(entity, sender)) return;
                var event = ExecuteMolangEvent.newBuilder()
                        .addTargets(EntityRef.newBuilder()
                                .setEntityId(entity.getId()).build())
                        .setExpression(message.expression())
                        .build();
                NetworkHandler.broadcastToVisiblePlayers(event, entity);
            });
        }
    }

    public static void handleEmitMolangSync(EmitMolangSync message,
                                            Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        var sender = (net.minecraft.server.level.ServerPlayer) context.player();
        if (sender != null && validArguments(message.arguments())) {
            enqueueServer(context, sender, () -> {
                var event = MolangSyncEvent.newBuilder()
                        .setSubject(EntityRef.newBuilder()
                                .setEntityId(sender.getId()).build());
                message.arguments().forEach(event::addArguments);
                NetworkHandler.broadcastToVisiblePlayersAndSelf(event.build(), sender);
            });
        }
    }

    public static void handleMolangSync(MolangSyncEvent message,
                                        Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        if (message.hasSubject() && !message.subject().orElseThrow().hasPlayerId()
                && validArguments(message.arguments())) {
            var connection = context.connection();
            context.enqueueWork(() -> ClientSessionRuntime.runIfCurrent(connection, () -> {
                var level = Minecraft.getInstance().level;
                Entity entity = level == null ? null : level.getEntity(message.subject().orElseThrow().entityId());
                if (entity != null) PlayerAnimatableCapabilityProvider.get(entity).ifPresent(capability -> {
                    var args = new FloatArrayList(message.arguments().size());
                    message.arguments().forEach(args::add);
                    capability.molangSync(args);
                });
            }));
        }
    }

    public static void handleSwingHand(SwingHandRequest message,
                                       Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        var sender = (net.minecraft.server.level.ServerPlayer) context.player();
        if (sender != null && message.hand() != Hand.HAND_UNSPECIFIED) {
            enqueueServer(context, sender, () -> swing(sender,
                    message.hand() == Hand.HAND_MAIN ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));
        }
    }

    private static void enqueueServer(IPayloadContext context,
                                      ServerPlayer sender, Runnable action) {
        var connection = context.connection();
        var service = ServerModelService.current().orElse(null);
        var owner = service == null ? null
                : service.session(sender, connection).orElse(null);
        if (owner != null) {
            context.enqueueWork(() -> service.runIfCurrent(
                    sender, connection, owner, () -> {
                        if (owner.active()) {
                            action.run();
                        }
                    }));
        }
    }

    public static void applyAcceptedModelSelection(ServerPlayer sender, Hash256 hash,
                                                   String textureId) {
        applyAcceptedModelSelection(sender, hash, textureId, true);
    }

    static void applyAcceptedModelSelection(ServerPlayer sender, Hash256 hash,
                                            String textureId, boolean publishState) {
        var modelCapability = ModelInfoCapabilityProvider.get(sender)
                .orElse(null);
        if (modelCapability == null) {
            return;
        }
        ServerModelService.instance().catalog().ifPresent(snapshot -> {
            if (hash == null) {
                ModelSelectionService.selectBuiltinDefault(modelCapability, snapshot, textureId);
            } else {
                modelCapability.setModelAndTexture(hash, textureId);
            }
            modelCapability.applyClientAnimation("");
        });
        if (publishState && PlayerStateHandler.sendAuthoritativeFull(sender, true)
                && sender.getVehicle() != null && sender.getVehicle().getFirstPassenger() == sender) {
            CapabilityEvent.onVehicleSetModel(sender.getVehicle(), sender);
        }
    }

    static Set<Hash256> readHashSet(Iterable<ByteBuffer> values) {
        var result = new HashSet<Hash256>();
        var count = 0;
        for (var value : values) {
            if (++count > StarredModelSnapshots.MAX_MODEL_SET_SIZE
                    || value.remaining() != Hash256.SIZE) return null;
            if (!result.add(new Hash256(ProtoBytes.copy(value)))) return null;
        }
        return result;
    }

    private static boolean validNonPlayerTarget(EntityRef target) {
        return target != null && !target.hasPlayerId();
    }

    private static boolean validArguments(FloatList values) {
        if (values.size() > MAX_MOLANG_ARGUMENTS) return false;
        for (var i = 0; i < values.size(); i++) if (!Float.isFinite(values.getFloat(i))) return false;
        return true;
    }

    private static void executeMolang(ExecuteMolangEvent message) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var target : message.targets()) {
            if (target.hasPlayerId()) continue;
            Entity entity = level.getEntity(target.entityId());
            if (entity instanceof Player player) {
                PlayerAnimatableCapabilityProvider.get(player).ifPresent(capability -> {
                    try {
                        IValue value = CustomMolangParser.parseSingleExpressionUnsafe(message.expression());
                        capability.executeMolangExp(value, true, false, null);
                    } catch (ParseException error) {
                        YesSteveModel.LOGGER.error("Failed to execute molang " + message.expression(), error);
                    }
                });
            } else if (TlmCommonCompat.isMaid(entity)) {
                TlmCommonCompat.handleExecuteMolang(entity, message.expression());
            }
        }
    }

    private static void swing(ServerPlayer player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, false);
    }
}
