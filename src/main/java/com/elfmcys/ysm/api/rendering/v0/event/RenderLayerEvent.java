// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.api.rendering.v0.event;

import com.elfmcys.ysm.api.rendering.v0.TargetKind;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

public class RenderLayerEvent extends Event implements IModBusEvent, ICancellableEvent {
    private final Object target;
    private final TargetKind targetKind;
    private final GeoRenderData renderData;
    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;
    private final int packedLight;
    private final int overlay;

    public RenderLayerEvent(Object target, TargetKind targetKind, GeoRenderData renderData, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, int overlay) {
        this.target = target;
        this.targetKind = targetKind;
        this.renderData = renderData;
        this.poseStack = poseStack;
        this.collector = collector;
        this.packedLight = packedLight;
        this.overlay = overlay;
    }

    public Object target() {
        return target;
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    public GeoRenderData renderData() {
        return renderData;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    public int packedLight() {
        return packedLight;
    }

    public int overlay() {
        return overlay;
    }
}
