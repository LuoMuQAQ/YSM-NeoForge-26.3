// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer;

import com.elfmcys.ysm.capability.PlayerAnimatableCapability;
import com.elfmcys.ysm.client.entity.CustomFirstPersonArmEntity;
import com.elfmcys.ysm.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.ysm.geckolib3.core.util.Color;
import com.elfmcys.ysm.geckolib3.geo.CustomTranslucentRenderType;
import com.elfmcys.ysm.natives.render.DeferredModelDraw;
import com.elfmcys.ysm.natives.render.HostProjection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.common.NeoForge;

public class CustomFirstPersonArmRenderer {
    private CustomFirstPersonArmEntity armEntity = null;

    /**
     * @return true when a YSM arm was submitted and vanilla arm rendering should be cancelled
     */
    public boolean render(LocalPlayer player, PlayerAnimatableCapability cap, HumanoidArm arm,
                          PoseStack poseStack, SubmitNodeCollector collector,
                          int packedLight, float partialTick) {
        if (armEntity == null || armEntity.getEntity() != player || armEntity.getMainModelEntity() != cap) {
            if (armEntity != null) {
                armEntity.reset();
            }
            armEntity = new CustomFirstPersonArmEntity(player, cap);
        }

        armEntity.checkModelUpdate();
        var data = armEntity.updateForArm(partialTick, arm);
        if (data == null || !data.modelState.isValid() || collector == null || !HostProjection.hasCapture()) {
            return false;
        }

        var renderEvent = new SpecialPlayerRenderEvent(player, cap, cap.getModelId());
        if (NeoForge.EVENT_BUS.post(renderEvent).isCanceled()) {
            return false;
        }

        var textureLocation = renderEvent.getTextureLocationOverride() == null ? cap.getTextureLocation() : renderEvent.getTextureLocationOverride();
        var renderType = CustomTranslucentRenderType.create(textureLocation);

        poseStack.pushPose();
        try {
            // First-person assets are authored for YSM's screen-space offset,
            // rather than for the vanilla player model's shoulder pivot.
            poseStack.translate(arm == HumanoidArm.LEFT ? 0.25 : -0.25, 1.8, 0);
            poseStack.scale(-1, -1, 1);

            return DeferredModelDraw.submit(collector, poseStack, renderType, data.modelState, packedLight, OverlayTexture.NO_OVERLAY,
                    Color.WHITE.getColor(), data.ctx.nativeType(), 0);
        } finally {
            poseStack.popPose();
        }
    }
}
