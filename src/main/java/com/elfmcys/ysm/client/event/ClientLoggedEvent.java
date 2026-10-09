// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.network.forge.ClientSessionRuntime;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;


@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ClientLoggedEvent {
    private static Connection loggedIn;

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        var connection = event.getConnection();
        if (loggedIn == connection) {
            return;
        }
        if (!YesSteveModel.isAvailable()) {
            loggedIn = connection;
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        ClientSessionRuntime.beginConnection(connection);
        loggedIn = connection;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        var connection = event.getConnection();
        if (loggedIn != connection) {
            return;
        }
        loggedIn = null;
        if (YesSteveModel.isAvailable()) {
            ClientSessionRuntime.disconnect(connection);
        }
    }
}
