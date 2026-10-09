// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.backpack.sophisticated;

import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackLayerRenderer;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class YsmBackpackLayerRenderer extends GeoLayerRenderer<CustomPlayerEntity> {
    // The provider reads model type/body transforms only; YSM supplies the locator pose.
    private final EntityModel<LivingEntityRenderState> model =
            new EntityModel<>(new ModelPart(List.of(), Map.of())) {};

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector,
                       CustomPlayerEntity animatable, GeoRenderData renderData,
                       @Nullable AvatarRenderState avatarState, int packedLight, int overlay) {
        var player = animatable.getEntity();
        var backpack = SophisticatedCompat.getBackpackItemStack(player);
        if (backpack == null || backpack.isEmpty()) {
            return;
        }
        var state = new LivingEntityRenderState();
        state.isBaby = player.isBaby();
        BackpackLayerRenderer.RENDER_STATE_MODIFIER.accept(player, state);
        // The custom locator already supplies the chest offset. The provider's
        // armor-slot branch selects WEARS_ARMOR=false, matching the old draw's
        // explicit false; it does not change where we obtained the item.
        BackpackLayerRenderer.addBackpackRenderState(state, player,
                new PlayerInventoryProvider.RenderInfo(backpack, true));
        renderData.modelState.visitLocatorGroup(PlayerLocator.get().backpack, poseStack, locatorPose -> {
            var local = new PoseStack();
            local.last().set(locatorPose.last());
            local.rotate(Axis.XP.rotationDegrees(180));
            local.rotate(Axis.YP.rotationDegrees(180));
            local.translate(0, -0.1, 0);
            BackpackLayerRenderer.submitBackpack(model, state, local, collector, packedLight);
        });
    }
}
