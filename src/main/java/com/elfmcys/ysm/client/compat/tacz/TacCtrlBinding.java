// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.tacz;

import com.elfmcys.ysm.client.animation.molang.CtrlBinding;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class TacCtrlBinding {
    static void addInnerBinding(CtrlBinding binding) {
        binding.livingEntityVar("tac_hold_gun", ctx -> OptionalApi.query("tacz", false, () -> OptionalApi.bool(OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "mainhandHoldGun", ctx.entity()))));
        binding.livingEntityVar("tac_gun_type", TacCtrlBinding::getGunType);
        binding.livingEntityVar("tac_gun_id", TacCtrlBinding::getGunId);
        binding.livingEntityVar("tac_is_fire", ctx -> OptionalApi.query("tacz", false, () -> OptionalApi.number(OptionalApi.call(operator(ctx.entity()), "getSynShootCoolDown")) > 0));
        binding.livingEntityVar("tac_is_aim", ctx -> OptionalApi.query("tacz", false, () -> OptionalApi.number(OptionalApi.call(operator(ctx.entity()), "getSynAimingProgress")) > 0));
        binding.livingEntityVar("tac_is_reload", ctx -> OptionalApi.query("tacz", false, () -> OptionalApi.number(OptionalApi.call(OptionalApi.call(operator(ctx.entity()), "getSynReloadState"), "getCountDown")) > 0));
        binding.livingEntityVar("tac_is_melee", ctx -> OptionalApi.query("tacz", false, () -> OptionalApi.number(OptionalApi.call(operator(ctx.entity()), "getSynMeleeCoolDown")) > 0));
        binding.livingEntityVar("tac_is_draw", ctx -> OptionalApi.query("tacz", false, () -> OptionalApi.number(OptionalApi.call(operator(ctx.entity()), "getSynDrawCoolDown")) > 0));

        // 新版新增
        binding.livingEntityVar("tac_fire_mode", ctx -> {
            return OptionalApi.query("tacz", "", () -> {
                var mode = OptionalApi.enumName(OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getMainHandFireMode", ctx.entity()));
                return mode.equals("UNKNOWN") ? "" : mode;
            });
        });
    }

    private static Object operator(LivingEntity entity) {
        return OptionalApi.callStatic("com.tacz.guns.api.entity.IGunOperator", "fromLivingEntity", entity);
    }

    private static String getGunType(IContext<LivingEntity> context) {
        return OptionalApi.query("tacz", "", () -> {
        ItemStack mainHandItem = context.entity().getMainHandItem();
        var gun = OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getIGunOrNull", mainHandItem);
        if (gun == null) {
            return StringUtils.EMPTY;
        }
        Optional<?> indexOptional = (Optional<?>) OptionalApi.callStatic("com.tacz.guns.api.TimelessAPI", "getCommonGunIndex", OptionalApi.call(gun, "getGunId", mainHandItem));
        if (indexOptional.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return (String) OptionalApi.call(indexOptional.get(), "getType");

        });
    }

    private static String getGunId(IContext<LivingEntity> context) {
        return OptionalApi.query("tacz", "", () -> {
        ItemStack mainHandItem = context.entity().getMainHandItem();
        var gun = OptionalApi.callStatic("com.tacz.guns.api.item.IGun", "getIGunOrNull", mainHandItem);
        if (gun == null) {
            return StringUtils.EMPTY;
        }
        return OptionalApi.call(gun, "getGunId", mainHandItem).toString();

        });
    }
}
