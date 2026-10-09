// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.molang;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.client.animation.molang.TLMBinding;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.variable.IValueEvaluator;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.Function;

public class TLMBindingInner {
    public static void addInnerBinding(TLMBinding binding) {
        binding.livingEntityVar("is_begging", checkMaid(maid -> MaidApi.read(maid, "isBegging")));
        binding.livingEntityVar("is_sitting", checkMaid(maid -> MaidApi.read(maid, "isMaidInSittingPose")));
        binding.livingEntityVar("has_backpack", checkMaid(maid -> MaidApi.read(maid, "hasBackpack")));
        binding.livingEntityVar("favorability_point", checkMaid(maid -> MaidApi.read(maid, "getFavorability")));
        binding.livingEntityVar("favorability_level", checkMaid(maid -> OptionalApi.call(MaidApi.read(maid, "getFavorabilityManager"), "getLevel")));
        binding.livingEntityVar("task_id", checkMaid(maid -> OptionalApi.call(MaidApi.read(maid, "getTask"), "getUid")));
        binding.livingEntityVar("schedule", checkMaid(maid -> OptionalApi.enumName(MaidApi.read(maid, "getSchedule")).toLowerCase(Locale.ENGLISH)));
        binding.livingEntityVar("activity", checkMaid(maid -> OptionalApi.call(MaidApi.read(maid, "getScheduleDetail"), "getName")));
        binding.livingEntityVar("gomoku_win_count", checkMaid(maid -> OptionalApi.call(MaidApi.read(maid, "getGameRecordManager"), "getGomokuWinCount")));
        binding.livingEntityVar("gomoku_rank", checkMaid(maid -> OptionalApi.callStatic(MaidApi.ROOT + "entity.ai.brain.MaidGomokuAI", "getRank", maid)));
        binding.livingEntityVar("game_statue", checkMaid(TLMBindingInner::getGameStatue));
        binding.livingEntityVar("backpack_type", checkMaid(maid -> OptionalApi.call(MaidApi.read(maid, "getMaidBackpackType"), "getId").toString()));
        binding.livingEntityVar("is_entity", checkMaid(maid -> MaidApi.renderState(maid, "ENTITY")));
        binding.livingEntityVar("is_statue", checkMaid(maid -> MaidApi.renderState(maid, "STATUE")));
        binding.livingEntityVar("is_garage_kit", checkMaid(maid -> MaidApi.renderState(maid, "GARAGE_KIT")));
        binding.livingEntityVar("show_item", checkMaid(TLMBindingInner::getShowItem));
    }

    @NotNull
    private static IValueEvaluator<Object, IContext<LivingEntity>> checkMaid(Function<LivingEntity, Object> predicate) {
        return ctx -> {
            LivingEntity entity = ctx.entity();
            if (entity instanceof LivingEntity maid && MaidApi.isMaid(maid)) {
                return OptionalApi.query(MaidApi.ID, 0, () -> predicate.apply(maid));
            }
            return 0;
        };
    }

    private static String getGameStatue(LivingEntity maid) {
        if (MaidApi.is("entity.item.EntitySit", maid.getVehicle())) {
            var manager = MaidApi.read(maid, "getGameRecordManager");
            if (OptionalApi.bool(OptionalApi.call(manager, "isWin"))) {
                return "win";
            }
            if (OptionalApi.bool(OptionalApi.call(manager, "isLost"))) {
                return "lost";
            }
        }
        return StringUtils.EMPTY;
    }

    private static String getShowItem(LivingEntity maid) {
        ItemStack item = (ItemStack) MaidApi.read(maid, "getBackpackShowItem");
        if (item.isEmpty()) {
            return StringUtils.EMPTY;
        }
        Identifier key = RegistryIds.itemId(item.getItem());
        if (key == null) {
            return StringUtils.EMPTY;
        }
        return key.toString();
    }
}
