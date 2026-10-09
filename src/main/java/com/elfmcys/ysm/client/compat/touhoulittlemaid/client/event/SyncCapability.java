// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.event;

import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class SyncCapability {
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) YsmMaidCapabilityProvider.get(event.getEntity());
    }
}
