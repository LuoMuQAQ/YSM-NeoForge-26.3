// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.event;

import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class YsmMaidTickEvent {
    public void onTickYsmMaid(Object event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        LivingEntity maid = (LivingEntity) OptionalApi.call(event, "getMaid");
        if (player.getUUID().equals(MaidApi.read(maid, "getOwnerUUID"))) {
            submitRoamingVariableChanges(maid);
        }
    }

    private void submitRoamingVariableChanges(LivingEntity maid) {
        YsmMaidCapabilityProvider.get(maid).ifPresent(cap -> {
            // TODO
        });
    }
}
