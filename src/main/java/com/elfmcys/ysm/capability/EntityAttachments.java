// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import com.elfmcys.ysm.YesSteveModel;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registered entity storage. Selection authority and synchronization remain in YSM. */
public final class EntityAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, YesSteveModel.MOD_ID);

    public static final Supplier<AttachmentType<ModelInfoCapability>> MODEL_INFO = TYPES.register(
            "model_id", () -> persistent(ModelInfoCapability::new,
                    ModelInfoCapability::serializeNBT, ModelInfoCapability::deserializeNBT)
                    .copyOnDeath().copyHandler((source, holder, lookup) -> {
                        var target = new ModelInfoCapability();
                        target.moveFrom(source);
                        return target;
                    }).build());
    public static final Supplier<AttachmentType<AuthModelsCapability>> AUTH_MODELS = TYPES.register(
            "own_models", () -> persistent(AuthModelsCapability::new,
                    state -> wrapList(state.serializeNBT()),
                    (state, data) -> state.deserializeNBT(data.getListOrEmpty("values")))
                    .copyOnDeath().build());
    public static final Supplier<AttachmentType<StarModelsCapability>> STAR_MODELS = TYPES.register(
            "star_models", () -> persistent(StarModelsCapability::new,
                    state -> wrapList(state.serializeNBT()),
                    (state, data) -> state.deserializeNBT(data.getListOrEmpty("values")))
                    .copyOnDeath().build());

    // Prepared by the offline world-copy migration. Unresolved paths and original
    // records remain serializable across saves; they never become implicit grants.
    public static final Supplier<AttachmentType<LegacyPlayerData>> LEGACY_PLAYER_DATA = TYPES.register(
            "legacy_player_data", () -> persistent(LegacyPlayerData::new,
                    LegacyPlayerData::serializeNBT, LegacyPlayerData::deserializeNBT)
                    .copyOnDeath().build());
    public static final Supplier<AttachmentType<LegacyEntityData>> LEGACY_ENTITY_DATA = TYPES.register(
            "legacy_entity_data", () -> persistent(LegacyEntityData::new,
                    LegacyEntityData::serializeNBT, LegacyEntityData::deserializeNBT).build());
    public static final Supplier<AttachmentType<ProjectileModelInfoCapability>> PROJECTILE_MODEL_INFO = TYPES.register(
            "projectile_model_id", () -> persistent(ProjectileModelInfoCapability::new,
                    ProjectileModelInfoCapability::serializeNBT,
                    ProjectileModelInfoCapability::deserializeNBT).build());
    public static final Supplier<AttachmentType<VehicleModelInfoCapability>> VEHICLE_MODEL_INFO = TYPES.register(
            "vehicle_model_id", () -> persistent(VehicleModelInfoCapability::new,
                    VehicleModelInfoCapability::serializeNBT,
                    VehicleModelInfoCapability::deserializeNBT).build());

    // The common registry contains no references to Minecraft client classes.
    // This slot is transient; its actual animation owner is constructed only on the client.
    public static final Supplier<AttachmentType<ClientRuntimeAttachment>> CLIENT_RUNTIME = TYPES.register(
            "client_runtime", () -> AttachmentType.builder(ClientRuntimeAttachment::new).build());

    // Separate from player/projectile/vehicle state: a maid can also be a modeled vehicle.
    public static final Supplier<AttachmentType<ClientRuntimeAttachment>> MAID_RUNTIME = TYPES.register(
            "maid_runtime", () -> AttachmentType.builder(ClientRuntimeAttachment::new).build());

    private EntityAttachments() {}

    public static void register(IEventBus modBus) { TYPES.register(modBus); }

    private static CompoundTag wrapList(ListTag values) {
        var data = new CompoundTag();
        data.put("values", values);
        return data;
    }

    private static <T> AttachmentType.Builder<T> persistent(
            Supplier<T> factory, Function<T, CompoundTag> writer, BiConsumer<T, CompoundTag> reader) {
        return AttachmentType.builder(factory).serialize(new IAttachmentSerializer<>() {
            @Override public T read(IAttachmentHolder holder, ValueInput input) {
                var state = factory.get();
                reader.accept(state, input.read("data", CompoundTag.CODEC).orElseGet(CompoundTag::new));
                return state;
            }

            @Override public boolean write(T state, ValueOutput output) {
                output.store("data", CompoundTag.CODEC, writer.apply(state));
                return true;
            }
        });
    }
}
