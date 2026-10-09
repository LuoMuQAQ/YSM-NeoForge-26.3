// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.predicate;

import com.elfmcys.ysm.client.animation.EntityTickStates;
import com.elfmcys.ysm.client.animation.SwingQueries;
import com.elfmcys.ysm.client.animation.condition.ConditionManager;
import com.elfmcys.ysm.client.animation.condition.ConditionalSwing;
import com.elfmcys.ysm.client.compat.ironsspellbooks.IronsSpellBooksCompat;
import com.elfmcys.ysm.client.compat.slashblade.SlashBladeCompat;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.client.entity.IPreviewEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

import static com.elfmcys.ysm.client.animation.predicate.IAnimationPredicate.playAnimation;

public class SwingPredicate implements IAnimationPredicate<CustomHumanoidEntity<?>> {
    @Override
    public PlayState test(AnimationEvent<CustomHumanoidEntity<?>> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity entity = event.getAnimatableEntity().getEntity();
        if (entity == null || event.getAnimatableEntity() instanceof IPreviewEntity) {
            return PlayState.STOP;
        }

        // 铁魔法兼容
        PlayState playState = IronsSpellBooksCompat.playAnimation(event, entity);
        if (playState != null) {
            return playState;
        }

        // 拔刀剑兼容，拔刀剑的使用不受 swing 限制
        if (!entity.isSleeping() && SlashBladeCompat.isSlashBladeItem(entity.getItemInHand(InteractionHand.MAIN_HAND))) {
            // 起手阻止后续原挥剑动画
            if (event.getCodedController().isAnimFinished()) {
                // 重置动画
                event.getCodedController().indicateReload();
            }
            String animationName = SlashBladeCompat.getAnimationName(event);
            if (StringUtils.isNoneBlank(animationName)) {
                if (event.getAnimatableEntity().getAnimation(animationName) != null) {
                    return playAnimation(event, animationName);
                }
                return PlayState.CONTINUE;
            }
        }

        // 其他情况
        if (SwingQueries.isSwinging(entity) && !entity.isSleeping()) {
            InteractionHand swingingArm = SwingQueries.swingingArm(entity);
            if (SwingQueries.swingTicks(entity) == 0 && event.getAnimatableEntity().getStateTracker().setEntityTickState(EntityTickStates.SWING)) {
                // swing 开始时重置动画
                event.getCodedController().indicateReload();
            }
            ConditionManager conditionManager = event.getAnimatableEntity().getConditionManager();
            ConditionalSwing conditionalSwing = swingingArm == InteractionHand.MAIN_HAND ? conditionManager.getSwingMainhand() : conditionManager.getSwingOffhand();
            if (conditionalSwing != null) {
                String name = conditionalSwing.doTest(entity, swingingArm);
                if (StringUtils.isNoneBlank(name)) {
                    return playAnimation(event, name);
                }
            }
            String defaultSwing = (swingingArm == InteractionHand.MAIN_HAND) ? "swing_hand" : "swing_offhand";
            return playAnimation(event, defaultSwing);
        }
        return PlayState.CONTINUE;
    }
}
