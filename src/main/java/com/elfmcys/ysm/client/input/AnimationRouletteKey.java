// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.input;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import com.elfmcys.ysm.client.gui.AnimationRouletteScreen;
import com.elfmcys.ysm.config.ServerConfig;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.util.InputCheckUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class AnimationRouletteKey {
    public static final KeyMapping ANIMATION_ROULETTE_KEY = new KeyMapping("key.yes_steve_model.animation_roulette.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Z,
            YsmKeyMappings.CATEGORY);

    public static final KeyMapping LOCK_ROULETTE_KEY = new KeyMapping("key.yes_steve_model.lock_roulette.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_L,
            YsmKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onKeyboardInput(InputEvent.Key event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (!InputCheckUtil.isInGame()) {
            return;
        }
        if (event.getAction() == InputConstants.PRESS && InputCheckUtil.keyIsMatch(event, ANIMATION_ROULETTE_KEY)
            && (!NetworkHandler.isRemoteChannelPresent() || ServerConfig.CAN_SWITCH_MODEL.get())) {
            if (TlmClientCompat.pointToMaid()) {
                TlmClientCompat.onRouletteMainKeyPressed();
            } else if (Minecraft.getInstance().player != null) {
                PlayerAnimatableCapabilityProvider.get(Minecraft.getInstance().player).ifPresent(cap -> {
                    String modelId = cap.getModelId();
                    var model = cap.getModelRenderTarget();
                    if (model != null && !model.info().getExtraAnimations().isEmpty()) {
                        if (Minecraft.getInstance().gui.screen() == null) {
                            Minecraft.getInstance().gui.setScreen(new AnimationRouletteScreen(modelId, model, cap));
                            return;
                        }
                        if (Minecraft.getInstance().gui.screen() instanceof AnimationRouletteScreen) {
                            Minecraft.getInstance().gui.setScreen(null);
                        }
                    }
                });
            }
        }
    }
}
