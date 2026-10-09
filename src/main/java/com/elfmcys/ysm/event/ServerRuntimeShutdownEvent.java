// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.model.ModelRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class ServerRuntimeShutdownEvent {
    private ServerRuntimeShutdownEvent() {}

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ModelRuntime.close();
    }
}
