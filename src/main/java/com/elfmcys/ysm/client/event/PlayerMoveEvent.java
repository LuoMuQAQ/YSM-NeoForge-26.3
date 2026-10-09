// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import com.mojang.blaze3d.platform.InputConstants;
import com.elfmcys.ysm.util.InputCheckUtil;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import static com.elfmcys.ysm.client.input.AnimationRouletteKey.LOCK_ROULETTE_KEY;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class PlayerMoveEvent {
    private static boolean LOCK_EXTRA_ANIMATION = false;

    @SubscribeEvent
    public static void onKeyboardInput(InputEvent.Key event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getAction() == InputConstants.PRESS && InputCheckUtil.keyIsMatch(event, LOCK_ROULETTE_KEY)) {
            LOCK_EXTRA_ANIMATION = !LOCK_EXTRA_ANIMATION;
        }
    }

    /**
     * 改用 TickEvent.ClientTickEvent 监听按键状态，避免与其他模组（如 Touch Controller） 冲突时漏判按键。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (LOCK_EXTRA_ANIMATION) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && isMoveKey(player)) {
            PlayerAnimatableCapabilityProvider.get(player).ifPresent(cap -> {
                if (cap.isPlayingExtraAnimation()) {
                    cap.stopExtraAnimation();
                    if (NetworkHandler.isRemoteChannelPresent()) {
                        ClientProtocolGateway.stopSelfAnimation();
                    }
                }
            });
        }
    }

    public static boolean isMoveKey(LocalPlayer player) {
        ClientInput input = player.input;
        return input != null && (hasImpulse(input.getMoveVector().x) || hasImpulse(input.getMoveVector().y)
               || input.keyPresses.jump() || input.keyPresses.shift());
    }

    private static boolean hasImpulse(float impulse) {
        return Math.abs(impulse) > 1.0E-5F;
    }

    public static void switchLock() {
        LOCK_EXTRA_ANIMATION = !LOCK_EXTRA_ANIMATION;
    }

    public static boolean isLocked() {
        return LOCK_EXTRA_ANIMATION;
    }
}
