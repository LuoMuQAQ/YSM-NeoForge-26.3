package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.entity.IPreviewEntity;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.elfmcys.ysm.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.elfmcys.ysm.util.RenderUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

final class YsmPreviewDraw {
    static final int FULL_BRIGHT = 15728880;

    private YsmPreviewDraw() {
    }

    static void submit(YsmModelPreviewState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        if (state.kind() == YsmModelPreviewState.Kind.PLAYER) {
            var player = state.player();
            if (player == null) {
                return;
            }
            RenderUtil.setRenderingInPaperDoll(true);
            try {
                PlayerAnimatableCapabilityProvider.get(player).ifPresent(cap ->
                        drawLiving(player, state, poseStack, () ->
                        RegisterEntityRenderersEvent.getPlayerRenderer().submitAnimatable(
                                cap, null, state.partialTick(), poseStack, collector, FULL_BRIGHT, null)));
            } finally {
                RenderUtil.setRenderingInPaperDoll(false);
            }
            return;
        }
        var animatable = state.animatable();
        var renderer = state.renderer();
        if (animatable == null || renderer == null) {
            return;
        }
        RenderUtil.setRenderingInInventory(true);
        try {
            if (!(animatable.getEntity() instanceof LivingEntity living)) {
                return;
            }
            drawLiving(living, state, poseStack, () -> {
                if (state.kind() == YsmModelPreviewState.Kind.TEXTURE) {
                    applyTexturePose(animatable, living, poseStack, state);
                }
                submitAnimatable(renderer, animatable, state.partialTick(), poseStack, collector);
            });
        } finally {
            RenderUtil.setRenderingInInventory(false);
        }
    }

    private static void drawLiving(LivingEntity living, YsmModelPreviewState state, PoseStack poseStack, Runnable draw) {
        float yBodyRot = living.yBodyRot;
        float yBodyRotO = living.yBodyRotO;
        float yRot = living.getYRot();
        float yRotO = living.yRotO;
        float xRot = living.getXRot();
        float xRotO = living.xRotO;
        float yHeadRot = living.yHeadRot;
        float yHeadRotO = living.yHeadRotO;
        Pose pose = living.getPose();
        ItemStack[] equipment = state.disableEquipments() ? hideEquipment(living) : null;
        try {
            float facing = state.disablePreviewRotation() ? 180.0F : 200.0F;
            if (state.kind() == YsmModelPreviewState.Kind.TEXTURE) {
                facing = -state.yaw();
                poseStack.translate(0, 0.8, 0);
                poseStack.rotate(new Quaternionf().rotateZ((float) Math.PI)
                        .mul(Axis.XP.rotationDegrees(-10.0F + state.pitch())));
            } else if (state.kind() == YsmModelPreviewState.Kind.PLAYER) {
                float bodyYaw = net.minecraft.util.Mth.lerp(state.partialTick(), yBodyRotO, yBodyRot);
                // Cancel the player's world body yaw, then retain the configured
                // screen offset. Applying it to the entity too would cancel it.
                poseStack.rotate(new Quaternionf().rotateZ((float) Math.toRadians(180.1F))
                        .mul(Axis.YP.rotationDegrees(bodyYaw + state.yaw() - 180.0F)));
            } else {
                poseStack.rotate(new Quaternionf().rotateZ((float) Math.PI));
                if (!state.disablePreviewRotation()) {
                    poseStack.rotate(Axis.XP.rotationDegrees(-10.0F));
                }
            }
            if (state.kind() == YsmModelPreviewState.Kind.TEXTURE) {
                living.yBodyRot = -state.yaw();
                living.yBodyRotO = -state.yaw();
                living.setYRot(180);
                living.yRotO = 180;
                living.yHeadRot = -state.yaw();
                living.yHeadRotO = -state.yaw();
            } else if (state.kind() != YsmModelPreviewState.Kind.PLAYER) {
                living.yBodyRot = facing;
                living.yBodyRotO = facing;
                living.setYRot(facing);
                living.yRotO = facing;
                living.yHeadRot = facing;
                living.yHeadRotO = facing;
            }
            if (state.kind() != YsmModelPreviewState.Kind.PLAYER) {
                living.setXRot(0);
                living.xRotO = 0;
            }
            draw.run();
        } finally {
            restoreEquipment(living, equipment);
            living.yBodyRot = yBodyRot;
            living.yBodyRotO = yBodyRotO;
            living.setYRot(yRot);
            living.yRotO = yRotO;
            living.setXRot(xRot);
            living.xRotO = xRotO;
            living.yHeadRot = yHeadRot;
            living.yHeadRotO = yHeadRotO;
            living.setPose(pose);
        }
    }

    private static void applyTexturePose(AnimatableEntity<?> animatable, LivingEntity living, PoseStack poseStack, YsmModelPreviewState state) {
        if (!(animatable instanceof IPreviewEntity preview)) {
            return;
        }
        var animation = preview.getPreviewInfo();
        if (animation.hasPreview("sleep")) {
            poseStack.rotate(Axis.YP.rotationDegrees(state.yaw() - 90.0F));
            poseStack.translate(0.5, 0.5625, 0);
            living.setPose(Pose.SLEEPING);
        }
        if (animation.hasPreview("swim") || animation.hasPreview("swim_stand")) {
            living.setPose(Pose.SWIMMING);
        }
        if (animation.hasPreview("sneak") || animation.hasPreview("sneaking")) {
            living.setPose(Pose.CROUCHING);
        }
        if (animation.hasPreview("sit")) {
            poseStack.translate(0, -0.5, 0);
        }
        if (animation.hasPreview("ride")) {
            poseStack.translate(0, 0.85, 0);
        }
        if (animation.hasPreview("ride_pig")) {
            poseStack.translate(0, 0.3125, 0);
        }
        if (animation.hasPreview("boat")) {
            poseStack.translate(0, -0.45, 0);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void submitAnimatable(GeoReplacedEntityRenderer renderer, AnimatableEntity<?> animatable, float partialTick,
                                          PoseStack poseStack, SubmitNodeCollector collector) {
        renderer.submitAnimatable((CustomHumanoidEntity) animatable, null, partialTick, poseStack, collector, FULL_BRIGHT, null);
    }

    private static ItemStack[] hideEquipment(LivingEntity living) {
        EquipmentSlot[] slots = EquipmentSlot.values();
        ItemStack[] saved = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            saved[i] = living.getItemBySlot(slots[i]);
            living.setItemSlot(slots[i], ItemStack.EMPTY);
        }
        return saved;
    }

    private static void restoreEquipment(LivingEntity living, ItemStack[] saved) {
        if (saved == null) {
            return;
        }
        EquipmentSlot[] slots = EquipmentSlot.values();
        for (int i = 0; i < slots.length && i < saved.length; i++) {
            living.setItemSlot(slots[i], saved[i]);
        }
    }
}
