// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.gui;

import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.elfmcys.ysm.client.gui.PlayerModelScreen;
import com.elfmcys.ysm.client.gui.PlayerTextureScreen;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.util.NameUtil;
import com.elfmcys.ysm.util.RenderUtil;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

public class MaidTextureScreen extends PlayerTextureScreen {
    private final LivingEntity maid;

    public MaidTextureScreen(PlayerModelScreen parent, Hash256 modelHash, ModelRenderTarget model, LivingEntity maid) {
        super(parent, modelHash, model);
        this.maid = maid;
    }

    @Override
    protected void selectTexture(Hash256 hash, String path, String texture, @Nullable ModelRenderTarget renderTarget) {
        previewEntity.updateModelAndTexture(hash, texture);
        var name = NameUtil.getModeName(renderTarget == null ? model : renderTarget, path);
        YsmMaidCapabilityProvider.get(maid).ifPresent(capability ->
                capability.setYsmModel(path, texture));
        OptionalApi.query(MaidApi.ID, null, () -> OptionalApi.call(
                OptionalApi.getStatic(MaidApi.ROOT + "network.NetworkHandler", "CHANNEL"), "sendToServer",
                OptionalApi.construct(MaidApi.ROOT + "network.message.YsmMaidModelMessage", maid.getId(), path, texture, name)));
    }

    @Override
    protected void renderReferenceEntity(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float partialTick) {
        graphics.enableScissor(x0, y0, x1, y1);
        try {
            YsmMaidCapabilityProvider.get(maid).ifPresent(capability -> {
                var hash = capability.getModelHash();
                if (hash != null) {
                    previewEntity.updateModelAndTexture(hash, capability.getTextureName());
                }
                RenderUtil.renderTextureScreenEntity(graphics, x + 299 / 2.0F + 40 + posX,
                        y + 235 / 2.0F + 80 + posY, scale, pitch, yaw, partialTick,
                        previewEntity, RegisterEntityRenderersEvent.getPlayerRenderer(), showGround);
            });
        } finally {
            graphics.disableScissor();
        }
    }
}
