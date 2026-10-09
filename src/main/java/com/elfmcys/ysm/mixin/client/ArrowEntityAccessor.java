// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import net.minecraft.world.entity.projectile.arrow.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.item.alchemy.PotionContents;

@Mixin(Arrow.class)
public interface ArrowEntityAccessor {
    // Keep this interface accessor-only so Mixin can apply it to the Arrow class.
    @Invoker("getPotionContents")
    PotionContents ysm$potionContents();
}
