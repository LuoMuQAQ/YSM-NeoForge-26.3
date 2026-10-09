// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.slashblade;

import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.builder.LoopType;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.elfmcys.ysm.init.ModItemTags;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class SlashBladeAnimation {
    static boolean isSlashBlade(ItemStack stack) {
        return stack.is(ModItemTags.SLASH_BLADE) || OptionalApi.query("slashblade", false, () -> OptionalApi.instance("mods.flammpfeil.slashblade.item.ItemSlashBlade", stack.getItem()));
    }

    static String getAnimationName(AnimationEvent<? extends AnimatableEntity<? extends LivingEntity>> event) {
        LivingEntity livingEntity = event.getAnimatableEntity().getEntity();
        return getCombName(livingEntity.getMainHandItem(), livingEntity);
    }

    static String getAnimationName(IContext<? extends LivingEntity> context) {
        LivingEntity livingEntity = context.entity();
        return getCombName(livingEntity.getMainHandItem(), livingEntity);
    }

    /**
     * slashblade:idle
     * slashblade:run
     * slashblade:walk
     */
    static PlayState playMainAnimation(AnimationEvent<? extends AnimatableEntity<? extends LivingEntity>> event, String animationName, LoopType loopType) {
        String name = "slashblade:" + animationName;
        if (event.getAnimatableEntity().getAnimation(name) != null) {
            return playAnimation(event, name, loopType);
        }
        return playAnimation(event, animationName, loopType);
    }

    @NotNull
    private static String getCombName(ItemStack mainHandItem, LivingEntity entity) {
        if (!SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
            return StringUtils.EMPTY;
        }
        return OptionalApi.query("slashblade", "", () -> {
            var bladeState = bladeState(mainHandItem);
            if (bladeState == null) return "";
            long time = (entity.level().getGameTime() - OptionalApi.integer(OptionalApi.call(bladeState, "getLastActionTime"))) * 50;
            if (SlashBladeCompat.isResharped()) {
                // 重锋兼容
                return SlashBladeResharped.getResharpedComboStateName(bladeState, time, entity);
            } else if (OptionalApi.instance("mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState", bladeState)) {
                // 旧版拔刀兼容
                return SlashBladeUnsafe.getOldComboStateName(bladeState, time);
            }
            return StringUtils.EMPTY;
        });
    }

    static Object bladeState(ItemStack stack) {
        var capability = OptionalApi.getStatic("mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade", "BLADESTATE");
        var optional = OptionalApi.call(stack, "getCapability", capability);
        if (optional instanceof java.util.Optional<?> value) return value.orElse(null);
        return OptionalApi.call(optional, "orElse", (Object) null);
    }

    @NotNull
    private static PlayState playAnimation(AnimationEvent<?> event, String animationName, LoopType loopType) {
        event.getCodedController().setAnimation(animationName, loopType);
        return PlayState.CONTINUE;
    }
}
