// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class LocalPlayerRespawnEvent {
    @SubscribeEvent
    public static void onFire(ClientPlayerNetworkEvent.Clone event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        // 仅客户端运行时，执行后续的 copyFrom 会出现 null 值问题
        if (!NetworkHandler.isRemoteChannelPresent()) {
            return;
        }

        PlayerAnimatableCapabilityProvider.existing(event.getOldPlayer()).ifPresent(oldCap ->
                PlayerAnimatableCapabilityProvider.get(event.getNewPlayer()).ifPresent(newCap ->
                        newCap.moveFrom(oldCap)));
        ClientProtocolGateway.localPlayerCloned();
    }
}
