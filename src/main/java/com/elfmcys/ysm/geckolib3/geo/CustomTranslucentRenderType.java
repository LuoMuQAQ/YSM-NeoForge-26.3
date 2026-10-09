// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Entity translucent output without host upload sorting. Native already sorts
 * this model's translucent quads, and a second sort would change that order.
 */
public final class CustomTranslucentRenderType {
    private static final Function<Identifier, RenderType> CUSTOM_TRANSLUCENT = Util.memoize(texture -> {
        var state = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                .setOitPipelines(RenderPipelines.OIT_ENTITY)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup();
        return RenderType.create("entity_translucent_ysm", state);
    });

    private CustomTranslucentRenderType() {
    }

    public static RenderType create(Identifier textureLocation) {
        return CUSTOM_TRANSLUCENT.apply(textureLocation);
    }
}
