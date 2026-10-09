// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.parcool;

import com.google.common.collect.Maps;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class ParCoolAnimationManger {
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, String> INDEX_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    static boolean hasAnimation(Player player) {
        String animationName = getAnimation(player);
        return StringUtils.isNotBlank(animationName);
    }

    @Nullable
    static String getAnimation(Player player) {
        return OptionalApi.query("parcool", null, () -> getProviderAnimation(player));
    }

    private static String getProviderAnimation(Player player) {
        var animation = OptionalApi.callStatic("com.alrex.parcool.common.capability.Animation", "get", player);
        if (animation != null && OptionalApi.bool(OptionalApi.call(animation, "hasAnimator"))) {
            var animator = OptionalApi.get(animation, "animator");
            var parkourability = OptionalApi.callStatic("com.alrex.parcool.common.capability.Parkourability", "get", player);
            if (parkourability == null) {
                return null;
            }

            // 爬行动画不需要，调用默认的即可
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.CrawlAnimator", animator)) {
                return null;
            }

            // 如果动画已经 shouldRemoved 了，就不播放动画了
            if (OptionalApi.bool(OptionalApi.call(animator, "shouldRemoved", player, parkourability))) {
                return null;
            }

            // 垂挂动画
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.ClingToCliffAnimator", animator)) {
                var direction = OptionalApi.enumName(OptionalApi.call(action(parkourability, "ClingToCliff"), "getFacingDirection"));
                return switch (direction) {
                    case "ToWall" -> "parcool:cling_to_cliff";
                    case "RightAgainstWall" -> "parcool:cling_to_cliff_right";
                    case "LeftAgainstWall" -> "parcool:cling_to_cliff_left";
                    default -> null;
                };
            }

            // 滑铲
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.DodgeAnimator", animator)) {
                var direction = OptionalApi.enumName(OptionalApi.get(animator, "direction"));
                return switch (direction) {
                    case "Front" -> "parcool:dodge_front";
                    case "Back" -> "parcool:dodge_back";
                    case "Left" -> "parcool:dodge_left";
                    case "Right" -> "parcool:dodge_right";
                    default -> null;
                };
            }

            // 翻滚
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.FlippingAnimator", animator)) {
                var direction = OptionalApi.enumName(OptionalApi.get(animator, "direction"));
                return switch (direction) {
                    case "Front" -> "parcool:flipping_front";
                    case "Back" -> "parcool:flipping_back";
                    default -> null;
                };
            }

            // 跑墙
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.HorizontalWallRunAnimator", animator)) {
                boolean wallIsRightSide = OptionalApi.bool(OptionalApi.get(animator, "wallIsRightSide"));
                return wallIsRightSide ? "parcool:horizontal_wall_run_right" : "parcool:horizontal_wall_run_left";
            }

            // 悬挂
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.HangAnimator", animator)) {
                var hangDown = action(parkourability, "HangDown");
                boolean orthogonalToBar = OptionalApi.bool(OptionalApi.call(hangDown, "isOrthogonalToBar"));
                return orthogonalToBar ? "parcool:hang_vertical" : "parcool:hang";
            }

            // 落地缓冲
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.RollAnimator", animator)) {
                var direction = OptionalApi.enumName(OptionalApi.get(animator, "direction"));
                return switch (direction) {
                    case "Front" -> "parcool:roll_front";
                    case "Back" -> "parcool:roll_back";
                    case "Left" -> "parcool:roll_left";
                    case "Right" -> "parcool:roll_right";
                    default -> null;
                };
            }

            // 翻越动画需要分左右
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.SpeedVaultAnimator", animator)) {
                var type = OptionalApi.enumName(OptionalApi.get(animator, "type"));
                if ("Left".equals(type)) {
                    return "parcool:speed_vault_left";
                }
                return "parcool:speed_vault_right";
            }

            // 墙跳
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.WallJumpAnimator", animator)) {
                boolean swingRightArm = OptionalApi.bool(OptionalApi.get(animator, "wallRightSide"));
                return swingRightArm ? "parcool:wall_jump_right" : "parcool:wall_jump_left";
            }

            // 墙滑
            if (OptionalApi.instance("com.alrex.parcool.client.animation.impl.WallSlideAnimator", animator)) {
                Vec3 wall = (Vec3) OptionalApi.call(action(parkourability, "WallSlide"), "getLeanedWallDirection");
                if (wall == null) {
                    return "parcool:wall_slide_right";
                }
                Vec3 bodyVec = (Vec3) OptionalApi.callStatic("com.alrex.parcool.utilities.VectorUtil", "fromYawDegree", player.yBodyRot);
                Vec3 vec = new Vec3(bodyVec.x, 0, bodyVec.z).normalize();
                Vec3 dividedVec = new Vec3(vec.x * wall.x + vec.z * wall.z, 0, -vec.x * wall.z + vec.z * wall.x).normalize();
                if (dividedVec.z < 0) {
                    return "parcool:wall_slide_right";
                } else {
                    return "parcool:wall_slide_left";
                }
            }

            return getAnimationName(animator);
        }
        return null;
    }

    private static Object action(Object ability, String name) {
        return OptionalApi.call(ability, "get", OptionalApi.type("com.alrex.parcool.common.action.impl." + name));
    }

    // 跑酷模组没有给这些动画命名，所以我们手动给命名吧
    private static String getAnimationName(Object animator) {
        return INDEX_MAP.computeIfAbsent(animator.getClass(), clz -> getAnimationNameFromClassName(clz.getSimpleName()));
    }

    private static String getAnimationNameFromClassName(String name) {
        if (StringUtils.isBlank(name)) {
            return "";
        }
        if (name.endsWith("Animator")) {
            name = name.substring(0, name.length() - "Animator".length());
        }
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        sb.insert(0, "parcool:");
        return sb.toString();
    }
}
