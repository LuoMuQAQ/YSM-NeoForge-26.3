// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = YesSteveModel.MOD_ID)
public class LivingShieldBlockEvent {
    public static final String COOLDOWN = "ysm$shield_block_cooldown";

    @SubscribeEvent
    public static void onShieldBlockEvent(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent event) {
        LivingEntity entity = event.getEntity();
        entity.getPersistentData().putInt(COOLDOWN, 5);
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.getPersistentData().contains(COOLDOWN)) {
            int cooldown = entity.getPersistentData().getInt(COOLDOWN).orElse(0);
            if (cooldown > 0) {
                entity.getPersistentData().putInt(COOLDOWN, cooldown - 1);
            } else {
                entity.getPersistentData().remove(COOLDOWN);
            }
        }
    }

    public static boolean inShieldBlockCooldown(LivingEntity entity) {
        return entity.getPersistentData().contains(COOLDOWN);
    }
}
