// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.replace;

import com.elfmcys.ysm.capability.ProjectileAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

public class FishingHookRendererReplace {
    public static boolean submit(FishingHook hook, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        return ProjectileAnimatableCapabilityProvider.get(hook).map(cap -> {
            if (cap.isInitialized() && cap.isModelPresent()) {
                hook.setXRot(0);
                hook.xRotO = 0;
                RegisterEntityRenderersEvent.getProjectRenderer().submitAnimatable(
                        cap, hook.getYRot(), state.partialTick, poseStack, collector, state.lightCoords, state.outlineColor);
                Player owner = hook.getPlayerOwner();
                if (owner != null) {
                    submitLine(hook, owner, state.partialTick, poseStack, collector);
                }
                return false;
            }
            return true;
        }).orElse(true);
    }

    private static void submitLine(FishingHook hook, Player owner, float partialTick, PoseStack poseStack, SubmitNodeCollector collector) {
        float swing = Mth.sin(Mth.sqrt(owner.getSwingAnimation(partialTick)) * (float) Math.PI);
        Vec3 origin = handPosition(owner, swing, partialTick).subtract(hook.getPosition(partialTick).add(0.0, 0.25, 0.0));
        float xa = (float) origin.x;
        float ya = (float) origin.y;
        float za = (float) origin.z;
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (int step = 0; step < 16; step++) {
                float start = step / 16.0F;
                float end = (step + 1) / 16.0F;
                lineVertex(xa, ya, za, buffer, pose, start, end, width);
                lineVertex(xa, ya, za, buffer, pose, end, start, width);
            }
        });
    }

    private static Vec3 handPosition(Player owner, float swing, float partialTick) {
        int invert = holdingArm(owner) == HumanoidArm.RIGHT ? 1 : -1;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher.camera != null && dispatcher.options.getCameraType().isFirstPerson() && owner == Minecraft.getInstance().player) {
            float fov = dispatcher.options.fov().get().intValue();
            Vec3 view = dispatcher.camera.getNearPlane(fov)
                    .getPointOnPlane(invert * 0.525F, -0.1F)
                    .scale(960.0 / fov)
                    .yRot(swing * 0.5F)
                    .xRot(-swing * 0.7F);
            return owner.getEyePosition(partialTick).add(view);
        }
        float bodyRot = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot) * ((float) Math.PI / 180.0F);
        double sin = Mth.sin(bodyRot);
        double cos = Mth.cos(bodyRot);
        float playerScale = owner.getScale();
        double rightOffset = invert * 0.35 * playerScale;
        double forwardOffset = 0.8 * playerScale;
        float yOffset = owner.isCrouching() ? -0.1875F : 0.0F;
        return owner.getEyePosition(partialTick).add(
                -cos * rightOffset - sin * forwardOffset,
                yOffset - 0.45 * playerScale,
                -sin * rightOffset + cos * forwardOffset);
    }

    private static HumanoidArm holdingArm(Player owner) {
        return owner.getMainHandItem().canPerformAction(ItemAbilities.FISHING_ROD_CAST)
                ? owner.getMainArm()
                : owner.getMainArm().getOpposite();
    }

    private static void lineVertex(float xa, float ya, float za, VertexConsumer buffer, PoseStack.Pose pose,
                                   float fraction, float next, float width) {
        float x = xa * fraction;
        float y = ya * (fraction * fraction + fraction) * 0.5F + 0.25F;
        float z = za * fraction;
        float nx = xa * next - x;
        float ny = ya * (next * next + next) * 0.5F + 0.25F - y;
        float nz = za * next - z;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x, y, z).setColor(-16777216).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }
}
