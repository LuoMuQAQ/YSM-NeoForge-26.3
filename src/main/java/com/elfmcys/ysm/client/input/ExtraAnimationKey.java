// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.input;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.event.PlayerMoveEvent;
import com.elfmcys.ysm.client.gui.AnimationRouletteScreen;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import com.elfmcys.ysm.util.InputCheckUtil;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.List;

import static com.elfmcys.ysm.client.gui.AnimationRouletteScreen.addRootClassify;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ExtraAnimationKey {
    public static final List<KeyMapping> EXTRA_ANIMATION_KEYS = Lists.newArrayList();

    public static void registerKeyBinding(RegisterKeyMappingsEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        EXTRA_ANIMATION_KEYS.clear();
        for (int i = 0; i <= 7; i++) {
            String name = String.format("key.yes_steve_model.extra_animation.%d.desc", i);
            KeyMapping keyMapping = new KeyMapping(name,
                    KeyConflictContext.IN_GAME,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYBOARD,
                    InputConstants.UNKNOWN.getValue(),
                    YsmKeyMappings.CATEGORY);
            event.register(keyMapping);
            EXTRA_ANIMATION_KEYS.add(keyMapping);
        }
    }

    @SubscribeEvent
    public static void onKeyboardInput(InputEvent.Key event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (!InputCheckUtil.isInGame()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        for (KeyMapping key : EXTRA_ANIMATION_KEYS) {
            if (event.getAction() == InputConstants.PRESS && InputCheckUtil.keyIsMatch(event, key)
                && player != null && !PlayerMoveEvent.isMoveKey(player)) {
                PlayerAnimatableCapabilityProvider.get(player).ifPresent(cap -> {
                    var model = cap.getModelRenderTarget();
                    int index = EXTRA_ANIMATION_KEYS.indexOf(key);
                    if (model == null) {
                        return;
                    }
                    var info = model.info();
                    var animations = info.getExtraAnimations();
                    if (animations.size() > index) {
                        String keyName = animations.get(index).key();
                        if ("#return".equals(keyName)) {
                            // #return 为停止播放轮盘动画
                            ClientProtocolGateway.stopSelfAnimation();
                        } else if (keyName.startsWith("#")
                                && info.getExtraAnimationClassifications().containsKey(keyName.substring(1))) {
                            addRootClassify(keyName.substring(1));
                            AnimationRouletteScreen screen = new AnimationRouletteScreen(
                                    info.getExtraAnimationButtons(),
                                    info.getExtraAnimationClassifications(),
                                    model, cap
                            );
                            Minecraft.getInstance().gui.setScreen(screen);
                        } else {
                            ClientProtocolGateway.playSelfAnimation(keyName);
                        }
                    }
                });
                return;
            }
        }
    }
}
