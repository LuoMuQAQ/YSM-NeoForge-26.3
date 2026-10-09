// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo;

import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;

public abstract class GeoLayerRenderer<T extends AnimatableEntity<?>> {
    public abstract void submit(PoseStack poseStack, SubmitNodeCollector collector, T animatable, GeoRenderData renderData,
                                @Nullable AvatarRenderState avatarState, int packedLight, int overlay);
}
