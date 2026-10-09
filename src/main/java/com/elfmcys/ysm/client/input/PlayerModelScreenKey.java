// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.input;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.gui.ConfigScreen;
import com.elfmcys.ysm.client.gui.DisclaimerScreen;
import com.elfmcys.ysm.client.gui.PlayerModelScreen;
import com.elfmcys.ysm.config.ClientConfig;
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
public class PlayerModelScreenKey {
    public static final KeyMapping PLAYER_MODEL_KEY = new KeyMapping("key.yes_steve_model.player_model.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Y,
            YsmKeyMappings.CATEGORY);

    @SubscribeEvent
    public static void onKeyboardInput(InputEvent.Key event) {
        if (!InputCheckUtil.isInGame()) {
            return;
        }
        if (event.getAction() == InputConstants.PRESS && InputCheckUtil.keyIsMatch(event, PLAYER_MODEL_KEY)) {
            if (!YesSteveModel.isAvailable()) {
                YesSteveModel.sendUnavailableMessage();
                return;
            }
            if (NetworkHandler.isRemoteChannelPresent() && !ServerConfig.CAN_SWITCH_MODEL.get()) {
                Minecraft.getInstance().gui.setScreen(new ConfigScreen(null));
                return;
            }
            if (ClientConfig.DISCLAIMER_SHOW.get()) {
                Minecraft.getInstance().gui.setScreen(new DisclaimerScreen());
            } else {
                Minecraft.getInstance().gui.setScreen(new PlayerModelScreen());
            }
        }
    }
}
