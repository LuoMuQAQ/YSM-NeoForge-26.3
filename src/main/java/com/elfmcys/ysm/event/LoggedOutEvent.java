// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.model.service.ServerModelService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber
public class LoggedOutEvent {
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ServerModelService.current().ifPresent(service ->
                    service.closeSession(serverPlayer));
        }
    }
}
