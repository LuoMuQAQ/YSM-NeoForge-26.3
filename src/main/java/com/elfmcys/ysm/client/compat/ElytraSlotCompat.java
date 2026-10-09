// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public class ElytraSlotCompat {
    private static final String MOD_ID = "elytraslot";
    private static boolean INSTALLED;

    public static void init() {
        INSTALLED = ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }

    public static ItemStack getEquippedElytraItem(LivingEntity entity) {
        return OptionalApi.query(MOD_ID, ItemStack.EMPTY, () -> (ItemStack) OptionalApi.call(
                OptionalApi.getStatic("com.illusivesoulworks.elytraslot.platform.Services", "ELYTRA"), "getEquipped", entity));
    }
}
