// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.api.rendering.v0.event;

import com.elfmcys.ysm.api.rendering.v0.TargetKind;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

public class RenderModelEvent extends Event implements IModBusEvent, ICancellableEvent {
    private final Object target;
    private final TargetKind targetKind;
    private final GeoRenderData renderData;
    private final SubmitNodeCollector collector;
    private final RenderType renderType;
    private final PoseStack poseStack;
    private final int light;
    private final int overlay;
    private final int color;
    private final int outlineColor;

    public RenderModelEvent(Object target, TargetKind targetKind, GeoRenderData renderData, SubmitNodeCollector collector, RenderType renderType, PoseStack poseStack, int light, int overlay, int color) {
        this(target, targetKind, renderData, collector, renderType, poseStack, light, overlay, color, 0);
    }

    public RenderModelEvent(Object target, TargetKind targetKind, GeoRenderData renderData, SubmitNodeCollector collector, RenderType renderType, PoseStack poseStack, int light, int overlay, int color, int outlineColor) {
        this.target = target;
        this.targetKind = targetKind;
        this.renderData = renderData;
        this.collector = collector;
        this.renderType = renderType;
        this.poseStack = poseStack;
        this.light = light;
        this.overlay = overlay;
        this.color = color;
        this.outlineColor = outlineColor;
    }

    /// 当前只有 Entity，以后说不定有别的
    public Object target() {
        return target;
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    public GeoRenderData renderData() {
        return renderData;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    public RenderType renderType() {
        return renderType;
    }

    public PoseStack pose() {
        return poseStack;
    }

    public int light() {
        return light;
    }

    public int overlay() {
        return overlay;
    }

    public int color() {
        return color;
    }

    public int outlineColor() {
        return outlineColor;
    }
}
