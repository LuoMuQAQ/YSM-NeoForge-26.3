// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.FirstPersonCompat;
import com.elfmcys.ysm.client.compat.PlayerAnimatorCompat;
import com.elfmcys.ysm.client.compat.realcamera.RealCameraCompat;
import com.elfmcys.ysm.client.renderer.YsmEntityLookup;
import com.elfmcys.ysm.config.ClientConfig;
import com.elfmcys.ysm.util.PersonView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ReplacePlayerRenderEvent {
    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Pre<?> event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (!(YsmEntityLookup.entity(event.getRenderState()) instanceof Player player)) {
            return;
        }
        LocalPlayer playerSelf = Minecraft.getInstance().player;
        if (player.equals(playerSelf) && ClientConfig.DISABLE_SELF_MODEL.get()) {
            return;
        }
        if (!player.equals(playerSelf) && ClientConfig.DISABLE_OTHER_MODEL.get()) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        PlayerAnimatableCapabilityProvider.get(player).ifPresent(cap -> {
            if (cap.isInitializedAndEnabled()) {
                if (!PersonView.isFirstPersonView(cap)
                        || FirstPersonCompat.isRenderingPlayer()
                        || RealCameraCompat.isActive()
                        || (ClientConfig.DISABLE_EXTERNAL_FIRST_PERSON_ANIM.get() || !PlayerAnimatorCompat.hasThirdPersonModelAnim(playerSelf))) {
                    event.setCanceled(true);
                    RegisterEntityRenderersEvent.getPlayerRenderer().submit(
                            player, event.getRenderState(), event.getPoseStack(), event.getSubmitNodeCollector());
                }
            }
        });
    }
}
