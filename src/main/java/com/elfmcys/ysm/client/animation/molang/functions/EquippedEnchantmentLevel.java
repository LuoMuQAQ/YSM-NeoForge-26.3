// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.molang.functions;

import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.function.entity.LivingEntityFunction;
import com.elfmcys.ysm.geckolib3.util.MolangUtils;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import com.elfmcys.ysm.util.EquipmentUtil;
import com.elfmcys.ysm.util.RegistryIds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

public class EquippedEnchantmentLevel extends LivingEntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null) {
            return null;
        }
        ItemStack itemStack = EquipmentUtil.getEquippedItem(context.entity().entity(), slotType);
        if (itemStack.isEmpty()) {
            return 0;
        }

        int sum = 0;
        for (var i = 1; i < arguments.size(); ++i) {
            Identifier id = arguments.getAsResourceLocation(context, i);
            if (id != null) {
                var enchantment = RegistryIds.enchantmentHolder(context.entity().entity().level(), id);
                if (enchantment != null) {
                    sum += itemStack.getEnchantments().getLevel(enchantment);
                }
            }
        }

        return sum;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
