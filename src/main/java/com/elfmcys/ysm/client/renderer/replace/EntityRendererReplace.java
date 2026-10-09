// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.replace;

import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.phys.Vec3;

public class EntityRendererReplace {
    public static boolean submit(Entity entity, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        return VehicleAnimatableCapabilityProvider.get(entity).map(cap -> {
            if (cap.isInitialized() && cap.isModelPresent()) {
                float yaw = getYaw(entity, entity.getYRot(), state.partialTick);
                RegisterEntityRenderersEvent.getVehicleRenderer().submitAnimatable(
                        cap, yaw, state.partialTick, poseStack, collector, state.lightCoords, state.outlineColor);
                return false;
            }
            return true;
        }).orElse(true);
    }

    public static float getYaw(Entity entity, float yawIn, float partialTick) {
        float yaw = yawIn;

        if (entity instanceof LivingEntity livingEntity) {
            yaw = getLivingEntityYaw(livingEntity, partialTick);
        } else if (entity instanceof AbstractMinecart minecart) {
            yaw = getMinecartYaw(minecart, partialTick, yaw);
        }
        return yaw;
    }

    private static float getLivingEntityYaw(LivingEntity livingEntity, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, livingEntity.yHeadRotO, livingEntity.yHeadRot);

        boolean shouldSit = livingEntity.isPassenger() && (livingEntity.getVehicle() != null && livingEntity.getVehicle().shouldRiderSit());
        if (shouldSit && livingEntity.getVehicle() instanceof LivingEntity vehicle) {
            yaw = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);

            float wrappedYawDiff = Mth.wrapDegrees(headYaw - yaw);
            wrappedYawDiff = Mth.clamp(wrappedYawDiff, -85.0F, 85.0F);

            yaw = headYaw - wrappedYawDiff;
            if (wrappedYawDiff * wrappedYawDiff > 2500.0F) {
                yaw += wrappedYawDiff * 0.2F;
            }
        }
        return yaw;
    }

    private static float getMinecartYaw(AbstractMinecart minecart, float partialTick, float yaw) {
        if (minecart.getBehavior() instanceof NewMinecartBehavior behavior) {
            if (behavior.cartHasPosRotLerp()) {
                return behavior.getCartLerpYRot(partialTick);
            }
            return minecart.getYRot();
        }
        if (!(minecart.getBehavior() instanceof OldMinecartBehavior behavior)) {
            return yaw;
        }
        double interpX = Mth.lerp(partialTick, minecart.xOld, minecart.getX());
        double interpY = Mth.lerp(partialTick, minecart.yOld, minecart.getY());
        double interpZ = Mth.lerp(partialTick, minecart.zOld, minecart.getZ());
        Vec3 basePos = behavior.getPos(interpX, interpY, interpZ);
        if (basePos != null) {
            Vec3 posFront = behavior.getPosOffs(interpX, interpY, interpZ, 0.3);
            Vec3 posBack = behavior.getPosOffs(interpX, interpY, interpZ, -0.3);
            if (posFront == null) {
                posFront = basePos;
            }

            if (posBack == null) {
                posBack = basePos;
            }
            Vec3 offset = posBack.add(-posFront.x, -posFront.y, -posFront.z);
            if (offset.length() != 0) {
                yaw = (float) (Math.atan2(offset.z, offset.x) * 180 / Math.PI);
            }
        }
        return yaw;
    }
}
