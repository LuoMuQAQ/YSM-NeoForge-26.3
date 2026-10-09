// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.api.rendering.v0.TargetKind;
import com.elfmcys.ysm.api.rendering.v0.event.RenderLayerEvent;
import com.elfmcys.ysm.api.rendering.v0.event.RenderModelEvent;
import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.renderer.YsmSubmitContext;
import com.elfmcys.ysm.geckolib3.core.util.Color;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public abstract class GeoReplacedEntityRenderer<TEntity extends LivingEntity, T extends CustomHumanoidEntity<TEntity>> implements IGeoRenderer<T> {
    protected final List<GeoLayerRenderer<T>> layerRenderers = new ObjectArrayList<>();

    public static int getPackedOverlay(LivingEntity entity, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
    }

    public void submitAnimatable(T animatable, @Nullable Identifier textureOverride, float partialTick,
                                 PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                 @Nullable AvatarRenderState avatarState) {
        var data = animatable.update(partialTick);
        if (data == null) {
            return;
        }
        final TEntity entity = animatable.getEntity();
        poseStack.pushPose();
        try {
            if (entity.getPose() == Pose.SLEEPING) {
                Direction direction = entity.getBedOrientation();
                if (direction != null) {
                    float eyeOffset = entity.getEyeHeight(Pose.STANDING) - 0.1f;
                    poseStack.translate(-direction.getStepX() * eyeOffset, 0, -direction.getStepZ() * eyeOffset);
                }
            }

            setupRotations(entity, poseStack, data.animationData.lerpedAge, data.animationData.lerpBodyRot, partialTick);

            if (entity.getVehicle() != null) {
                VehicleAnimatableCapabilityProvider.get(entity.getVehicle()).ifPresent(cap -> {
                    var rot = cap.getRotation();
                    if (rot != null) {
                        poseStack.rotate(new Quaternionf().rotateZYX(rot.z, 0, rot.x).invert());
                    }
                });
            }

            poseStack.translate(0, 0.01f, 0);

            var texture = textureOverride == null ? data.texture : textureOverride;
            var bodyVisible = !entity.isInvisible();
            int outlineColor = avatarState == null ? 0 : avatarState.outlineColor;
            var glowing = outlineColor != 0;
            var renderType = getRenderType(texture, bodyVisible, glowing, data.modelState.hasTranslucentVertices());
            if (renderType == null) {
                return;
            }

            var packedOverlay = getPackedOverlay(entity, 0);
            preRender(data, animatable, poseStack, packedLight, packedOverlay, Color.WHITE);
            if (data.renderLayersFirst && !entity.isSpectator()) {
                submitLayers(poseStack, collector, animatable, data, avatarState, packedLight, packedOverlay);
            }

            if (data.modelState.isValid()) {
                var event = new RenderModelEvent(entity, TargetKind.PLAYER, data, collector, renderType,
                         poseStack, packedLight, packedOverlay, Color.WHITE.getColor(), outlineColor);
                if (!YesSteveModel.postEvent(event)) {
                    render(data, animatable, renderType, poseStack, collector, packedLight, packedOverlay, Color.WHITE, outlineColor);
                }
            }

            if (!data.renderLayersFirst && !entity.isSpectator()) {
                submitLayers(poseStack, collector, animatable, data, avatarState, packedLight, packedOverlay);
            }
            postRender(data, animatable, poseStack, packedLight, packedOverlay, Color.WHITE);
        } finally {
            poseStack.popPose();
        }
    }

    protected void submitLayers(PoseStack poseStack, SubmitNodeCollector collector, T animatable, GeoRenderData renderData,
                                @Nullable AvatarRenderState avatarState, int packedLight, int overlay) {
        var event = new RenderLayerEvent(animatable.getEntity(), TargetKind.PLAYER, renderData, poseStack, collector, packedLight, overlay);
        if (!YesSteveModel.postEvent(event)) {
            for (GeoLayerRenderer<T> layerRenderer : this.layerRenderers) {
                layerRenderer.submit(poseStack, collector, animatable, renderData, avatarState, packedLight, overlay);
            }
        }
    }

    protected void setupRotations(TEntity entity, PoseStack poseStack, float ageInTicks, float bodyRot, float partialTick) {
        int deathTime = entity.deathTime;
        boolean autoSpin = entity.isAutoSpinAttack();

        var inventoryPose = YsmSubmitContext.inventoryPose(entity);
        if (inventoryPose == null && entity.onClimbable()) {
            Optional<BlockPos> climbablePos = entity.getLastClimbablePos();
            if (climbablePos.isPresent()) {
                BlockState blockState = entity.level().getBlockState(climbablePos.get());
                Optional<Direction> facing = blockState.getOptionalValue(HorizontalDirectionalBlock.FACING);
                if (facing.isPresent()) {
                    bodyRot = facing.get().getOpposite().get2DDataValue() * 90;
                }
            }
        }

        if (entity.isFullyFrozen()) {
            bodyRot += (float) (Math.cos(entity.tickCount * 3.25) * Math.PI * 0.4);
        }
        if (entity.getPose() != Pose.SLEEPING) {
            poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
        }
        if (deathTime > 0) {
            float fall = (deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            fall = Math.min(net.minecraft.util.Mth.sqrt(fall), 1.0F);
            poseStack.rotateDegrees(Axis.ZP, fall * 90.0F);
        } else if (autoSpin) {
            float pitch = inventoryPose == null ? entity.getXRot(partialTick) : inventoryPose.pitch();
            poseStack.rotateDegrees(Axis.XP, -90.0F - pitch);
            poseStack.rotateDegrees(Axis.YP, ageInTicks * -75.0F);
        } else if (entity.getPose() == Pose.SLEEPING) {
            Direction bedOrientation = entity.getBedOrientation();
            float angle = bedOrientation != null ? sleepDirectionToRotation(bedOrientation) : bodyRot;
            poseStack.rotateDegrees(Axis.YP, angle);
            poseStack.rotateDegrees(Axis.ZP, 90.0F);
            poseStack.rotateDegrees(Axis.YP, 270.0F);
        }

    }

    private static float sleepDirectionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0F;
            case WEST -> 0.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
        };
    }

    public final boolean addLayer(GeoLayerRenderer<T> layer) {
        return this.layerRenderers.add(layer);
    }
}
