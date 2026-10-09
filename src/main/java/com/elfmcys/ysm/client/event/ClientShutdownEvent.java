// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.model.ModelRuntime;
import com.elfmcys.ysm.model.service.ClientModelService;
import com.elfmcys.ysm.network.forge.ClientSessionRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientShutdownEvent {
    private ClientShutdownEvent() {}

    @SubscribeEvent
    public static void onStopping(ClientStoppingEvent event) {
        // Seal network callbacks before closing the service while GPU/audio hosts
        // still exist. The later LoggingOut event then has no live session to close.
        try {
            ClientSessionRuntime.shutdown();
        } finally {
            ClientModelService.current().ifPresent(ClientModelService::close);
        }
    }

    @SubscribeEvent
    public static void onStopped(ClientStoppedEvent event) {
        // Minecraft has now disconnected and joined its integrated server. Shared
        // catalogs/chunk workers must remain alive until that server has stopped.
        ModelRuntime.close();
    }
}
