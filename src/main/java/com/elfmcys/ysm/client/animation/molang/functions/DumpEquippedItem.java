// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.molang.functions;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.function.entity.LivingEntityFunction;
import com.elfmcys.ysm.geckolib3.util.MolangUtils;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import com.elfmcys.ysm.util.EquipmentUtil;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;


public class DumpEquippedItem extends LivingEntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        if (!context.entity().isDebugEnabled()) {
            return null;
        }

        EquipmentSlot slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null) {
            return null;
        }

        ItemStack itemStack = EquipmentUtil.getEquippedItem(context.entity().entity(), slotType);
        if (itemStack.isEmpty()) {
            return null;
        }

        Identifier id = RegistryIds.itemId(itemStack.getItem());
        if (id == null) {
            return null;
        }
        context.entity().debugPrint(Component.literal("Display ").append(ComponentUtils.copyOnClickText(itemStack.getItem().getName(itemStack).getString(99))));
        context.entity().debugPrint(Component.literal("Name ").append(ComponentUtils.copyOnClickText(id.toString())));

        itemStack.typeHolder().tags().forEach(key -> {
            context.entity().debugPrint(Component.literal("Tag ").append(ComponentUtils.copyOnClickText(key.location().toString())));
        });

        for (Holder<Enchantment> holder : itemStack.getEnchantments().keySet()) {
            int level = itemStack.getEnchantments().getLevel(holder);
            Identifier enchantmentId = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
            if (enchantmentId == null || level <= 0) {
                continue;
            }
            context.entity().debugPrint(Component.literal("Enchantment: display ").append(ComponentUtils.copyOnClickText(Enchantment.getFullname(holder, level).getString(99)))
                            .append(Component.literal("  name ").append(ComponentUtils.copyOnClickText(enchantmentId.toString()))));
        }

        return null;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
