// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.layer;

import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.parrot.Parrot;
import org.jspecify.annotations.Nullable;

public class CustomParrotOnShoulderLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    private @Nullable ParrotModel model;

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, CustomPlayerEntity animatable, GeoRenderData renderData,
                       @Nullable AvatarRenderState avatarState, int packedLight, int overlay) {
        if (avatarState == null) {
            return;
        }
        submitShoulder(poseStack, collector, renderData, avatarState, packedLight, avatarState.parrotOnLeftShoulder, true);
        submitShoulder(poseStack, collector, renderData, avatarState, packedLight, avatarState.parrotOnRightShoulder, false);
    }

    private void submitShoulder(PoseStack poseStack, SubmitNodeCollector collector, GeoRenderData renderData,
                                AvatarRenderState avatarState, int packedLight, Parrot.@Nullable Variant variant, boolean left) {
        if (variant == null) {
            return;
        }
        var locator = left ? PlayerLocator.get().leftShoulder : PlayerLocator.get().rightShoulder;
        ParrotRenderState parrotState = new ParrotRenderState();
        parrotState.pose = ParrotModel.Pose.ON_SHOULDER;
        parrotState.ageInTicks = avatarState.ageInTicks;
        parrotState.walkAnimationPos = avatarState.walkAnimationPos;
        parrotState.walkAnimationSpeed = avatarState.walkAnimationSpeed;
        parrotState.yRot = avatarState.yRot;
        parrotState.xRot = avatarState.xRot;
        renderData.modelState.visitLocatorGroup(locator, poseStack, locatorPose -> {
            locatorPose.translate(0, 1.5, 0);
            locatorPose.rotate(Axis.ZP.rotationDegrees(180));
            collector.submitModel(model(), parrotState, locatorPose, ParrotRenderer.getVariantTexture(variant),
                    packedLight, OverlayTexture.NO_OVERLAY, avatarState.outlineColor);
        });
    }

    private ParrotModel model() {
        ParrotModel current = this.model;
        if (current == null) {
            current = new ParrotModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PARROT));
            this.model = current;
        }
        return current;
    }
}
