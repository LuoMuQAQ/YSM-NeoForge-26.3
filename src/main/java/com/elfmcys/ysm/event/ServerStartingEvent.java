// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.model.service.ServerModelService;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber
public class ServerStartingEvent {
    @SubscribeEvent
    public static void onServerInit(final ServerAboutToStartEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        ServerModelService.start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        ServerModelService.current().ifPresent(ServerModelService::close);
    }
}
