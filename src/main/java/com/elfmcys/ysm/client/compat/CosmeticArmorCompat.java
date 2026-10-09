// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Optional;

public class CosmeticArmorCompat {
    private static final String MOD_ID = "cosmeticarmorreworked";
    private static boolean INSTALLED;

    public static void init() {
        INSTALLED = ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }

    public static Optional<ItemStack> getSkinArmorItem(Player player, EquipmentSlot slot) {
        if (!slot.isArmor()) {
            return Optional.empty();
        }

        return OptionalApi.query(MOD_ID, Optional.empty(), () -> {
        var cosInventory = OptionalApi.callStatic("lain.mods.cos.api.CosArmorAPI", "getCAStacksClient", player.getUUID());
        if (OptionalApi.bool(OptionalApi.call(cosInventory, "isSkinArmor", slot.getIndex()))) {
            return Optional.of(ItemStack.EMPTY);
        }

        var skinArmor = (ItemStack) OptionalApi.call(cosInventory, "getStackInSlot", slot.getIndex());
        return skinArmor.isEmpty() ? Optional.empty() : Optional.of(skinArmor);
        });
    }
}
