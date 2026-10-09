// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.lang.LanguageManager;
import com.elfmcys.ysm.model.resource.client.asset.ClientAssetBatch;
import com.elfmcys.ysm.model.service.ClientModelService;
import com.elfmcys.ysm.model.catalog.client.ModelPackInfo;
import com.elfmcys.ysm.client.texture.CustomTexture;
import com.elfmcys.ysm.client.texture.CustomTextureManager;
import com.elfmcys.ysm.client.texture.TextureHolder;
import com.elfmcys.ysm.model.domain.ModelPackDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class PackButton extends Button implements AutoCloseable {
    private final static Identifier ICON = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/default_pack_icon.png");

    private final ModelPackInfo pack;
    private @Nullable CustomTexture texture;
    private TextureHolder icon;
    private boolean closed;

    public PackButton(int x, int y, int width, int height, ModelPackInfo pack,
                      @Nullable ModelPackDescriptor descriptor, ClientAssetBatch assets, OnPress onPress) {
        super(x, y, width, height, Component.literal(LanguageManager.getI18n(pack, "name", pack.name())), onPress, DEFAULT_NARRATION);
        this.pack = pack;
        this.icon = pack.icon() == null ? null : CustomTextureManager.register(pack.icon(), 10 * 20);
        if (descriptor != null && descriptor.coverHash() != null) {
            assets.packCover(descriptor)
                    .whenComplete((source, error) -> Minecraft.getInstance().execute(() -> {
                        if (closed) {
                            return;
                        }
                        if (source != null) {
                            texture = ClientModelService.instance().createTexture(source);
                            icon = CustomTextureManager.register(texture, 10 * 20);
                        } else if (!isCancellation(error)) {
                            if (error == null) {
                                YesSteveModel.LOGGER.debug(
                                        "Failed to load model pack cover {}: unknown error",
                                        descriptor.hierarchy());
                            } else {
                                YesSteveModel.LOGGER.debug("Failed to load model pack cover {}",
                                        descriptor.hierarchy(), unwrap(error));
                            }
                        }
                    }));
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDeltaTime) {
        if (icon == null && pack.icon() != null) {
            icon = CustomTextureManager.register(pack.icon(), 10 * 20);
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        int backgroundColor = 0xFF_9B51E0;
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, backgroundColor, backgroundColor);

        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height - 20);
        try {
            var image = icon == null ? ICON : icon.id();
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, image, getX(), getY(), 0, 0,
                    width, height, width, height);
        } finally {
            graphics.disableScissor();
        }

        if (this.isHoveredOrFocused()) {
            graphics.nextStratum();
            int sideColor = 0xFF_E1BEE7;
            graphics.fillGradient(this.getX(), this.getY() + 1, this.getX() + 1, this.getY() + this.height - 1, sideColor, sideColor);
            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, sideColor, sideColor);
            graphics.fillGradient(this.getX() + this.width - 1, this.getY() + 1, this.getX() + this.width, this.getY() + this.height - 1, sideColor, sideColor);
            graphics.fillGradient(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, sideColor, sideColor);
        }

        graphics.nextStratum();
        graphics.fill(getX() + 1, getY() + height - 20, getX() + width - 1, getY() + height - 1, 0xFF434242);
        List<FormattedCharSequence> split = font.split(getMessage(), 45);
        if (split.size() > 1) {
            drawCenteredString(graphics, font, split.get(0), getX() + width / 2, getY() + height - 19, 0xFFF3EFE0);
            drawCenteredString(graphics, font, split.get(1), getX() + width / 2, getY() + height - 10, 0xFFF3EFE0);
        } else {
            drawCenteredString(graphics, font, getMessage(), getX() + width / 2, getY() + height - 15, 0xFFF3EFE0);
        }
    }

    public void renderComponentTooltip(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Screen screen, int pMouseX, int pMouseY) {
        String desc = LanguageManager.getI18n(pack, "description", pack.desc());
        if (StringUtils.isBlank(desc)) {
            return;
        }
        List<Component> mutableComponents = Collections.singletonList(Component.literal(desc));
        if (this.isHovered()) {
            graphics.setComponentTooltipForNextFrame(screen.getMinecraft().font, mutableComponents, pMouseX, pMouseY);
        }
    }

    private static void drawCenteredString(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text) / 2, y, color, false);
    }

    private static void drawCenteredString(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text) / 2, y, color, false);
    }

    @Override
    public void close() {
        closed = true;
        if (texture != null) {
            CustomTextureManager.release(texture);
            texture = null;
        }
        icon = null;
    }

    private static boolean isCancellation(@Nullable Throwable error) {
        return unwrap(error) instanceof CancellationException;
    }

    private static Throwable unwrap(@Nullable Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
