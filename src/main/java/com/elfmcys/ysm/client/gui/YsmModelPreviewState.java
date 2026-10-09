package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public final class YsmModelPreviewState implements PictureInPictureRenderState {
    public enum Kind {
        MODEL,
        TEXTURE,
        PLAYER
    }

    private final Kind kind;
    private final @Nullable AnimatableEntity<?> animatable;
    private final @Nullable GeoReplacedEntityRenderer<?, ?> renderer;
    private final @Nullable LivingEntity player;
    private final float partialTick;
    private final float pitch;
    private final float yaw;
    private final boolean disablePreviewRotation;
    private final boolean disableEquipments;
    private final float anchorX;
    private final float anchorY;
    private final int x0;
    private final int y0;
    private final int x1;
    private final int y1;
    private final float scale;
    private final @Nullable ScreenRectangle scissorArea;
    private final @Nullable ScreenRectangle bounds;

    private YsmModelPreviewState(Kind kind, @Nullable AnimatableEntity<?> animatable, @Nullable GeoReplacedEntityRenderer<?, ?> renderer,
                                  @Nullable LivingEntity player, float partialTick, float pitch, float yaw,
                                  boolean disablePreviewRotation, boolean disableEquipments,
                                  float anchorX, float anchorY, int x0, int y0, int x1, int y1,
                                  float scale, @Nullable ScreenRectangle scissorArea) {
        this.kind = kind;
        this.animatable = animatable;
        this.renderer = renderer;
        this.player = player;
        this.partialTick = partialTick;
        this.pitch = pitch;
        this.yaw = yaw;
        this.disablePreviewRotation = disablePreviewRotation;
        this.disableEquipments = disableEquipments;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.scale = scale;
        this.scissorArea = scissorArea;
        this.bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }

    public static YsmModelPreviewState model(AnimatableEntity<?> animatable, GeoReplacedEntityRenderer<?, ?> renderer,
                                             float partialTick, boolean disablePreviewRotation, boolean disableEquipments,
                                             float anchorX, float anchorY, int x0, int y0, int x1, int y1,
                                             float scale, @Nullable ScreenRectangle scissorArea) {
        return new YsmModelPreviewState(Kind.MODEL, animatable, renderer, null, partialTick, 0, 0,
                disablePreviewRotation, disableEquipments, anchorX, anchorY, x0, y0, x1, y1, scale, scissorArea);
    }

    public static YsmModelPreviewState texture(AnimatableEntity<?> animatable, GeoReplacedEntityRenderer<?, ?> renderer,
                                               float partialTick, float pitch, float yaw,
                                               float anchorX, float anchorY, int x0, int y0, int x1, int y1,
                                               float scale, @Nullable ScreenRectangle scissorArea) {
        return new YsmModelPreviewState(Kind.TEXTURE, animatable, renderer, null, partialTick, pitch, yaw,
                false, false, anchorX, anchorY, x0, y0, x1, y1, scale, scissorArea);
    }

    public static YsmModelPreviewState player(LivingEntity player, float partialTick, float yawOffset,
                                              float anchorX, float anchorY, int x0, int y0, int x1, int y1,
                                              float scale, @Nullable ScreenRectangle scissorArea) {
        return new YsmModelPreviewState(Kind.PLAYER, null, null, player, partialTick, 0, yawOffset,
                false, false, anchorX, anchorY, x0, y0, x1, y1, scale, scissorArea);
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable AnimatableEntity<?> animatable() {
        return animatable;
    }

    public @Nullable GeoReplacedEntityRenderer<?, ?> renderer() {
        return renderer;
    }

    public @Nullable LivingEntity player() {
        return player;
    }

    public float partialTick() {
        return partialTick;
    }

    public float pitch() {
        return pitch;
    }

    public float yaw() {
        return yaw;
    }

    public boolean disablePreviewRotation() {
        return disablePreviewRotation;
    }

    public boolean disableEquipments() {
        return disableEquipments;
    }

    public float anchorX() {
        return anchorX;
    }

    public float anchorY() {
        return anchorY;
    }

    @Override
    public int x0() {
        return x0;
    }

    @Override
    public int y0() {
        return y0;
    }

    @Override
    public int x1() {
        return x1;
    }

    @Override
    public int y1() {
        return y1;
    }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }
}
