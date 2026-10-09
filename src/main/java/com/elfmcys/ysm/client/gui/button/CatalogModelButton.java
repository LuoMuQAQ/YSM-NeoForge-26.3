// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.StarModelsCapabilityProvider;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.elfmcys.ysm.client.gui.CustomGuiPlayerEntity;
import com.elfmcys.ysm.model.resource.client.asset.ClientAssetBatch;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.catalog.client.entry.CatalogModelMetadata;
import com.elfmcys.ysm.model.catalog.client.entry.ClientCatalogEntry;
import com.elfmcys.ysm.client.texture.TextureHolder;
import com.elfmcys.ysm.config.ClientConfig;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.util.ModelIdUtil;
import com.elfmcys.ysm.util.RenderUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A catalog entry that progresses from loading indicator to preview image to a full local renderTarget. */
public final class CatalogModelButton extends Button implements AutoCloseable {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    private final ClientCatalogEntry entry;
    private final CatalogModelMetadata metadata;
    private final CustomGuiPlayerEntity entity;
    private final boolean needAuth;
    private final SelectionHandler selection;
    private final RenderTargetHandler openRenderTarget;
    private final Component pathName;
    private final CatalogModelCardState state;

    public CatalogModelButton(int x, int y, ClientCatalogEntry entry, boolean needAuth,
                              ClientAssetBatch assets,
                              CustomGuiPlayerEntity entity, SelectionHandler selection,
                              RenderTargetHandler openRenderTarget) {
        super(x, y, 52, 90, name(CatalogModelMetadata.from(entry)), ignored -> { }, DEFAULT_NARRATION);
        this.entry = entry;
        this.metadata = CatalogModelMetadata.from(entry);
        this.entity = entity;
        this.needAuth = needAuth;
        this.selection = selection;
        this.openRenderTarget = openRenderTarget;
        this.pathName = Component.literal(ModelIdUtil.getFileNameFromPath(metadata.path()));
        this.state = new CatalogModelCardState(assets, entry, metadata, entity);
    }

    public Hash256 modelHash() {
        return entry.modelHash();
    }

    public @Nullable ModelRenderTarget renderTarget() {
        return state.renderTarget();
    }

    public void updateDemand(long hoverGeneration, boolean bakeEligible) {
        state.updateDemand(hoverGeneration, bakeEligible);
    }

    @Override
    public Component getMessage() {
        return ClientConfig.SHOW_MODEL_ID_FIRST.get() ? pathName : super.getMessage();
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        if (!needAuth) {
            selection.select(entry.modelHash(), metadata.path(), metadata.defaultTexture(), state.renderTarget());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var renderTarget = state.renderTarget();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT
                && isMouseOver(event.x(), event.y()) && renderTarget != null) {
            openRenderTarget.open(entry.modelHash(), metadata.path(), renderTarget);
            return true;
        }
        if (needAuth) {
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        state.updatePreviewAnimations(isHovered(), isFocused(), Util.getMillis());
        var color = needAuth ? 0x7F000000 : 0xFF434242;
        graphics.fillGradient(getX(), getY(), getX() + width, getY() + height, color, color);
        var renderTarget = state.renderTarget();
        if (renderTarget != null) {
            renderImage(graphics, state.background());
            renderEntity(graphics);
            renderImage(graphics, state.foreground());
        } else {
            var preview = state.preview();
            if (preview != null) {
                renderImage(graphics, preview);
            } else {
                renderLoading(graphics);
            }
        }
        renderState(graphics);
        renderName(graphics);
    }

    public void renderTooltip(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        if (!isHovered()) {
            return;
        }
        var locale = Minecraft.getInstance().getLanguageManager().getSelected();
        var input = CatalogModelTooltipFormatter.input(metadata, entry, locale, state.loadError());
        var lines = CatalogModelTooltipFormatter.format(input, Minecraft.getInstance().hasShiftDown(), I18n::get);
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (var line : lines) {
            wrapped.addAll(screen.getMinecraft().font.split(
                    Component.literal(line.text()).withStyle(line.color()),
                    CatalogModelTooltipFormatter.MAX_WIDTH));
        }
        graphics.setTooltipForNextFrame(screen.getMinecraft().font, wrapped, mouseX, mouseY);
    }

    @Override
    public void close() {
        state.close();
    }

    private void renderEntity(GuiGraphicsExtractor graphics) {
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height - 20);
        try {
            RenderUtil.renderModelInGui(graphics, getX() + width / 2f, getY() + height / 2f + 20f, 30f,
                    RenderUtil.guiPartialTick(), entity,
                    RegisterEntityRenderersEvent.getPlayerRenderer(),
                    metadata.info().getSettings().disablePreviewRotation(), true);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderImage(GuiGraphicsExtractor graphics, @Nullable TextureHolder image) {
        if (image == null) {
            return;
        }
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height - 20);
        try {
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.id(), getX(), getY(), 0, 0, width, height, width, height);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderLoading(GuiGraphicsExtractor graphics) {
        var phase = (int) ((Util.getMillis() / 250L) % 4L);
        var dots = ".".repeat(phase);
        graphics.centeredText(Minecraft.getInstance().font, dots, getX() + width / 2,
                getY() + (height - 20) / 2, 0xFFF3EFE0);
    }

    private void renderName(GuiGraphicsExtractor graphics) {
        // Keep deferred preview textures and authorization shading below the title.
        graphics.nextStratum();
        graphics.fill(getX() + 1, getY() + height - 20, getX() + width - 1, getY() + height - 1, 0xFF434242);
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> split = font.split(getMessage(), 45);
        if (split.size() > 1) {
            graphics.centeredText(font, split.get(0), getX() + width / 2, getY() + height - 19, 0xFFF3EFE0);
            graphics.centeredText(font, split.get(1), getX() + width / 2, getY() + height - 10, 0xFFF3EFE0);
        } else {
            graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + height - 15, 0xFFF3EFE0);
        }
    }

    private void renderState(GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
        if (!needAuth && isHoveredOrFocused()) {
            graphics.fillGradient(getX(), getY() + 1, getX() + 1, getY() + height - 1, 0xFFF3EFE0, 0xFFF3EFE0);
            graphics.fillGradient(getX(), getY(), getX() + width, getY() + 1, 0xFFF3EFE0, 0xFFF3EFE0);
            graphics.fillGradient(getX() + width - 1, getY() + 1, getX() + width, getY() + height - 1, 0xFFF3EFE0, 0xFFF3EFE0);
            graphics.fillGradient(getX(), getY() + height - 1, getX() + width, getY() + height, 0xFFF3EFE0, 0xFFF3EFE0);
        }
        if (needAuth) {
            graphics.fillGradient(getX(), getY(), getX() + width, getY() + height, 0x9F222222, 0x9F222222);
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            StarModelsCapabilityProvider.get(player).ifPresent(stars -> {
                if (stars.containModel(entry.modelHash())) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, getX() + width - 14, getY(), 16, 0, 16, 16, 256, 256);
                }
            });
        }
    }

    private static Component name(CatalogModelMetadata metadata) {
        return Component.literal(CatalogModelTooltipFormatter.displayName(metadata,
                Minecraft.getInstance().getLanguageManager().getSelected()));
    }

    @FunctionalInterface
    public interface SelectionHandler {
        void select(Hash256 hash, String path, String texture, @Nullable ModelRenderTarget renderTarget);
    }

    @FunctionalInterface
    public interface RenderTargetHandler {
        void open(Hash256 hash, String path, ModelRenderTarget renderTarget);
    }
}
