// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.overlay;

import com.elfmcys.ysm.client.gui.ExtraPlayerConfigScreen;
import com.elfmcys.ysm.config.ExtraPlayerScreenConfig;
import com.elfmcys.ysm.util.RenderUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.gui.GuiLayer;

public class ExtraPlayerScreen implements GuiLayer {
    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (ExtraPlayerScreenConfig.DISABLE_PLAYER_RENDER.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.gui.screen() instanceof ExtraPlayerConfigScreen) {
            return;
        }

        double posX = ExtraPlayerScreenConfig.PLAYER_POS_X.get();
        double posY = ExtraPlayerScreenConfig.PLAYER_POS_Y.get();
        float scale = ExtraPlayerScreenConfig.PLAYER_SCALE.get().floatValue();
        float yawOffset = ExtraPlayerScreenConfig.PLAYER_YAW_OFFSET.get().floatValue();

        RenderUtil.renderExtraPlayerEntity(graphics, player, posX, posY, scale, yawOffset, -500,
                deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
