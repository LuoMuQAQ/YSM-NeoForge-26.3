// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.layer;

import com.elfmcys.ysm.client.compat.slashblade.SlashBladeCompat;
import com.elfmcys.ysm.client.compat.slashblade.SlashBladeRender;
import com.elfmcys.ysm.client.compat.swarfare.SWarfareCompat;
import com.elfmcys.ysm.client.compat.tacz.TACZCompat;
import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class CustomPlayerItemInHandLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, CustomPlayerEntity animatable, GeoRenderData renderData,
                       @Nullable AvatarRenderState avatarState, int packedLight, int overlay) {
        LivingEntity entity = animatable.getEntity();
        ItemStack mainHandItem = entity.getMainHandItem();
        if (!mainHandItem.isEmpty() && renderData.modelState.locatorGroupSize(PlayerLocator.get().rightHand) > 0) {
            if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
                SlashBladeRender.renderMainhandSlashBlade(entity, renderData, poseStack, collector, packedLight, mainHandItem);
            } else {
                boolean flashState = TACZCompat.openFlashShellRender(entity, mainHandItem);
                try {
                    submitHeldItem(entity, mainHandItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT,
                            renderData, poseStack, collector, packedLight, overlay, outline(avatarState));
                } finally {
                    if (flashState) TACZCompat.stopFlashShellRender(mainHandItem);
                }
            }
        }

        ItemStack offhandItem = entity.getOffhandItem();
        if (!offhandItem.isEmpty() && renderData.modelState.locatorGroupSize(PlayerLocator.get().leftHand) > 0) {
            if (SlashBladeCompat.isSlashBladeItem(offhandItem)) {
                SlashBladeRender.renderOffhandSlashBlade(renderData, poseStack, collector, packedLight, offhandItem);
            } else {
                boolean submitted = TACZCompat.renderOffsetHand(offhandItem, renderData, entity, poseStack, collector, packedLight)
                        || SWarfareCompat.renderOffsetHand(offhandItem, renderData, entity, poseStack, collector, packedLight);
                if (!submitted) {
                    submitHeldItem(entity, offhandItem, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
                            renderData, poseStack, collector, packedLight, overlay, outline(avatarState));
                }
            }
        }
    }

    private static void submitHeldItem(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm,
                                        GeoRenderData data, PoseStack poseStack, SubmitNodeCollector collector,
                                        int packedLight, int overlay, int outline) {
        if (itemStack.isEmpty()) {
            return;
        }
        boolean leftHand = arm == HumanoidArm.LEFT;
        var locator = leftHand ? PlayerLocator.get().leftHand : PlayerLocator.get().rightHand;
        var itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(itemState, itemStack, displayContext, entity);
        data.modelState.visitLocatorGroup(locator, poseStack, locatorPose -> {
            locatorPose.translate(0, -0.0625, -0.1);
            locatorPose.rotate(Axis.XP.rotationDegrees(-90.0F));
            if (SWarfareCompat.isGun(itemStack)) {
                locatorPose.translate(0.1, 0, 0);
                locatorPose.scale(1.25F, 1.25F, 1.25F);
            }
            itemState.submit(locatorPose, collector, packedLight, overlay, outline);
        });
    }

    private static int outline(@Nullable AvatarRenderState avatarState) {
        return avatarState == null ? 0 : avatarState.outlineColor;
    }
}
