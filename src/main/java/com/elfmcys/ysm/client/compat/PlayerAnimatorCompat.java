// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.fml.ModList;

public class PlayerAnimatorCompat {
    private static final String MOD_ID = "player_animation_library";
    private static boolean INSTALLED;

    public static void init() {
        INSTALLED = ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }

    public static boolean hasThirdPersonModelAnim(AbstractClientPlayer player) {
        if (isInstalled()) {
            var stack = PlayerAnimationAccess.getPlayerAnimManager(player);
            return stack.getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL;
        }
        return false;
    }
}
