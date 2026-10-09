// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThrowableItemProjectile.class)
public interface ThrowableItemProjectileAccessor {
    @Invoker("getDefaultItem")
    Item ysm$GetDefaultItem();
}
