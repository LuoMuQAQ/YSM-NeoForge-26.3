// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.TlmCommonCompat;
import com.elfmcys.ysm.model.ModelRuntime;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@net.neoforged.fml.common.EventBusSubscriber()
public final class CommonEvent {
    @SubscribeEvent
    public static void onSetupEvent(FMLCommonSetupEvent event) {
        if (!YesSteveModel.isAvailable()) {
            event.enqueueWork(() -> ModLoader.addLoadingIssue(YesSteveModel.getUnavailableWarning()));
            return;
        }
        event.enqueueWork(() -> {
            ModelRuntime.initialize();
            TlmCommonCompat.registerEvent();
        });
    }

}
