// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("setLivingEntityFlag")
    void setFlag(int pKey, boolean pValue);

    @Accessor("swingState")
    LivingEntity.SwingState ysm$swingState();

    @Invoker("getModifiedSwingDuration")
    int ysm$swingDuration(SwingAnimation animation);
}
