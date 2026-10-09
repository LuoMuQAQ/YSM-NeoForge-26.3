// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.layer;

import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.elfmcys.ysm.util.EquipmentUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Cutout elytra at the model locator. Equipment-asset layers and foil are not submitted:
 * the host equipment renderer needs an {@code EquipmentAssetManager} that Minecraft does not expose.
 */
public class CustomPlayerElytraLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    private static final Identifier WINGS_LOCATION = Identifier.withDefaultNamespace("textures/entity/elytra.png");
    private @Nullable ElytraModel elytraModel;

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, CustomPlayerEntity animatable, GeoRenderData renderData,
                       @Nullable AvatarRenderState avatarState, int packedLight, int overlay) {
        if (avatarState == null) {
            return;
        }
        Player player = animatable.getEntity();
        ItemStack stack = EquipmentUtil.getEquippedElytraItem(player);
        if (stack.isEmpty()) {
            return;
        }
        Identifier texture = wingsTexture(avatarState);
        ElytraModel model = model();
        renderData.modelState.visitLocatorGroup(PlayerLocator.get().elytra, poseStack, locatorPose -> {
            locatorPose.translate(0, 1.5, 0);
            locatorPose.rotate(Axis.ZP.rotationDegrees(180));
            locatorPose.scale(2.0F, 2.0F, 2.0F);
            collector.submitModel(model, avatarState, locatorPose, RenderTypes.entityCutout(texture),
                    packedLight, OverlayTexture.NO_OVERLAY, avatarState.outlineColor);
        });
    }

    private ElytraModel model() {
        ElytraModel model = this.elytraModel;
        if (model == null) {
            model = new ElytraModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.ELYTRA));
            this.elytraModel = model;
        }
        return model;
    }

    private static Identifier wingsTexture(AvatarRenderState state) {
        var skin = state.skin;
        if (skin.elytra() != null) {
            return skin.elytra().texturePath();
        }
        if (skin.cape() != null && state.showCape) {
            return skin.cape().texturePath();
        }
        return WINGS_LOCATION;
    }
}
