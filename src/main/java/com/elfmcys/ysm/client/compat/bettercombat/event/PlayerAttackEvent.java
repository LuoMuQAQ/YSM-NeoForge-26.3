// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.bettercombat.event;

import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import com.elfmcys.ysm.mixin.client.LivingEntityAccessor;
import com.elfmcys.ysm.mixin.client.LivingEntitySwingStateAccessor;
import net.minecraft.world.item.component.SwingAnimation;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

public class PlayerAttackEvent implements BetterCombatClientEvents.PlayerAttackStart {
    @Override
    public void onPlayerAttackStart(LocalPlayer player, AttackHand hand) {
        var swingingHand = hand.isOffHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var entity = (LivingEntityAccessor) player;
        // Each Better Combat attack restarted the old swing fields unconditionally.
        // LivingEntity.swing now refuses an early restart; use the host state owner.
        ((LivingEntitySwingStateAccessor) entity.ysm$swingState()).ysm$restart(
                swingingHand, SwingAnimation.DEFAULT, entity.ysm$swingDuration(SwingAnimation.DEFAULT));
        ClientProtocolGateway.swingHand(swingingHand);
    }
}
