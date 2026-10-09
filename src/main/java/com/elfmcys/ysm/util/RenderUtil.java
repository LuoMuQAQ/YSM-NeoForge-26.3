// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.util;

import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.FirstPersonCompat;
import com.elfmcys.ysm.client.compat.IrisCompat;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.entity.IPreviewEntity;
import com.elfmcys.ysm.client.gui.YsmModelPreviewState;
import com.elfmcys.ysm.client.renderer.replace.EntityRendererReplace;
import com.elfmcys.ysm.client.renderer.YsmSubmitContext;
import com.elfmcys.ysm.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.ysm.geckolib3.geo.RenderContext;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.elfmcys.ysm.geckolib3.model.AnimatedGeoModel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class RenderUtil {
    private static boolean renderingInInventory = false;
    private static boolean renderingInPaperDoll = false;
    private static boolean renderingLevel = false;

    private RenderUtil() {
    }

    public static void setRenderingInInventory(boolean value) {
        renderingInInventory = value;
    }

    public static void setRenderingInPaperDoll(boolean renderingEntitiesInPaperDoll) {
        renderingInPaperDoll = renderingEntitiesInPaperDoll;
    }

    public static void setRenderingLevel(boolean renderingLevel) {
        RenderUtil.renderingLevel = renderingLevel;
    }

    public static boolean isRenderingLevel() {
        RenderSystem.assertOnRenderThread();
        return renderingLevel;
    }

    public static float guiPartialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    public static RenderContext extractRenderContext() {
        RenderSystem.assertOnRenderThread();
        if (YsmSubmitContext.isInventory()) {
            return new RenderContext(false, false, false, true, false, false, false);
        }
        return new RenderContext(
                renderingLevel && !renderingInInventory && !renderingInPaperDoll && !FirstPersonCompat.isRenderingPlayer(),
                IrisCompat.isRenderingShadow(),
                FirstPersonCompat.isRenderingPlayer(),
                renderingInInventory,
                renderingInPaperDoll,
                false,
                false);
    }

    public static void adjustPassengerPosition(Entity entity, PoseStack poseStack, float partialTicks) {
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) {
            return;
        }
        VehicleAnimatableCapabilityProvider.get(vehicle).ifPresent(vehicleCap -> {
            if (!vehicleCap.isInitialized() || !vehicleCap.isModelPresent()) {
                return;
            }
            int index = vehicle.getPassengers().indexOf(entity);
            if (index < 0) {
                return;
            }
            AnimatedGeoModel loadedGeoModel = vehicleCap.getLoadedGeoModel();
            if (loadedGeoModel == null) {
                return;
            }
            float rawVehicleYaw = Mth.lerp(partialTicks, vehicle.yRotO, vehicle.getYRot());
            float vehicleYaw = EntityRendererReplace.getYaw(vehicle, rawVehicleYaw, partialTicks);
            poseStack.rotate(Axis.YP.rotationDegrees(180 - vehicleYaw));
            poseStack.rotate(Axis.YN.rotationDegrees(180 - vehicleYaw));

            double relativeY = vehicle.getPassengerRidingPosition(entity).y - vehicle.getY()
                    - entity.getVehicleAttachmentPoint(vehicle).y;
            double yOffset = -relativeY;
            boolean playerHasCap = entity instanceof Player player && PlayerAnimatableCapabilityProvider.get(player).isPresent();
            if (playerHasCap || TlmClientCompat.hasMaidCap(entity)) {
                yOffset = yOffset - 0.5;
            }
            poseStack.translate(0, yOffset, 0);
        });
    }

    public static <T extends LivingEntity, TAnimatable extends AnimatableEntity<T> & IPreviewEntity> void renderTextureScreenEntity(
            GuiGraphicsExtractor graphics, float anchorX, float anchorY, float scale, float pitch, float yaw, float partialTicks,
            TAnimatable entity, GeoReplacedEntityRenderer<T, ? super TAnimatable> renderer, boolean showGround) {
        var rect = previewViewport(graphics);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.submitPictureInPictureRenderState(YsmModelPreviewState.texture(
                entity, renderer, partialTicks, pitch, yaw, anchorX, anchorY,
                rect.left(), rect.top(), rect.right(), rect.bottom(), scale, rect));
    }

    public static <T extends LivingEntity, TAnimatable extends CustomHumanoidEntity<T>> void renderModelInGui(
            GuiGraphicsExtractor graphics, float anchorX, float anchorY, float scale, float partialTicks,
            TAnimatable animatable, GeoReplacedEntityRenderer<T, TAnimatable> renderer,
            boolean disablePreviewRotation, boolean disableEquipments) {
        var rect = previewViewport(graphics);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.submitPictureInPictureRenderState(YsmModelPreviewState.model(
                animatable, renderer, partialTicks, disablePreviewRotation, disableEquipments,
                anchorX, anchorY + (disablePreviewRotation ? 5.5F : 0.0F),
                rect.left(), rect.top(), rect.right(), rect.bottom(), scale, rect));
    }

    public static void renderExtraPlayerEntity(GuiGraphicsExtractor graphics, LocalPlayer player, double anchorX, double anchorY,
                                               float scale, float yawOffset, int ignoredDepth, float partialTicks) {
        var rect = previewViewport(graphics);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        graphics.submitPictureInPictureRenderState(YsmModelPreviewState.player(
                player, partialTicks, yawOffset, (float) anchorX + scale * 0.5F, (float) anchorY + scale * 2.0F,
                rect.left(), rect.top(), rect.right(), rect.bottom(), scale, rect));
    }

    private static ScreenRectangle previewViewport(GuiGraphicsExtractor graphics) {
        var scissor = graphics.peekScissorStack();
        return scissor == null ? new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight()) : scissor;
    }
}
