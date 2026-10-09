// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.layer;

import com.elfmcys.ysm.client.entity.CustomPlayerEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.elfmcys.ysm.util.EquipmentUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Submits the host's WINGS equipment layers at the model's elytra locators. */
public class CustomPlayerElytraLayer extends GeoLayerRenderer<CustomPlayerEntity> {
    private final ElytraModel elytraModel;
    private final ElytraModel elytraBabyModel;
    private final EquipmentLayerRenderer equipmentRenderer;

    public CustomPlayerElytraLayer(EntityRendererProvider.Context context) {
        this.elytraModel = new ElytraModel(context.bakeLayer(ModelLayers.ELYTRA));
        this.elytraBabyModel = new ElytraModel(context.bakeLayer(ModelLayers.ELYTRA_BABY));
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

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
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }
        Identifier texture = wingsTexture(avatarState);
        ElytraModel model = avatarState.isBaby ? this.elytraBabyModel : this.elytraModel;
        renderData.modelState.visitLocatorGroup(PlayerLocator.get().elytra, poseStack, locatorPose -> {
            // visitLocatorGroup already anchors this pose at the animated pivot.
            // The host wing root is at the shoulders (y = 0); only flip its Y axis.
            locatorPose.rotate(Axis.ZP.rotationDegrees(180));
            // The host model already uses block units. Keep the authored locator
            // scale and avoid doubling the vanilla wing dimensions.
            equipmentRenderer.renderLayers(EquipmentClientInfo.LayerType.WINGS, equippable.assetId().get(),
                    model, avatarState, stack, locatorPose, collector, packedLight, texture, avatarState.outlineColor, 0);
        });
    }

    private static @Nullable Identifier wingsTexture(AvatarRenderState state) {
        var skin = state.skin;
        if (skin.elytra() != null) {
            return skin.elytra().texturePath();
        }
        if (skin.cape() != null && state.showCape) {
            return skin.cape().texturePath();
        }
        // A null player override selects the resource pack's WINGS equipment asset.
        return null;
    }
}
