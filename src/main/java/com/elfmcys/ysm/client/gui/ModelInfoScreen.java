// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.gui.button.AuthorButton;
import com.elfmcys.ysm.client.gui.button.FlatColorButton;
import com.elfmcys.ysm.client.lang.LanguageManager;
import com.elfmcys.ysm.client.texture.CustomTexture;
import com.elfmcys.ysm.client.texture.CustomTextureManager;
import com.elfmcys.ysm.format.schema.model.views.ModelInfoView;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.resource.client.asset.ClientAssetBatch;
import com.elfmcys.ysm.model.resource.client.asset.ModelAssetSelector;
import com.elfmcys.ysm.model.service.ClientModelService;
import com.elfmcys.ysm.proto.mixel.manifest.info.Author;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("removal")
public class ModelInfoScreen extends Screen {
    private final static Identifier DEFAULT_AVATAR = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/default_avatar.png");
    private final static Map<String, Component> LINK_TYPE_PRESET = ImmutableMap.of(
            "home", Component.translatable("gui.yes_steve_model.url.home"),
            "donate", Component.translatable("gui.yes_steve_model.url.donate")
    );
    private final List<CustomTexture> loadedAvatars = new ArrayList<>();
    private final Map<Integer, AuthorButton> authorButtons = new HashMap<>();

    private final PlayerModelScreen parent;
    private final ModelRenderTarget model;
    private final ModelInfoView modelInfo;
    private int startAuthorIndex = 0;
    private ClientAssetBatch pageAssets;
    private int x;
    private int y;

    public ModelInfoScreen(PlayerModelScreen parent, ModelRenderTarget model) {
        super(Component.literal("Model Info GUI"));
        this.parent = parent;
        this.model = model;
        this.modelInfo = model.info();
    }

    @Override
    public void removed() {
        closePage();
        super.removed();
    }

    @Override
    protected void init() {
        closePage();
        this.clearWidgets();
        pageAssets = ClientModelService.instance().createAssetBatch();

        this.x = (width - 420) / 2;
        this.y = (height - 235) / 2;

        var metadata = this.modelInfo.getMetadata();
        List<Author> authors = metadata == null
                ? List.of() : metadata.authors();
        if (authors.size() <= startAuthorIndex) {
            startAuthorIndex = 0;
        }

        for (int i = 0; i < 5; i++) {
            int index = startAuthorIndex + i;
            if (index >= authors.size()) {
                for (; i < 5; i++) {
                    addRenderableWidget(AuthorButton.empty(this.x + 25 + 75 * i, this.y + 15, this));
                }
                continue;
            }
            var author = authors.get(index);
            var button = new AuthorButton(this.x + 25 + 75 * i, this.y + 15,
                    author, model, DEFAULT_AVATAR, index, this);
            authorButtons.put(index, button);
            addRenderableWidget(button);
            requestAvatar(index, button, pageAssets);
        }
        pageAssets.submit();

        addRenderableWidget(new FlatColorButton(x + 2, y + 25, 18, 100, Component.literal("<"), (b) -> {
            if (startAuthorIndex > 0) {
                startAuthorIndex = Math.max(0, startAuthorIndex - 5);
                this.init();
            }
        }).setTooltips("gui.yes_steve_model.pre_page"));
        addRenderableWidget(new FlatColorButton(x + 25 + 75 * 5, y + 25, 18, 100, Component.literal(">"), (b) -> {
            if (startAuthorIndex + 5 < authors.size()) {
                startAuthorIndex = startAuthorIndex + 5;
                this.init();
            }
        }).setTooltips("gui.yes_steve_model.next_page"));

        int y = this.y + 150;
        for (var i = 0; i < Math.min(metadata.links().size(), 2); i++) {
            var link = metadata.links().get(i);
            var type = link.key();
            var value = link.value_();

            var displayText = LINK_TYPE_PRESET.get(type);
            if (displayText == null) {
                displayText = Component.literal(type);
            }

            addRenderableWidget(new FlatColorButton(this.x + 310, y, 85, 20, displayText, b -> openUrl(value)));
            y += 25;
        }
        addRenderableWidget(new FlatColorButton(this.x + 310, y, 85, 20, Component.translatable("gui.yes_steve_model.model.return"), (b) -> {
            this.getMinecraft().gui.setScreen(parent);
        }));
    }

    private void openUrl(@Nullable String homeUrl) {
        if (homeUrl != null && StringUtils.isNoneBlank(homeUrl)) {
            this.getMinecraft().gui.setScreen(new ConfirmLinkScreen(yes -> {
                if (yes) {
                    com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(homeUrl));
                }
                this.getMinecraft().gui.setScreen(this);
            }, java.net.URI.create(homeUrl), true));
        }
    }

    private void requestAvatar(int index, AuthorButton button, ClientAssetBatch assets) {
        var manifest = ClientModelService.instance().catalog().find(model.modelHash())
                .map(entry -> entry.displayRepresentation().view().getManifest()).orElse(null);
        var metadata = manifest == null ? null : manifest.info().metadataUnsafe();
        if (metadata == null || metadata.authors().isEmpty()
                || index >= metadata.authors().size()
                || !metadata.authors().get(index).hasAvatar()) {
            return;
        }
        assets.presentation(model.modelHash(),
                        ModelAssetSelector.PresentationAsset.AUTHOR_AVATAR, index)
                .whenComplete((source, error) -> Minecraft.getInstance().execute(() -> {
                    if (pageAssets != assets || authorButtons.get(index) != button) {
                        return;
                    }
                    if (source != null) {
                        var texture = ClientModelService.instance().createTexture(source);
                        loadedAvatars.add(texture);
                        button.setAvatar(CustomTextureManager.register(texture).id());
                    } else if (!isCancellation(error)) {
                        if (error == null) {
                            YesSteveModel.LOGGER.debug("Failed to load author avatar {}: unknown error", index);
                        } else {
                            YesSteveModel.LOGGER.debug("Failed to load author avatar {}", index, unwrap(error));
                        }
                    }
                }));
    }

    private void closePage() {
        var closingAssets = pageAssets;
        pageAssets = null;
        loadedAvatars.forEach(CustomTextureManager::release);
        loadedAvatars.clear();
        authorButtons.clear();
        if (closingAssets != null) {
            closingAssets.close();
        }
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

        graphics.fillGradient(this.x + 25, this.y + 150, this.x + 305, this.y + 220, 0x8F_5B5B5B, 0x8F_5B5B5B);

        var metadata = this.modelInfo.getMetadata();
        if (metadata != null) {
            String tips = LanguageManager.getI18n(model, "metadata.tips", metadata.tips().orElse(""));
            List<FormattedCharSequence> splitDesc = font.split(Component.literal(tips), 270);
            int offset = 0;
            for (FormattedCharSequence desc : splitDesc) {
                graphics.text(font, desc, this.x + 30, this.y + 154 + offset, 0xFFFFFFFF);
                offset += font.lineHeight;
                if (offset > font.lineHeight * 7) {
                    break;
                }
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        this.renderables.stream().filter(r -> r instanceof AuthorButton)
                .forEach(r -> ((AuthorButton) r).renderToolTip(graphics, this, mouseX, mouseY));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
