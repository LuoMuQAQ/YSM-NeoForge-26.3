package com.elfmcys.ysm.client.animation;

import com.elfmcys.ysm.mixin.client.LivingEntityAccessor;
import com.elfmcys.ysm.mixin.client.LivingEntitySwingStateAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;

/**
 * Reads the 26.3 swing description without changing molang names.
 * swing_time stays an integer tick count. The last hand is remembered because
 * the current swing is cleared when the animation ends.
 */
public final class SwingQueries {
    // Animation workers for different entities also query this container.
    private static final Map<LivingEntity, InteractionHand> LAST_HAND = Collections.synchronizedMap(new WeakHashMap<>());

    private SwingQueries() {
    }

    public static boolean isSwinging(LivingEntity entity) {
        return entity.isSwinging();
    }

    public static int swingTicks(LivingEntity entity) {
        if (!entity.isSwinging()) {
            return 0;
        }
        LivingEntity.SwingState state = ((LivingEntityAccessor) entity).ysm$swingState();
        return ((LivingEntitySwingStateAccessor) (Object) state).ysm$ticks();
    }

    public static InteractionHand swingingArm(LivingEntity entity) {
        var current = entity.getCurrentSwing();
        if (current != null) {
            LAST_HAND.put(entity, current.hand());
            return current.hand();
        }
        return LAST_HAND.getOrDefault(entity, InteractionHand.MAIN_HAND);
    }

    public static float attackTime(LivingEntity entity, float partialTick) {
        return entity.getSwingAnimation(partialTick);
    }
}
