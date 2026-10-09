// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.swarfare;

import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.builder.LoopType;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@SuppressWarnings("removal")
public class SWarfareCompatInner {
    private static final TagKey<Item> PISTOL = TagKey.create(Registries.ITEM, Identifier.parse("superbwarfare:animated/pistol"));
    private static final TagKey<Item> RPG = TagKey.create(Registries.ITEM, Identifier.parse("superbwarfare:animated/rpg"));

    static boolean isGun(ItemStack stack) {
        return OptionalApi.query("superbwarfare", false, () -> {
        return OptionalApi.instance("com.atsuishio.superbwarfare.item.gun.GunItem", stack.getItem());

        });
    }

    static boolean shouldHidePlayerRender(Player player) {
        return OptionalApi.query("superbwarfare", false, () -> {
        if (OptionalApi.instance("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", player.getVehicle())) {
            return OptionalApi.bool(OptionalApi.call(player.getVehicle(), "hidePassenger", player));
        }
        return false;

        });
    }

    static boolean renderOffhandGun(ItemStack heldItem, GeoRenderData data, LivingEntity player, PoseStack poseStack, SubmitNodeCollector buffer, int packedLight) {
        var locator = heldItem.is(PISTOL) ? PlayerLocator.get().pistol : PlayerLocator.get().rifle;
        if (data.modelState.locatorGroupSize(locator) == 0) return false;
        var itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(itemState, heldItem, ItemDisplayContext.FIXED, player);
        if (heldItem.is(PISTOL)) {
            data.modelState.visitLocatorGroup(PlayerLocator.get().pistol, poseStack, locatorPose -> {
                locatorPose.translate(0, -0.125, 0);
                locatorPose.scale(0.65f, 0.65f, 0.65f);
                locatorPose.rotate(Axis.YP.rotationDegrees(90));
                locatorPose.rotate(Axis.ZP.rotationDegrees(-90.0F));
                itemState.submit(locatorPose, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0);
            });
        }
        if (!heldItem.is(PISTOL)) {
            data.modelState.visitLocatorGroup(PlayerLocator.get().rifle, poseStack, locatorPose -> {
                locatorPose.scale(0.65f, 0.65f, 0.65f);
                locatorPose.rotate(Axis.YP.rotationDegrees(-180.0F));
                itemState.submit(locatorPose, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0);
            });
        }
        return true;
    }

    @Nullable
    static PlayState playLungeMineAnimation(AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event) {
        return OptionalApi.query("superbwarfare", null, () -> {
        LivingEntity livingEntity = event.getAnimatableEntity().getEntity();
        if (!Objects.equals(Minecraft.getInstance().player, livingEntity)) {
            return null;
        }
        if (!(OptionalApi.instance("com.atsuishio.superbwarfare.item.LungeMine", livingEntity.getMainHandItem().getItem()))) {
            return null;
        }
        if (OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "lungeSprint")) > 0) {
            return playAnimation(event, "superbwarfare:lunge_mine_sprint");
        } else if (OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "lungeDraw")) > 0) {
            return playAnimation(event, "superbwarfare:lunge_mine_draw");
        } else if (OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "lungeAttack")) > 0) {
            return playAnimation(event, "superbwarfare:lunge_mine_fire");
        } else if (livingEntity.isSprinting() && livingEntity.onGround() && OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "lungeDraw")) == 0) {
            return playAnimation(event, "superbwarfare:lunge_mine_run");
        } else {
            return playAnimation(event, "superbwarfare:lunge_mine_idle");
        }

        });
    }

    /**
     * tac:idle
     * tac:run
     * tac:walk
     */
    static PlayState playGunMainAnimation(AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event, String animationName, LoopType loopType) {
        String tacName = "tac:" + animationName;
        var playerAnimation = event.getAnimatableEntity().getAnimation(tacName);
        if (playerAnimation != null) {
            return playAnimation(event, tacName, loopType);
        }
        return playAnimation(event, animationName, loopType);
    }

    @NotNull
    private static PlayState getGunTypeAnimation(AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event, ItemStack gun, String prefix) {
        return getGunTypeAnimation(event, gun, prefix, LoopType.LOOP);
    }

    /**
     * tac:hold:pistol
     * tac:aim:pistol
     * tac:reload:pistol
     * tac:aim_shoot:pistol
     * tac:hold_shoot:pistol
     * tac:run:pistol
     */
    static PlayState playGunHoldAnimation(AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event, ItemStack heldItem) {
        return OptionalApi.query("superbwarfare", null, () -> {
        // 先检查刺雷
        PlayState playState = playLungeMineAnimation(event);
        if (playState != null) {
            return playState;
        }
        // 再检查枪械
        if (!(OptionalApi.instance("com.atsuishio.superbwarfare.item.gun.GunItem", heldItem.getItem()))) {
            return null;
        }
        LivingEntity livingEntity = event.getAnimatableEntity().getEntity();

        if (!livingEntity.isSwimming() && livingEntity.getPose() == Pose.SWIMMING) {
            if (Math.abs(event.getLimbSwingAmount()) > 0.05) {
                return getGunTypeAnimation(event, heldItem, "tac:climb:");
            } else {
                return getGunTypeAnimation(event, heldItem, "tac:climbing:");
            }
        }

        // zoomTime 是玩家客户端数据，需要额外判断是否是当前玩家
        double aimProgress = OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "zoomTime"));
        if (!Objects.equals(Minecraft.getInstance().player, livingEntity)) {
            aimProgress = 0;
        }
        if (aimProgress > 0.3) {
            return getGunTypeAnimation(event, heldItem, "tac:aim:");
        } else {
            if (livingEntity.onGround() && livingEntity.isSprinting()) {
                return getGunTypeAnimation(event, heldItem, "tac:run:");
            }
            return getGunTypeAnimation(event, heldItem, "tac:hold:");
        }

        });
    }

    /**
     * 这些动画可能是带有后摇的动画，故需要单独分一个频道来播放，从而才能超过时长进行播放
     */
    static PlayState playGunOnceAnimation(AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event, ItemStack heldItem) {
        return OptionalApi.query("superbwarfare", PlayState.STOP, () -> {
        if (!(OptionalApi.instance("com.atsuishio.superbwarfare.item.gun.GunItem", heldItem.getItem()))) {
            return PlayState.STOP;
        }
        LivingEntity livingEntity = event.getAnimatableEntity().getEntity();
        var gunData = OptionalApi.callStatic("com.atsuishio.superbwarfare.data.gun.GunData", "from", heldItem);
        if (!Objects.equals(Minecraft.getInstance().player, livingEntity)) {
            return PlayState.STOP;
        }

        // 重置动画
        if (event.getCodedController() != null && event.getCodedController().isAnimFinished()) {
            event.getCodedController().indicateReload();
        }

        if (OptionalApi.bool(OptionalApi.call(gunData, "reloading")) && OptionalApi.number(OptionalApi.call(OptionalApi.get(gunData, "reload"), "time")) > 40) {
            return getGunTypeAnimation(event, heldItem, "tac:reload:", LoopType.PLAY_ONCE);
        }

        float meleeTime = OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "gunMelee"));
        if (meleeTime > 5) {
            return getGunTypeAnimation(event, heldItem, "tac:melee:", LoopType.PLAY_ONCE);
        }

        double fireTick = OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "fireRotTimer"));
        if (0 < fireTick && fireTick < 1) {
            double aimProgress = OptionalApi.number(OptionalApi.getStatic("com.atsuishio.superbwarfare.event.ClientEventHandler", "zoomTime"));
            boolean isClimbing = !livingEntity.isSwimming() && livingEntity.getPose() == Pose.SWIMMING && Math.abs(event.getLimbSwingAmount()) <= 0.05;

            if (isClimbing) {
                return getGunTypeAnimation(event, heldItem, "tac:climbing:fire:", LoopType.PLAY_ONCE);
            }
            if (aimProgress > 0.3) {
                return getGunTypeAnimation(event, heldItem, "tac:aim:fire:", LoopType.PLAY_ONCE);
            } else {
                return getGunTypeAnimation(event, heldItem, "tac:hold:fire:", LoopType.PLAY_ONCE);
            }
        }
        return PlayState.CONTINUE;

        });
    }

    @NotNull
    private static PlayState getGunTypeAnimation(AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event,
                                                 ItemStack gun, String prefix, LoopType loopType) {
        var gunCondition = event.getAnimatableEntity().getConditionManager().getTAC();
        if (gunCondition != null) {
            ItemStack stack = event.getAnimatableEntity().getEntity().getMainHandItem();
            String name = gunCondition.doTest(stack, prefix);
            if (StringUtils.isNoneBlank(name)) {
                return playAnimation(event, name, loopType);
            }
        }
        if (gun.is(PISTOL)) {
            return playAnimation(event, prefix + "pistol", loopType);
        }
        if (gun.is(RPG)) {
            return playAnimation(event, prefix + "rpg", loopType);
        }
        return playAnimation(event, prefix + "rifle", loopType);
    }

    @NotNull
    private static PlayState playAnimation(AnimationEvent<?> event, String animationName, LoopType loopType) {
        event.getCodedController().setAnimation(animationName, loopType);
        return PlayState.CONTINUE;
    }

    @NotNull
    private static PlayState playAnimation(AnimationEvent<?> event, String animationName) {
        event.getCodedController().setAnimation(animationName);
        return PlayState.CONTINUE;
    }
}
