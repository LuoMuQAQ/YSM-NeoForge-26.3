// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.client.renderer.CustomPlayerRenderer;
import com.elfmcys.ysm.config.ClientConfig;
import com.elfmcys.ysm.event.api.SpecialPlayerRenderEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class RenderFirstPlayerBackground {
    /**
     * 因为 RenderHandEvent 可有几率会渲染多次，所以为了避免多次渲染，这样设计
     */
    private static boolean ALREADY_RENDERED = false;

    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        ALREADY_RENDERED = false;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (ClientConfig.DISABLE_SELF_MODEL.get()) {
            return;
        }
        if (ClientConfig.DISABLE_SELF_HANDS.get()) {
            return;
        }
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null || ALREADY_RENDERED) {
            return;
        }
        ALREADY_RENDERED = true;
        PlayerAnimatableCapabilityProvider.get(player).ifPresent(cap -> {
            if (!cap.isInitializedAndEnabled()) {
                return;
            }
            String modelId = cap.getModelId();
            ModelRenderTarget model = cap.getModelRenderTarget();
            var variant = cap.getModelVariant();
            if (model == null || variant == null) {
                return;
            }
            // TODO
            /*
            if (!variant.armModel().hasFirstPersonBackground) {
                return;
            }
             */
            CustomPlayerRenderer renderer = RegisterEntityRenderersEvent.getPlayerRenderer();
            final PoseStack poseStack = event.getPoseStack();
            CustomPlayerEntity customPlayer = cap;
            if (NeoForge.EVENT_BUS.post(new SpecialPlayerRenderEvent(player, customPlayer, modelId)).isCanceled()) {
                return;
            }

            if (renderer != null) {
                poseStack.pushPose();
                if (Minecraft.getInstance().options.bobView().get()) {
                    bobView(poseStack, event.getPartialTick(), player);
                }
                poseStack.translate(0, -1.5, 0);
                // The old native background mode is not part of DeferredModelDraw.
                poseStack.popPose();
            }
        });
    }

    private static void bobView(PoseStack poseStack, float partialTick, AbstractClientPlayer player) {
        float walk2 = -player.walkAnimation.position(partialTick);
        float bob = player.avatarState().getInterpolatedBob(partialTick);
        poseStack.translate(-Mth.sin(walk2 * (float) Math.PI) * bob * 0.5F, Math.abs(Mth.cos(walk2 * (float) Math.PI) * bob), 0.0D);
        poseStack.rotate(Axis.ZN.rotationDegrees(Mth.sin(walk2 * (float) Math.PI) * bob * 3.0F));
        poseStack.rotate(Axis.XN.rotationDegrees(Math.abs(Mth.cos(walk2 * (float) Math.PI - 0.2F) * bob) * 5.0F));
    }
}
