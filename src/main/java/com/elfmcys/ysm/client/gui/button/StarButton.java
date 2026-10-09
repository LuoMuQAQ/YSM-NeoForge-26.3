// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.StarModelsCapabilityProvider;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class StarButton extends FlatColorButton {
    private final static Identifier ICON = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    public StarButton(int x, int y) {
        super(x, y, 20, 20, Component.empty(), (b) -> {
        });
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        int startX = (this.width - 16) / 2;
        int startY = (this.height - 16) / 2;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerAnimatableCapabilityProvider.get(player).ifPresent(modelInfoCap -> StarModelsCapabilityProvider.get(player).ifPresent(starModelsCap -> {
                var modelHash = modelInfoCap.getModelHash();
                if (modelHash != null && starModelsCap.containModel(modelHash)) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, this.getX() + startX, this.getY() + startY,
                            16, 0, 16, 16, 256, 256);
                } else {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, this.getX() + startX, this.getY() + startY,
                            0, 0, 16, 16, 256, 256);
                }
            }));
        }
    }

    @Override
    public void onPress(InputWithModifiers input) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerAnimatableCapabilityProvider.get(player).ifPresent(modelInfoCap -> StarModelsCapabilityProvider.get(player).ifPresent(starModelsCap -> {
                var modelHash = modelInfoCap.getModelHash();
                if (modelHash == null) {
                    return;
                }
                if (starModelsCap.containModel(modelHash)) {
                    starModelsCap.removeModel(modelHash);
                    ClientProtocolGateway.updateStar(modelHash, false);
                } else {
                    starModelsCap.addModel(modelHash);
                    ClientProtocolGateway.updateStar(modelHash, true);
                }
            }));
        }
    }
}
