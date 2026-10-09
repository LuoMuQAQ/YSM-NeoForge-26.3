// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.gui;

import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import com.elfmcys.ysm.client.gui.ModelInfoScreen;
import com.elfmcys.ysm.client.gui.PlayerModelScreen;
import com.elfmcys.ysm.client.gui.PlayerTextureScreen;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.util.ModelIdUtil;
import com.elfmcys.ysm.util.NameUtil;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.Objects;

public class MaidModelScreen extends PlayerModelScreen {
    private final LivingEntity maid;

    public MaidModelScreen(LivingEntity maid) {
        this.maid = maid;
    }

    @Override
    protected void selectModel(Hash256 hash, String path, String texture, ModelRenderTarget renderTarget) {
        var name = renderTarget == null ? Component.literal(ModelIdUtil.getFileNameFromPath(path))
                : NameUtil.getModeName(renderTarget, path);
        YsmMaidCapabilityProvider.get(maid).ifPresent(capability ->
                capability.setYsmModel(path, texture));
        OptionalApi.query(MaidApi.ID, null, () -> OptionalApi.call(
                OptionalApi.getStatic(MaidApi.ROOT + "network.NetworkHandler", "CHANNEL"), "sendToServer",
                OptionalApi.construct(MaidApi.ROOT + "network.message.YsmMaidModelMessage", maid.getId(), path, texture, name)));
    }

    @Override
    protected PlayerTextureScreen getTextureScreen(PlayerModelScreen parent, Hash256 modelHash,
                                                    ModelRenderTarget model) {
        var maidModel = YsmMaidCapabilityProvider.get(maid)
                .map(capability -> capability.getModelRenderTarget()).orElse(null);
        return new MaidTextureScreen(parent, modelHash, Objects.requireNonNullElse(maidModel, model), maid);
    }

    @Override
    protected ModelInfoScreen getModelInfoScreen(PlayerModelScreen parent, ModelRenderTarget model) {
        var maidModel = YsmMaidCapabilityProvider.get(maid)
                .map(capability -> capability.getModelRenderTarget()).orElse(null);
        return new ModelInfoScreen(parent, Objects.requireNonNullElse(maidModel, model));
    }

    @Override
    protected void renderReferenceEntity(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(x + 5, y + 29, x + 130, y + 200);
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + 5, y + 29, x + 130, y + 200, 70,
                    0.0625F, mouseX, mouseY, maid);
        } finally {
            graphics.disableScissor();
        }

        YsmMaidCapabilityProvider.get(maid).ifPresent(capability -> {
            var renderTarget = capability.getModelRenderTarget();
            var path = capability.getModelId();
            var modelName = renderTarget == null ? ModelIdUtil.getFileNameFromPath(path)
                    : renderTarget.getDisplayName(ModelIdUtil.getFileNameFromPath(path));
            var lineY = y + 205;
            for (FormattedCharSequence line : font.split(FormattedText.of(modelName), 125)) {
                graphics.text(font, line, x + (135 - font.width(line)) / 2, lineY, 0xFFF3EFE0);
                lineY += 10;
            }
        });
    }
}
