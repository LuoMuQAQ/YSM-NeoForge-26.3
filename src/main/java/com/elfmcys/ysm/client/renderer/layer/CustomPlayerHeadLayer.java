// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.layer;

import com.elfmcys.ysm.client.compat.simplehat.SimpleHatsCompat;
import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class CustomPlayerHeadLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, CustomPlayerEntity animatable, GeoRenderData renderData,
                       @Nullable AvatarRenderState avatarState, int packedLight, int overlay) {
        Player player = animatable.getEntity();
        int outline = avatarState == null ? 0 : avatarState.outlineColor;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty() && !isArmorHead(head)) {
            submitHeadItem(poseStack, collector, packedLight, overlay, outline, renderData, player, head);
        }
        ItemStack curiosHead = SimpleHatsCompat.getCuriosHead(player);
        if (curiosHead != null && !curiosHead.isEmpty()) {
            submitHeadItem(poseStack, collector, packedLight, overlay, outline, renderData, player, curiosHead);
        }
    }

    private static boolean isArmorHead(ItemStack itemStack) {
        return itemStack.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    private static void submitHeadItem(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, int overlay, int outline,
                                        GeoRenderData data, Player player, ItemStack head) {
        var itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(itemState, head, ItemDisplayContext.HEAD, player);
        data.modelState.visitLocatorGroup(PlayerLocator.get().head, poseStack, locatorPose -> {
            locatorPose.scale(0.625F, 0.625F, 0.625F);
            locatorPose.translate(0.0F, 0.25F, 0.0F);
            itemState.submit(locatorPose, collector, packedLight, overlay, outline);
        });
    }
}
