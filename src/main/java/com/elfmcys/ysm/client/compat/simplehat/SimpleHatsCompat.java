// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.simplehat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public class SimpleHatsCompat {
    private static final String SIMPLE_HATS = "simplehats";
    private static boolean IS_LOADED = false;

    public static void init() {
        IS_LOADED = ModList.get().isLoaded(SIMPLE_HATS);
    }

    @Nullable
    public static ItemStack getCuriosHead(LivingEntity livingEntity) {
        if (IS_LOADED && !com.elfmcys.ysm.client.compat.OptionalApi.isDisabled(SIMPLE_HATS)) {
            return HatCuriosCompat.getCuriosHead(livingEntity);
        }
        return null;
    }
}
