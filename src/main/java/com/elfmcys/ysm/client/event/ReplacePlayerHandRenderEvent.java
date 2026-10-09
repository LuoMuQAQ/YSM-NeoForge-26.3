// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.bus.api.SubscribeEvent;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ReplacePlayerHandRenderEvent {
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (submitArm(event.getArm(), event.getPoseStack(),
                event.getSubmitNodeCollector(), event.getLightCoords(), partialTick)) {
            event.setCanceled(true);
        }
    }

    public static boolean submitArm(HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector,
                                    int packedLight, float partialTick) {
        if (!YesSteveModel.isAvailable()) {
            return false;
        }
        if (ClientConfig.DISABLE_SELF_MODEL.get()) {
            return false;
        }
        if (ClientConfig.DISABLE_SELF_HANDS.get()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        return PlayerAnimatableCapabilityProvider.get(player).map(cap -> {
            if (!cap.isInitializedAndEnabled()) {
                return false;
            }
            if (cap.getModelRenderTarget() == null || cap.getModelVariant() == null) {
                return false;
            }
            return RegisterEntityRenderersEvent.getFirstPersonArmRenderer().render(
                    player, cap, arm, poseStack, collector, packedLight, partialTick);
        }).orElse(false);
    }
}
