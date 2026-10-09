// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.capability;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.EntityAttachments;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.CustomYsmMaidEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;

public final class YsmMaidCapabilityProvider {
    private YsmMaidCapabilityProvider() {}

    public static Optional<CustomYsmMaidEntity> get(Entity entity) {
        if (!accepts(entity)) return Optional.empty();
        return Optional.of(entity.getData(EntityAttachments.MAID_RUNTIME).getOrCreate(
                CustomYsmMaidEntity.class, () -> new CustomYsmMaidEntity((LivingEntity) entity, true)));
    }

    public static Optional<CustomYsmMaidEntity> existing(Entity entity) {
        if (entity == null || !entity.level().isClientSide()) return Optional.empty();
        return entity.getExistingData(EntityAttachments.MAID_RUNTIME)
                .flatMap(slot -> slot.existing(CustomYsmMaidEntity.class));
    }

    public static void release(Entity entity) {
        if (entity == null || !entity.level().isClientSide()) return;
        existing(entity).ifPresent(CustomYsmMaidEntity::reset);
        entity.removeData(EntityAttachments.MAID_RUNTIME);
    }

    private static boolean accepts(Entity entity) {
        return entity != null && entity.level().isClientSide() && YesSteveModel.isAvailable()
                && TlmClientCompat.isInstalled() && MaidApi.isMaid(entity);
    }
}
