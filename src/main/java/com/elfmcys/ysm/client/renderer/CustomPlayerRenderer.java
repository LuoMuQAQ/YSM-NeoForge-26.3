// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer;

import com.elfmcys.ysm.capability.PlayerAnimatableCapability;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.swarfare.SWarfareCompat;
import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.renderer.layer.CustomParrotOnShoulderLayer;
import com.elfmcys.ysm.client.renderer.layer.CustomPlayerElytraLayer;
import com.elfmcys.ysm.client.renderer.layer.CustomPlayerHeadLayer;
import com.elfmcys.ysm.client.renderer.layer.CustomPlayerItemInHandLayer;
import com.elfmcys.ysm.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.ysm.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

public class CustomPlayerRenderer extends GeoReplacedEntityRenderer<Player, CustomPlayerEntity> {
    public CustomPlayerRenderer() {
        addLayer(new CustomPlayerItemInHandLayer());
        addLayer(new CustomPlayerElytraLayer());
        addLayer(new CustomParrotOnShoulderLayer());
        addLayer(new CustomPlayerHeadLayer());
    }

    public void submit(Player player, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (SWarfareCompat.shouldHidePlayerRender(player)) {
            return;
        }
        PlayerAnimatableCapability cap = PlayerAnimatableCapabilityProvider.get(player).orElse(null);
        if (cap == null) {
            return;
        }
        cap.checkModelUpdate();
        var event = new SpecialPlayerRenderEvent(player, cap, cap.getModelId());
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return;
        }
        submitAnimatable(cap, event.getTextureLocationOverride(), state.partialTick, poseStack, collector, state.lightCoords, state);
        submitNameTags(state, poseStack, collector);
    }

    private static void submitNameTags(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        var camera = YsmSubmitContext.camera();
        if (camera == null || (state.scoreText == null && state.nameTag == null)) {
            return;
        }
        int offset = state.showExtraEars ? -10 : 0;
        poseStack.pushPose();
        try {
            if (state.scoreText != null) {
                collector.submitNameTag(poseStack, state.nameTagAttachment, offset, state.scoreText, !state.isDiscrete, state.lightCoords, camera);
                poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
            }
            if (state.nameTag != null) {
                collector.submitNameTag(poseStack, state.nameTagAttachment, offset, state.nameTag, !state.isDiscrete, state.lightCoords, camera);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
