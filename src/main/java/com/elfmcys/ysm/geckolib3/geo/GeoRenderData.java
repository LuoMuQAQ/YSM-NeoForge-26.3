// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo;

import com.elfmcys.ysm.geckolib3.geo.animated.GeoModelState;
import com.elfmcys.ysm.geckolib3.model.provider.data.EntityModelData;
import net.minecraft.resources.Identifier;

public class GeoRenderData {
    public final GeoModelState modelState = new GeoModelState();

    public Identifier texture;
    public float widthScale;
    public float heightScale;
    public boolean renderLayersFirst;
    public RenderContext ctx;

    public float partialTicks;
    public EntityModelData animationData;
}
