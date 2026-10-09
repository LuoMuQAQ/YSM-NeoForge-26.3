// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.core.molang.variable.entity;

import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.ysm.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;

public class ThrowableItemProjectileVariable extends LambdaVariable<ThrowableItemProjectile> {
    public ThrowableItemProjectileVariable(IValueEvaluator<?, IContext<ThrowableItemProjectile>> evaluator) {
        super(evaluator);
    }

    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof ThrowableItemProjectile;
    }
}
