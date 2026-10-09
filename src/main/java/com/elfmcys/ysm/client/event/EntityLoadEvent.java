// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.ProjectileAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class EntityLoadEvent {
    private static final Cache<Integer, List<Consumer<Entity>>> CACHE = CacheBuilder.newBuilder().expireAfterAccess(30,TimeUnit.SECONDS).build();

    @SubscribeEvent
    public static void onEntityLoadToWorld(final EntityJoinLevelEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        var list = CACHE.getIfPresent(event.getEntity().getId());
        if (list != null) {
            for (var consumer : list) {
                consumer.accept(event.getEntity());
            }
        }
        CACHE.invalidate(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onEntityLeaveWorld(final EntityLeaveLevelEvent event) {
        var entity = event.getEntity();
        if (entity instanceof Player) {
            PlayerAnimatableCapabilityProvider.existing(entity)
                    .ifPresent(capability -> capability.reset());
        }
        ProjectileAnimatableCapabilityProvider.get(entity)
                .ifPresent(capability -> capability.reset());
        VehicleAnimatableCapabilityProvider.get(entity)
                .ifPresent(capability -> capability.reset());
        entity.removeData(com.elfmcys.ysm.capability.EntityAttachments.CLIENT_RUNTIME);
        TlmClientCompat.releaseAnimatable(entity);
        CACHE.invalidate(entity.getId());
    }

    public static void executeOnEntity(int entityId, Consumer<Entity> consumer) {
        Minecraft.getInstance().execute(() -> {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var entity = level.getEntity(entityId);
                if (entity != null) {
                    consumer.accept(entity);
                } else {
                    addRecoveryHandler(entityId, consumer);
                }
            }
        });
    }

    // 非线程安全
    private static void addRecoveryHandler(int entityId, Consumer<Entity> consumer) {
        var list = CACHE.getIfPresent(entityId);
        if (list == null) {
            list = new ArrayList<>(3);
            CACHE.put(entityId, list);
        }
        list.add(consumer);
    }
}
