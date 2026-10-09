// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.model.service.ClientModelService;
import com.elfmcys.ysm.client.texture.CustomTextureManager;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ClientTickEvent {
    private static int tickCount;
    private static int refreshRate = 60;

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        tickCount++;
        CustomTextureManager.tick();
        ClientModelService.current().ifPresent(ClientModelService::tick);
        // Window no longer exposes a refresh rate. Keep the last value, which starts at 60.

        var player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerAnimatableCapabilityProvider.get(player).ifPresent(capability -> {
                capability.handleRoamingVarsChanges();
                ClientProtocolGateway.tick(player, capability);
            });
        }
    }

    public static int getTickCount() {
        return tickCount;
    }

    public static int getRefreshRate() {
        return refreshRate;
    }
}
