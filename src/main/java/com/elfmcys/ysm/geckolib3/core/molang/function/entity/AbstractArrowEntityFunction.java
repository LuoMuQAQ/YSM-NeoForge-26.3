// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.core.molang.function.entity;

import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.function.ContextFunction;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

public abstract class AbstractArrowEntityFunction extends ContextFunction<AbstractArrow> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractArrow;
    }
}
