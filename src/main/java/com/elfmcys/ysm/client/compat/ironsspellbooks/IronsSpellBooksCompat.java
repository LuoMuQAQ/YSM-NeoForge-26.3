// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.ironsspellbooks;

import com.elfmcys.ysm.client.animation.molang.CtrlBinding;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

public class IronsSpellBooksCompat {
    private static final String ID = "irons_spellbooks";
    private static boolean IS_LOADED = false;

    public static void init() {
        IS_LOADED = ModList.get().isLoaded(ID);
    }

    public static boolean isInstalled() {
        return IS_LOADED && !com.elfmcys.ysm.client.compat.OptionalApi.isDisabled(ID);
    }

    public static void addBinding(CtrlBinding binding) {
        if (IS_LOADED && !com.elfmcys.ysm.client.compat.OptionalApi.isDisabled(ID)) {
            IronsSpellBooksCompatInner.addInnerBinding(binding);
        } else {
            addEmptyBinding(binding);
        }
    }

    @Nullable
    public static PlayState playAnimation(AnimationEvent<CustomHumanoidEntity<?>> event, LivingEntity entity) {
        if (IS_LOADED && !com.elfmcys.ysm.client.compat.OptionalApi.isDisabled(ID)) {
            return IronsSpellBooksCompatInner.playAnimation(event, entity);
        }
        return null;
    }

    /**
     * 没有安装此模组时，这些 molang 应该存在，否则会报错
     */
    private static void addEmptyBinding(CtrlBinding binding) {
        binding.clientPlayerVar("iss_animation", ctx -> "");
    }
}
