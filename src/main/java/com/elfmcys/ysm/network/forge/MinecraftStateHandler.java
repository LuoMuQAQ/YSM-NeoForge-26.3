// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.capability.ProjectileAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.ProjectileModelInfoCapability;
import com.elfmcys.ysm.capability.VehicleModelInfoCapability;
import com.elfmcys.ysm.client.event.EntityLoadEvent;
import com.elfmcys.ysm.geckolib3.core.molang.util.StringPool;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.network.protocol.ModelReferenceCodec;
import com.elfmcys.ysm.proto.network.EntityRef;
import com.elfmcys.ysm.proto.network.MolangVariable;
import com.elfmcys.ysm.proto.network.ProjectileModelState;
import com.elfmcys.ysm.proto.network.VehicleModelState;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MinecraftStateHandler {
    private MinecraftStateHandler() {
    }

    public static ProjectileModelState projectile(
            int entityId, ProjectileModelInfoCapability capability) {
        var message = ProjectileModelState.newBuilder()
                .setEntity(EntityRef.newBuilder()
                        .setEntityId(entityId).build())
                .setModel(ModelReferenceCodec.create(capability.getOwnerModelHash(), null));
        addVariables(message::addMolangVariables, capability.getMolangVarsServerBound());
        return message.build();
    }

    public static VehicleModelState vehicle(
            int entityId, VehicleModelInfoCapability capability) {
        var message = VehicleModelState.newBuilder()
                .setEntity(EntityRef.newBuilder()
                        .setEntityId(entityId).build())
                .setModel(ModelReferenceCodec.create(capability.getOwnerModelHash(), null));
        addVariables(message::addMolangVariables, capability.getMolangVarsServerBound());
        return message.build();
    }

    public static void handleProjectile(ProjectileModelState message,
                                        Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        if (!ModelReferenceCodec.valid(message.model())) {
            return;
        }
        var modelHash = ModelReferenceCodec.read(message.model());
        var variables = clientVariables(message.molangVariables());
        var connection = context.connection();
        context.enqueueWork(() -> ClientSessionRuntime.runIfCurrent(connection,
                () -> EntityLoadEvent.executeOnEntity(message.entity().entityId(),
                        entity -> applyProjectile(entity, modelHash, variables))));
    }

    public static void handleVehicle(VehicleModelState message,
                                     Supplier<IPayloadContext> contextSupplier) {
        var context = contextSupplier.get();
        if (!ModelReferenceCodec.valid(message.model())) {
            return;
        }
        var modelHash = ModelReferenceCodec.read(message.model());
        var variables = clientVariables(message.molangVariables());
        var connection = context.connection();
        context.enqueueWork(() -> ClientSessionRuntime.runIfCurrent(connection,
                () -> EntityLoadEvent.executeOnEntity(message.entity().entityId(),
                        entity -> applyVehicle(entity, modelHash, variables))));
    }

    private static void addVariables(
            Consumer<MolangVariable> variableConsumer,
            Object2FloatOpenHashMap<String> variables) {
        variables.object2FloatEntrySet().fastForEach(entry -> variableConsumer.accept(
                MolangVariable.newBuilder()
                        .setName(entry.getKey())
                        .setValue(entry.getFloatValue()).build()));
    }

    private static Int2FloatOpenHashMap clientVariables(
            Iterable<MolangVariable> variables) {
        var result = new Int2FloatOpenHashMap();
        for (var variable : variables) {
            result.put(StringPool.computeIfAbsent(variable.name()), variable.value_());
        }
        return result;
    }

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("DataFlowIssue")
    private static void applyProjectile(Entity entity, Hash256 modelHash, Int2FloatOpenHashMap variables) {
        ProjectileAnimatableCapabilityProvider.initialize(entity).ifPresent(animatable -> {
            animatable.init(modelHash);
            animatable.initRoamingVars(variables);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyVehicle(Entity entity, Hash256 modelHash, Int2FloatOpenHashMap variables) {
        VehicleAnimatableCapabilityProvider.initialize(entity).ifPresent(animatable -> {
            animatable.init(modelHash);
            animatable.initRoamingVars(variables);
        });
    }
}
