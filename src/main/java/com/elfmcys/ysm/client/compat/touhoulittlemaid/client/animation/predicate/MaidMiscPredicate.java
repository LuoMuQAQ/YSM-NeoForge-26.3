// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.predicate;

import com.elfmcys.ysm.client.animation.predicate.IAnimationPredicate;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.CustomYsmMaidEntity;
import com.elfmcys.ysm.client.entity.IPreviewEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;

import static com.elfmcys.ysm.client.animation.predicate.IAnimationPredicate.playLoopAnimation;

public class MaidMiscPredicate implements IAnimationPredicate<CustomYsmMaidEntity> {
    public static final String[] ANIM_LIST = new String[]{"game_win", "game_lost", "beg"};

    @Override
    public PlayState test(AnimationEvent<CustomYsmMaidEntity> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity maid = event.getAnimatableEntity().getEntity();
        if (maid == null || event.getAnimatableEntity() instanceof IPreviewEntity) {
            return PlayState.STOP;
        }
        // 赢棋输棋优先
        if (MaidApi.is("entity.item.EntitySit", maid.getVehicle())) {
            var manager = MaidApi.read(maid, "getGameRecordManager");
            if (MaidApi.flag(manager, "isWin")) {
                return playLoopAnimation(event, "game_win");
            }
            if (MaidApi.flag(manager, "isLost")) {
                return playLoopAnimation(event, "game_lost");
            }
        }
        // 祈求动画
        if (MaidApi.flag(maid, "isBegging")) {
            return playLoopAnimation(event, "beg");
        }
        return PlayState.STOP;
    }
}
