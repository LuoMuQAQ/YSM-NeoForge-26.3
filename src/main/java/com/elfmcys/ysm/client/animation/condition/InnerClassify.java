// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.condition;

import com.elfmcys.ysm.client.compat.slashblade.SlashBladeCompat;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import com.elfmcys.ysm.init.ModItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;

public class InnerClassify {
    private static final String EMPTY = "";

    public static String doClassifyTest(String extraPre, LivingEntity livingEntity, InteractionHand hand) {
        ItemStack itemInHand = livingEntity.getItemInHand(hand);
        String classify = getClassify(itemInHand);
        if (!classify.equals(EMPTY)) {
            return extraPre + classify;
        }
        return EMPTY;
    }

    public static String getClassify(ItemStack itemInHand) {
        Item item = itemInHand.getItem();
        // 优先判断拔刀剑
        if (SlashBladeCompat.isSlashBladeItem(itemInHand)) {
            return "slashblade";
        }
        if (itemInHand.typeHolder().is(ItemTags.SWORDS) || itemInHand.typeHolder().is(ModItemTags.SWORDS)) {
            return "sword";
        }
        if (TlmClientCompat.isGohei(item)) {
            return "gohei";
        }
        if (itemInHand.typeHolder().is(ItemTags.AXES) || itemInHand.typeHolder().is(ModItemTags.AXES)) {
            return "axe";
        }
        if (itemInHand.typeHolder().is(ItemTags.PICKAXES) || itemInHand.typeHolder().is(ModItemTags.PICKAXES)) {
            return "pickaxe";
        }
        if (itemInHand.typeHolder().is(ItemTags.SHOVELS) || itemInHand.typeHolder().is(ModItemTags.SHOVELS)) {
            return "shovel";
        }
        if (itemInHand.typeHolder().is(ItemTags.HOES) || itemInHand.typeHolder().is(ModItemTags.HOES)) {
            return "hoe";
        }
        if (item instanceof ShieldItem || itemInHand.typeHolder().is(ModItemTags.SHIELDS)) {
            return "shield";
        }
        if (item instanceof CrossbowItem || itemInHand.typeHolder().is(ModItemTags.CROSSBOWS)) {
            return "crossbow";
        }
        if (item instanceof BowItem || itemInHand.typeHolder().is(ModItemTags.BOWS)) {
            return "bow";
        }
        if (item instanceof FishingRodItem || itemInHand.typeHolder().is(ModItemTags.FISHING_RODS)) {
            return "fishing_rod";
        }
        // 对，就这个名字
        if (item instanceof TridentItem || itemInHand.typeHolder().is(ModItemTags.TRIDENTS)) {
            return "spear";
        }
        if (item instanceof ThrowablePotionItem || itemInHand.typeHolder().is(ModItemTags.THROWABLE_POTION)) {
            return "throwable_potion";
        }
        return EMPTY;
    }
}
