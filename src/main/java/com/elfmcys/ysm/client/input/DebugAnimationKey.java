// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.input;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.gui.overlay.DebugAnimationScreen;
import com.elfmcys.ysm.util.InputCheckUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;


@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class DebugAnimationKey {
    public static final KeyMapping DEBUG_ANIMATION_KEY = new KeyMapping("key.yes_steve_model.debug_animation.desc",
            KeyConflictContext.IN_GAME, KeyModifier.ALT,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_B,
            YsmKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onKeyboardInput(InputEvent.Key event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (!InputCheckUtil.isInGame()) {
            return;
        }
        if (event.getAction() == InputConstants.PRESS && InputCheckUtil.keyIsMatch(event, DEBUG_ANIMATION_KEY)) {
            if (!DebugAnimationScreen.isEnabled()) {
                DebugAnimationScreen.enable();
            } else {
                DebugAnimationScreen.disable();
            }
        }
    }
}
