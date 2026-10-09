// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.network.forge.SessionProtocolHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber
public final class LoginEvent {
    @SubscribeEvent
    public static void onLoggedInServer(PlayerEvent.PlayerLoggedInEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SessionProtocolHandler.sendServerHello(serverPlayer);
        }
    }
}
