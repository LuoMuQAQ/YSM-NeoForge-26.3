// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.bettercombat;

import com.elfmcys.ysm.client.animation.molang.CtrlBinding;
import com.elfmcys.ysm.client.compat.bettercombat.event.PlayerAttackEvent;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.bettercombat.client.animation.AttackAnimationStack;
import net.minecraft.client.player.AbstractClientPlayer;

public class BetterCombatCompatInner {
    static void innerInit() {
        BetterCombatClientEvents.ATTACK_START.register(new PlayerAttackEvent());
    }

    static void addInnerBinding(CtrlBinding binding) {
        binding.clientPlayerVar("bcombat_attack_animation", BetterCombatCompatInner::getAttackAnimation);
    }

    private static String getAttackAnimation(IContext<AbstractClientPlayer> context) {
        var layer = PlayerAnimationAccess.getPlayerAnimationLayer(context.entity(), AttackAnimationStack.ID);
        if (!(layer instanceof AttackAnimationStack stack) || !stack.isActive()) {
            return "";
        }
        var animation = stack.getCurrentAnimationInstance();
        return animation == null ? "" : animation.getNameOrId();
    }
}
