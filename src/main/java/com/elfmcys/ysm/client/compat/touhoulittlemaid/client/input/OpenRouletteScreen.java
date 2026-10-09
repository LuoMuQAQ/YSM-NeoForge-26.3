// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.input;

import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import com.elfmcys.ysm.client.gui.AnimationRouletteScreen;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;

public class OpenRouletteScreen {
    public static boolean pointToMaid() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (!(Minecraft.getInstance().hitResult instanceof EntityHitResult result)) {
            return false;
        }
        if (result.getEntity() instanceof LivingEntity maid && MaidApi.isMaid(maid)) {
            if (!MaidApi.flag(maid, "isYsmModel")) {
                return false;
            }
            return player.getUUID().equals(MaidApi.read(maid, "getOwnerUUID"));
        }
        return false;
    }

    public static void onRouletteMainKeyPressed() {
        if (!(Minecraft.getInstance().hitResult instanceof EntityHitResult result)) {
            return;
        }
        if (result.getEntity() instanceof LivingEntity maid && MaidApi.isMaid(maid)) {
            YsmMaidCapabilityProvider.get(maid).ifPresent(cap -> {
                var model = cap.getModelRenderTarget();
                if (model != null && !model.info().getExtraAnimations().isEmpty()) {
                    if (Minecraft.getInstance().gui.screen() == null) {
                        Minecraft.getInstance().gui.setScreen(new AnimationRouletteScreen(cap.getModelId(), model, cap));
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
