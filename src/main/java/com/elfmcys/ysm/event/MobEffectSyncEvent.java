// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.ModelInfoCapabilityProvider;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber
@SuppressWarnings("resource")
public class MobEffectSyncEvent {
    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (!YesSteveModel.isAvailable() || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance().getEffect() != null) {
            var effectInstance = event.getEffectInstance();
            ModelInfoCapabilityProvider.get(player).ifPresent(cap -> {
                cap.getPropertiesTracker().addEffect(player, effectInstance.getEffect().value(), effectInstance.getAmplifier() + 1);
            });
        }
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (!YesSteveModel.isAvailable() || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && event.getEffect() != null) {
            ModelInfoCapabilityProvider.get(player).ifPresent(cap -> {
                cap.getPropertiesTracker().removeEffect(player, event.getEffect().value());
            });
        }
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (!YesSteveModel.isAvailable() || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance() != null && event.getEffectInstance().getEffect() != null) {
            ModelInfoCapabilityProvider.get(player).ifPresent(cap -> {
                cap.getPropertiesTracker().removeEffect(player, event.getEffectInstance().getEffect().value());
            });
        }
    }
}
