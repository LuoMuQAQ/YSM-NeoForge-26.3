// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.util.InputCheckUtil;
import com.elfmcys.ysm.client.input.ModelInputCodes;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT, modid = YesSteveModel.MOD_ID)
public class ModInputEvent {
    public static final boolean[] KEY_STATES = new boolean[ModelInputCodes.KEY_COUNT];
    public static final boolean[] MOUSE_STATES = new boolean[ModelInputCodes.MOUSE_COUNT];

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        int key = ModelInputCodes.keyboard(event.getKey());
        if (key >= 0 && event.getAction() == InputConstants.RELEASE) {
            KEY_STATES[key] = false;
        } else if (key >= 0 && InputCheckUtil.isInGame() && event.getAction() == InputConstants.PRESS) {
            KEY_STATES[key] = true;
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Post event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        int button = ModelInputCodes.mouse(event.getButton());
        if (button >= 0 && event.getAction() == InputConstants.RELEASE) {
            MOUSE_STATES[button] = false;
        } else if (button >= 0 && InputCheckUtil.isInGame() && event.getAction() == InputConstants.PRESS) {
            MOUSE_STATES[button] = true;
        }
    }

    @SubscribeEvent
    public static void clearInactiveInput(ClientTickEvent.Post event) {
        if (!YesSteveModel.isAvailable() || !InputCheckUtil.isInGame()) {
            Arrays.fill(KEY_STATES, false);
            Arrays.fill(MOUSE_STATES, false);
        }
    }
}
