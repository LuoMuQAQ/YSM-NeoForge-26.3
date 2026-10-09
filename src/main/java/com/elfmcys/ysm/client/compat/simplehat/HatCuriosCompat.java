// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.simplehat;

import com.elfmcys.ysm.client.compat.curios.CuriosCompatInner;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

public class HatCuriosCompat {
    private static final String SLOT_TYPE = "head";

    @Nullable
    public static ItemStack getCuriosHead(LivingEntity livingEntity) {
        return CuriosApi.getCuriosInventory(livingEntity)
                .map(handler -> handler)
                .flatMap(handler -> handler.getStacksHandler(SLOT_TYPE))
                .map(curiosHandler -> CuriosCompatInner.searchCuriosHandler(curiosHandler, itemStack -> OptionalApi.query("simplehats", false, () -> OptionalApi.instance("fonnymunkey.simplehats.common.item.HatItem", itemStack.getItem()))))
                .orElse(null);
    }
}
