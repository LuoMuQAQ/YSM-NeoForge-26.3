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

public class MaidStatuePredicate implements IAnimationPredicate<CustomYsmMaidEntity> {
    public static final String[] ANIM_LIST = new String[]{"statue", "garage_kit"};

    @Override
    public PlayState test(AnimationEvent<CustomYsmMaidEntity> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity maid = event.getAnimatableEntity().getEntity();
        if (maid == null || event.getAnimatableEntity() instanceof IPreviewEntity) {
            return PlayState.STOP;
        }
        // 检查女仆是否是雕像或者手办状态，如果是，那么播放对应动画
        if (MaidApi.renderState(maid, "STATUE")) {
            return playLoopAnimation(event, "statue");
        }
        if (MaidApi.renderState(maid, "GARAGE_KIT")) {
            return playLoopAnimation(event, "garage_kit");
        }
        return PlayState.STOP;
    }
}
