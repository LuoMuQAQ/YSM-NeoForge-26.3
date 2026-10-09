// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.tacz;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import java.util.List;
import java.util.function.Consumer;

public class TacEvent {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void register() {
        OptionalApi.query("tacz", null, () -> {
            var classes = List.of("GunFireEvent", "GunMeleeEvent", "GunReloadEvent").stream()
                    .map(name -> OptionalApi.type("com.tacz.guns.api.event.common." + name).asSubclass(Event.class)).toList();
            for (var type : classes) {
                Consumer listener = event -> OptionalApi.query("tacz", null, () -> {
                    if (!YesSteveModel.isAvailable()) return null;
                    var shooter = (LivingEntity) OptionalApi.call(event,
                            type.getSimpleName().equals("GunReloadEvent") ? "getEntity" : "getShooter");
                    PlayerAnimatableCapabilityProvider.get(shooter).ifPresent(cap -> cap.setTacGunAnimationNeedReload(true));
                    TlmClientCompat.markTacGunAnimationNeedReload(shooter);
                    return null;
                });
                NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) type, listener);
            }
            return null;
        });
    }
}
