// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.event;

import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.gui.MaidModelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class YsmMaidScreenEvent {
    public void onOpenYsmMaidScreen(Object event) {
        var maid = (LivingEntity) OptionalApi.call(event, "getMaid");
        YsmMaidCapabilityProvider.get(maid).ifPresent(cap -> Minecraft.getInstance().gui.setScreen(new MaidModelScreen(maid)));
    }
}
