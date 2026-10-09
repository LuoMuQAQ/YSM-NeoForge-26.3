// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.swem;

import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Locale;

public class SwemCompatInner {
    static boolean isHorse(Entity entity) {
        return OptionalApi.query("swem", false, () -> OptionalApi.instance(
                "com.alaharranhonor.swem.forge.entities.horse.SWEMHorseEntityBase", entity));
    }

    static String state(Entity horse) {
        return OptionalApi.query("swem", "", () -> {
            double jumpHeight = ((Number) OptionalApi.get(horse, "jumpHeight")).doubleValue();
            if (jumpHeight > 0) return "jump_lv" + Math.min(Mth.ceil(jumpHeight), 5);
            if (notMoving(horse)) return "idle";
            return OptionalApi.enumName(OptionalApi.call(horse, "getGait")).toLowerCase(Locale.ENGLISH);
        });
    }

    static String getAnimation(LivingEntity entity) {
        var horse = entity.getVehicle();
        if (!isHorse(horse)) return null;
        var name = state(horse);
        return name.isEmpty() ? null : "swem:" + name;
    }

    static boolean notMoving(Entity horse) {
        double x = horse.getX() - horse.xo;
        double z = horse.getZ() - horse.zo;
        return Math.sqrt(x * x + z * z) <= 0d;
    }
}
