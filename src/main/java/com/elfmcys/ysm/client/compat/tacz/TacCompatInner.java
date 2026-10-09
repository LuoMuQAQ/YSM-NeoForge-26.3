// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.tacz;

import com.elfmcys.ysm.client.animation.condition.ConditionTAC;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.builder.LoopType;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

class TacCompatInner {
    private static Optional<?> gunIndex(Object id) {
        return (Optional<?>) OptionalApi.callStatic("com.tacz.guns.api.TimelessAPI", "getCommonGunIndex", id);
    }

    static boolean isGun(ItemStack itemStack) {
        return OptionalApi.query("tacz", false, () -> {
        return OptionalApi.instance("com.tacz.guns.api.item.IGun", itemStack.getItem());

        });
    }

    static void registerEvent() {
        TacEvent.register();
    }

    static boolean isGrenade(ItemStack itemStack) {
        // TODO 手雷还没有
        return false;
    }

    static boolean renderOffhandGun(ItemStack heldItem, GeoRenderData data, LivingEntity player, PoseStack poseStack, SubmitNodeCollector buffer, int packedLight) {
        return OptionalApi.query("tacz", false, () -> {
            var gun = OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getIGunOrNull", heldItem);
            if (gun == null) return false;
            var index = gunIndex(OptionalApi.call(gun, "getGunId", heldItem));
            if (index.isEmpty()) return false;
            String weaponType = (String) OptionalApi.call(index.get(), "getType");
            var locator = isType(weaponType, "pistol") ? PlayerLocator.get().pistol : PlayerLocator.get().rifle;
            if (data.modelState.locatorGroupSize(locator) == 0) return false;
            var itemState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForLiving(itemState, heldItem, ItemDisplayContext.FIXED, player);
            if (isType(weaponType, "pistol")) {
                data.modelState.visitLocatorGroup(PlayerLocator.get().pistol, poseStack, locatorPose -> {
                    locatorPose.translate(0, -0.125, 0);
                    locatorPose.scale(0.65f, 0.65f, 0.65f);
                    locatorPose.rotate(Axis.YP.rotationDegrees(-90.0F));
                    locatorPose.rotate(Axis.ZP.rotationDegrees(90.0F));
                    itemState.submit(locatorPose, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0);
                });
            }
            if (!isType(weaponType, "pistol")) {
                data.modelState.visitLocatorGroup(PlayerLocator.get().rifle, poseStack, locatorPose -> {
                    locatorPose.scale(0.65f, 0.65f, 0.65f);
                    locatorPose.rotate(Axis.YP.rotationDegrees(-180.0F));
                    itemState.submit(locatorPose, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0);
                });
            }
            return true;
        });
    }

    static PlayState playGrenadeAnimation(AnimationEvent<? extends AnimatableEntity<? extends LivingEntity>> event, InteractionHand hand) {
        // TODO 手雷还没有
        if (hand == InteractionHand.MAIN_HAND) {
            return playLoopAnimation(event, "tac:mainhand:grenade");
        }
        return playLoopAnimation(event, "tac:offhand:grenade");
    }

    /**
     * tac:idle
     * tac:run
     * tac:walk
     */
    static PlayState playGunMainAnimation(AnimationEvent<? extends AnimatableEntity<? extends LivingEntity>> event, String animationName, LoopType loopType) {
        String tacName = "tac:" + animationName;
        if (event.getAnimatableEntity().getAnimation(tacName) != null) {
            return playAnimation(event, tacName, loopType);
        }
        return playAnimation(event, animationName, loopType);
    }

    /**
     * tac:hold:pistol
     * tac:aim:pistol
     * tac:reload:pistol
     * tac:aim_shoot:pistol
     * tac:hold_shoot:pistol
     * tac:run:pistol
     */
    static PlayState playGunHoldAnimation(AnimationEvent<? extends CustomHumanoidEntity<?>> event, ItemStack heldItem) {
        return OptionalApi.query("tacz", PlayState.STOP, () -> {
        var gun = OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getIGunOrNull", heldItem);
        if (gun == null) {
            return PlayState.STOP;
        }
        Optional<?> indexOptional = gunIndex(OptionalApi.call(gun, "getGunId", heldItem));
        if (indexOptional.isEmpty()) {
            return PlayState.STOP;
        }
        Object gunIndex = indexOptional.get();
        String weaponType = (String) OptionalApi.call(gunIndex, "getType");
        LivingEntity livingEntity = event.getAnimatableEntity().getEntity();
        var operator = OptionalApi.callStatic("com.tacz.guns.api.entity.IGunOperator", "fromLivingEntity", livingEntity);

        if (!livingEntity.isSwimming() && livingEntity.getPose() == Pose.SWIMMING) {
            if (Math.abs(event.getLimbSwingAmount()) > 0.05) {
                return getGunTypeAnimation(event, weaponType, "tac:climb:");
            } else {
                return getGunTypeAnimation(event, weaponType, "tac:climbing:");
            }
        }

        float aimProgress = OptionalApi.number(OptionalApi.call(operator, "getSynAimingProgress"));
        if (aimProgress > 0) {
            return getGunTypeAnimation(event, weaponType, "tac:aim:");
        } else {
            if (livingEntity.onGround() && livingEntity.isSprinting()) {
                return getGunTypeAnimation(event, weaponType, "tac:run:");
            }
            return getGunTypeAnimation(event, weaponType, "tac:hold:");
        }

        });
    }

    /**
     * 这些动画可能是带有后摇的动画，故需要单独分一个频道来播放，从而才能超过时长进行播放
     */
    static PlayState playGunOnceAnimation(AnimationEvent<? extends CustomHumanoidEntity<?>> event, ItemStack heldItem) {
        return OptionalApi.query("tacz", PlayState.STOP, () -> {
        var gun = OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getIGunOrNull", heldItem);
        if (gun == null) {
            return PlayState.STOP;
        }
        Optional<?> indexOptional = gunIndex(OptionalApi.call(gun, "getGunId", heldItem));
        if (indexOptional.isEmpty()) {
            return PlayState.STOP;
        }

        Object gunIndex = indexOptional.get();
        String weaponType = (String) OptionalApi.call(gunIndex, "getType");
        LivingEntity livingEntity = event.getAnimatableEntity().getEntity();
        var operator = OptionalApi.callStatic("com.tacz.guns.api.entity.IGunOperator", "fromLivingEntity", livingEntity);
        long fireTick = OptionalApi.integer(OptionalApi.call(operator, "getSynShootCoolDown"));

        if (OptionalApi.instance("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator", livingEntity)) {
            fireTick = Math.max(fireTick, OptionalApi.integer(OptionalApi.call(livingEntity, "getClientShootCoolDown")));
        }

        if (event.getAnimatableEntity().isTacGunAnimationNeedReload()) {
            playLoopAnimation(event, "empty");
        }
        event.getAnimatableEntity().setTacGunAnimationNeedReload(false);

        float reloadProgress = OptionalApi.number(OptionalApi.call(OptionalApi.call(operator, "getSynReloadState"), "getCountDown"));
        if (reloadProgress > 0) {
            return getGunTypeAnimation(event, weaponType, "tac:reload:", LoopType.PLAY_ONCE);
        }

        long synMeleeCoolDown = OptionalApi.integer(OptionalApi.call(operator, "getSynMeleeCoolDown"));
        if (synMeleeCoolDown > 0) {
            return getGunTypeAnimation(event, weaponType, "tac:melee:", LoopType.PLAY_ONCE);
        }

        if (fireTick > 0) {
            float aimProgress = OptionalApi.number(OptionalApi.call(operator, "getSynAimingProgress"));
            boolean isClimbing = !livingEntity.isSwimming() && livingEntity.getPose() == Pose.SWIMMING && Math.abs(event.getLimbSwingAmount()) <= 0.05;

            if (isClimbing) {
                return getGunTypeAnimation(event, weaponType, "tac:climbing:fire:", LoopType.PLAY_ONCE);
            }
            if (aimProgress > 0) {
                return getGunTypeAnimation(event, weaponType, "tac:aim:fire:", LoopType.PLAY_ONCE);
            } else {
                return getGunTypeAnimation(event, weaponType, "tac:hold:fire:", LoopType.PLAY_ONCE);
            }
        }
        return PlayState.CONTINUE;

        });
    }

    static void openFlashShellRender(LivingEntity livingEntity) {
        OptionalApi.query("tacz", null, () -> {
        LocalPlayer player = Minecraft.getInstance().player;
        if (livingEntity.equals(player)) {
            OptionalApi.setStatic("com.tacz.guns.client.model.functional.MuzzleFlashRender", "isSelf", true);
            OptionalApi.setStatic("com.tacz.guns.client.model.functional.ShellRender", "isSelf", true);
        }

            return null;
        });
    }

    static void stopFlashShellRender() {
        OptionalApi.cleanup("tacz", () -> {
            try {
                OptionalApi.setStatic("com.tacz.guns.client.model.functional.MuzzleFlashRender", "isSelf", false);
            } finally {
                OptionalApi.setStatic("com.tacz.guns.client.model.functional.ShellRender", "isSelf", false);
            }
        });
    }

    @Nullable
    static Identifier getGunId(ItemStack itemInHand) {
        return OptionalApi.query("tacz", null, () -> {
        var iGun = OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getIGunOrNull", itemInHand);
        if (iGun == null) {
            return null;
        }
        return (Identifier) OptionalApi.call(iGun, "getGunId", itemInHand);

        });
    }

    @NotNull
    private static PlayState getGunTypeAnimation(AnimationEvent<? extends CustomHumanoidEntity<?>> event, String weaponType, String prefix) {
        return getGunTypeAnimation(event, weaponType, prefix, LoopType.LOOP);
    }

    @NotNull
    private static PlayState getGunTypeAnimation(AnimationEvent<? extends CustomHumanoidEntity<?>> event, String weaponType, String prefix, LoopType loopType) {
        ConditionTAC conditionTAC = event.getAnimatableEntity().getConditionManager().getTAC();
        if (conditionTAC != null) {
            ItemStack stack = event.getAnimatableEntity().getEntity().getMainHandItem();
            String name = conditionTAC.doTest(stack, prefix);
            if (StringUtils.isNoneBlank(name)) {
                return playAnimation(event, name, loopType);
            }
        }
        if (isType(weaponType, "pistol")) {
            return playAnimation(event, prefix + "pistol", loopType);
        }
        if (isType(weaponType, "rpg")) {
            return playAnimation(event, prefix + "rpg", loopType);
        }
        return playAnimation(event, prefix + "rifle", loopType);
    }

    @NotNull
    private static PlayState playLoopAnimation(AnimationEvent<?> event, String animationName) {
        return playAnimation(event, animationName, LoopType.LOOP);
    }

    @NotNull
    private static PlayState playAnimation(AnimationEvent<?> event, String animationName, LoopType loopType) {
        event.getCodedController().setAnimation(animationName, loopType);
        return PlayState.CONTINUE;
    }

    private static boolean isType(String type, String tabType) {
        return type.equals(tabType);
    }
}
