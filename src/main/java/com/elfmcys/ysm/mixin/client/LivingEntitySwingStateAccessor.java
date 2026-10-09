package com.elfmcys.ysm.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.SwingState.class)
public interface LivingEntitySwingStateAccessor {
    @Accessor("ticks")
    int ysm$ticks();

    @Invoker("start")
    void ysm$restart(InteractionHand hand, SwingAnimation animation, int durationTicks);
}
