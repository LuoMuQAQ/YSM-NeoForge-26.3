// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.renderer.YsmEntityLookup;
import com.elfmcys.ysm.client.renderer.YsmSubmitContext;
import com.elfmcys.ysm.client.renderer.replace.EntityRendererReplace;
import com.elfmcys.ysm.client.renderer.replace.FishingHookRendererReplace;
import com.elfmcys.ysm.client.renderer.replace.ProjectileRendererReplace;
import com.elfmcys.ysm.config.ClientConfig;
import com.elfmcys.ysm.util.RenderUtil;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @WrapMethod(method = "submit")
    private void ysm$submitScope(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                PoseStack poseStack, SubmitNodeCollector collector, Operation<Void> original) {
        try (var ignored = YsmSubmitContext.begin(state, camera)) {
            original.call(state, camera, x, y, z, poseStack, collector);
        }
    }

    @WrapWithCondition(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
            )
    )
    private boolean ysm$replaceSubmit(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector collector, CameraRenderState camera) {
        if (!YesSteveModel.isAvailable()) {
            return true;
        }
        Entity entity = YsmEntityLookup.entity(state);
        if (entity == null) {
            return true;
        }
        if (entity instanceof Projectile projectile && !ClientConfig.DISABLE_PROJECTILE_MODEL.get()) {
            if (projectile instanceof FishingHook hook) {
                return FishingHookRendererReplace.submit(hook, state, poseStack, collector);
            }
            return ProjectileRendererReplace.submit(projectile, state, poseStack, collector);
        }
        if (!ClientConfig.DISABLE_VEHICLE_MODEL.get()) {
            RenderUtil.adjustPassengerPosition(entity, poseStack, state.partialTick);
            return EntityRendererReplace.submit(entity, state, poseStack, collector);
        }
        return true;
    }
}
