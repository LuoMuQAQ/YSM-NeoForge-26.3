// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.concurrent.TimeUnit;

@OnlyIn(Dist.CLIENT)
public final class AnimatableCacheUtil {
    public static final Cache<Identifier, Entity> ENTITIES_CACHE = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();
}
