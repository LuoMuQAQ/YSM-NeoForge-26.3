// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.renderer.replace;

import com.elfmcys.ysm.capability.ProjectileAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.projectile.Projectile;

public class ProjectileRendererReplace {
    public static boolean submit(Projectile entity, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        return ProjectileAnimatableCapabilityProvider.get(entity).map(cap -> {
            if (cap.isInitialized() && cap.isModelPresent()) {
                RegisterEntityRenderersEvent.getProjectRenderer().submitAnimatable(
                        cap, entity.getYRot(), state.partialTick, poseStack, collector, state.lightCoords, state.outlineColor);
                return false;
            }
            return true;
        }).orElse(true);
    }
}
