// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.client.compat.top.TopPlugin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

@net.neoforged.fml.common.EventBusSubscriber()
public final class InterModMsg {
    @SubscribeEvent
    @SuppressWarnings("Convert2MethodRef")
    public static void onEnqueue(final InterModEnqueueEvent event) {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", () -> new TopPlugin());
    }
}