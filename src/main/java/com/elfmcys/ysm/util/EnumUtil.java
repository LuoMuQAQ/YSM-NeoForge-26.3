// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.util;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemUseAnimation;
import org.apache.commons.lang3.EnumUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class EnumUtil {
    private final static Object2ReferenceOpenHashMap<String, EquipmentSlot> EQUIPMENT_SLOTS =
            new Object2ReferenceOpenHashMap<>(Arrays.stream(EquipmentSlot.values()).collect(Collectors.toMap(u -> u.getName().toLowerCase(Locale.US), u -> u)));

    public static Optional<ItemUseAnimation> getUseAnim(String name) {
        return Optional.ofNullable(EnumUtils.getEnum(ItemUseAnimation.class, name.toUpperCase(Locale.US)));
    }

    public static Optional<EquipmentSlot> getEquipmentSlot(String name) {
        return Optional.ofNullable(EQUIPMENT_SLOTS.get(name));
    }
}
