// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.animation.AnimationRegister;
import com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent;
import com.elfmcys.ysm.client.gui.button.CatalogTextureButton;
import com.elfmcys.ysm.client.gui.button.FlatColorButton;
import com.elfmcys.ysm.client.gui.button.FlatIconButton;
import com.elfmcys.ysm.model.resource.client.ModelRenderTarget;
import com.elfmcys.ysm.model.service.ClientModelService;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import com.elfmcys.ysm.util.RenderUtil;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PlayerTextureScreen extends Screen {
    private static final String ANIMATION_ANNOTATIONS = "——";
    private static final float SCALE_MAX = 360f;
    private static final float SCALE_MIN = 18f;
    private static final float PITCH_MAX = 90f;
    private static final float PITCH_MIN = -90f;
    private static final CustomGuiPlayerEntity[] TEXTURE_BUTTON_ENTITY = new CustomGuiPlayerEntity[4];
    private static final int LEFT_MOUSE_BUTTON = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT_MOUSE_BUTTON = InputConstants.MOUSE_BUTTON_RIGHT;

    protected final CustomGuiPlayerEntity previewEntity = new CustomGuiPlayerEntity();
    protected final ModelRenderTarget model;
    protected final Hash256 modelHash;
    protected final String modelPath;
    private final PlayerModelScreen parent;
    private final List<String> textures;
    private final List<String> animations;
    private final List<CatalogTextureButton> textureButtons = new ArrayList<>();
    private String selectedTexture;
    private String animation = "";
    private int maxTexturePage;
    private int texturePage;
    private int maxAnimationPage;
    private int animationPage;
    protected int x;
    protected int y;
    protected float posX;
    protected float posY = -60;
    protected float scale = 80;
    protected float yaw = 165;
    protected float pitch = -5;
    protected boolean showGround = true;

    static {
        for (var index = 0; index < TEXTURE_BUTTON_ENTITY.length; index++) {
            TEXTURE_BUTTON_ENTITY[index] = new CustomGuiPlayerEntity();
        }
    }

    public PlayerTextureScreen(PlayerModelScreen parent, Hash256 modelHash, ModelRenderTarget model) {
        super(Component.literal("Player Texture GUI"));
        this.parent = parent;
        this.modelHash = modelHash;
        this.model = model;
        var entry = ClientModelService.instance().catalog().find(modelHash)
                .orElseThrow(() -> new IllegalArgumentException("Unknown model hash: " + modelHash));
        this.modelPath = entry.displayPath();
        this.textures = entry.displayRepresentation().view().getPlayer().getTextureNames().stream()
                .sorted().toList();
        this.selectedTexture = model.playerResources().defaultTextureName();
        this.animations = new ArrayList<>(model.playerResources().animations().keySet());
        this.animations.removeIf(name -> name.startsWith(ANIMATION_ANNOTATIONS));
        this.animations.sort(String::compareTo);
        previewEntity.getPreviewInfo().setPreview(AnimationRegister.IDLE);
        previewEntity.updateModelAndTexture(modelHash, selectedTexture);
    }

    @Override
    protected void init() {
        closePage();
        clearWidgets();
        x = (width - 420) / 2;
        y = (height - 235) / 2;
        maxTexturePage = Math.max(0, (textures.size() - 1) / 4);
        maxAnimationPage = Math.max(0, (animations.size() - 1) / 11);
        texturePage = Math.min(texturePage, maxTexturePage);
        animationPage = Math.min(animationPage, maxAnimationPage);

        addRenderableWidget(new FlatColorButton(x + 5, y, 80, 18,
                Component.translatable("gui.yes_steve_model.model.return"),
                ignored -> getMinecraft().gui.setScreen(parent)));
        addRenderableWidget(new FlatIconButton(x + 281, y + 2, 16, 16, 64, 16,
                ignored -> animation = AnimationRegister.IDLE)
                .setTooltips("gui.yes_steve_model.model.stop"));
        addRenderableWidget(new FlatIconButton(x + 263, y + 2, 16, 16, 48, 16, ignored -> {
            posX = 0;
            posY = -60;
            scale = 80;
            yaw = 165;
            pitch = -5;
        }).setTooltips("gui.yes_steve_model.model.reset"));
        addRenderableWidget(new FlatIconButton(x + 245, y + 2, 16, 16, 64, 0,
                ignored -> showGround = !showGround).setTooltips("gui.yes_steve_model.model.ground"));

        addRenderableWidget(new FlatColorButton(x + 321, y + 213, 18, 18, Component.literal("<"), ignored -> {
            if (texturePage > 0) {
                texturePage--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(x + 383, y + 213, 18, 18, Component.literal(">"), ignored -> {
            if (texturePage < maxTexturePage) {
                texturePage++;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(x + 11, y + 214, 16, 16, Component.literal("<"), ignored -> {
            if (animationPage > 0) {
                animationPage--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(x + 63, y + 214, 16, 16, Component.literal(">"), ignored -> {
            if (animationPage < maxAnimationPage) {
                animationPage++;
                init();
            }
        }));

        addAnimationButtons();
        addTextureButtons();
    }

    private void addAnimationButtons() {
        for (var slot = 0; slot < 11; slot++) {
            var index = slot + animationPage * 11;
            if (index >= animations.size()) {
                break;
            }
            var name = animations.get(index);
            var key = "gui.yes_steve_model.texture.button.%s".formatted(name.replace(':', '.'));
            var keyDesc = key + ".desc";
            var label = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(name);
            var button = new FlatColorButton(x + 5, y + 27 + 17 * slot, 80, 16, label,
                    ignored -> animation = name);
            if (Language.getInstance().has(keyDesc)) {
                button.setTooltips(Lists.newArrayList(
                        Component.translatable(keyDesc).withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.yes_steve_model.texture.button.animation_name", name)
                                .withStyle(ChatFormatting.GRAY)));
            }
            addRenderableWidget(button);
        }
    }

    private void addTextureButtons() {
        for (var slot = 0; slot < 4; slot++) {
            var index = slot + texturePage * 4;
            if (index >= textures.size()) {
                break;
            }
            var button = new CatalogTextureButton(x + 306 + 56 * (slot % 2),
                    y + 5 + 104 * (slot / 2), modelHash, modelPath, textures.get(index),
                    TEXTURE_BUTTON_ENTITY[slot], this::selectTexture);
            textureButtons.add(button);
            addRenderableWidget(button);
        }
    }

    protected void selectTexture(Hash256 hash, String path, String texture, @Nullable ModelRenderTarget renderTarget) {
        selectedTexture = texture;
        previewEntity.updateModelAndTexture(hash, texture);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerAnimatableCapabilityProvider.get(player).ifPresent(capability -> {
                capability.updateModelAndTexture(hash, texture);
                if (NetworkHandler.isRemoteChannelPresent()) {
                    ClientProtocolGateway.selectModel(hash, texture);
                }
            });
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(x, y + 22, x + 90, y + 235, 0xFF222222, 0xFF222222);
        graphics.fillGradient(x + 93, y, x + 299, y + 235, 0xFF222222, 0xFF222222);
        graphics.fillGradient(x + 302, y, x + 420, y + 235, 0xFF222222, 0xFF222222);

        if (!previewEntity.getPreviewInfo().hasPreview(animation)) {
            previewEntity.getPreviewInfo().setPreview(animation);
        }
        renderReferenceEntity(graphics, x + 93, y, x + 299, y + 235, partialTick);

        var texturePageInfo = "%d/%d".formatted(texturePage + 1, maxTexturePage + 1);
        graphics.text(font, texturePageInfo, x + 302 + (118 - font.width(texturePageInfo)) / 2,
                y + 223 - font.lineHeight / 2, 0xFFF3EFE0);
        var animationPageInfo = "%d/%d".formatted(animationPage + 1, maxAnimationPage + 1);
        graphics.text(font, animationPageInfo, x + 5 + (80 - font.width(animationPageInfo)) / 2,
                y + 218, 0xFFF3EFE0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderables.stream().filter(FlatColorButton.class::isInstance).map(FlatColorButton.class::cast)
                .forEach(button -> button.renderToolTip(graphics, this, mouseX, mouseY));
    }

    protected void renderReferenceEntity(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float partialTick) {
        graphics.enableScissor(x0, y0, x1, y1);
        try {
            RenderUtil.renderTextureScreenEntity(graphics, x + 299 / 2.0F + 40 + posX,
                    y + 235 / 2.0F + 80 + posY, scale, pitch, yaw, partialTick,
                    previewEntity, RegisterEntityRenderersEvent.getPlayerRenderer(), showGround);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!inViewRange(event.x(), event.y())) {
            return false;
        }
        if (event.button() == LEFT_MOUSE_BUTTON) {
            yaw += (float) (1.5 * dragX);
            pitch = Mth.clamp(pitch - (float) dragY, PITCH_MIN, PITCH_MAX);
        } else if (event.button() == RIGHT_MOUSE_BUTTON) {
            posX += (float) dragX;
            posY += (float) dragY;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var delta = scrollY;
        if (delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (inViewRange(mouseX, mouseY)) {
            scale = Mth.clamp(scale + (float) delta * 0.07f * scale, SCALE_MIN, SCALE_MAX);
            return true;
        }
        if (inAnimationRange(mouseX, mouseY)) {
            var next = page(animationPage, maxAnimationPage, delta);
            if (next != animationPage) {
                animationPage = next;
                init();
            }
            return true;
        }
        if (inTextureRange(mouseX, mouseY)) {
            var next = page(texturePage, maxTexturePage, delta);
            if (next != texturePage) {
                texturePage = next;
                init();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        closePage();
        previewEntity.reset();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeTextureButtons() {
        textureButtons.forEach(CatalogTextureButton::close);
        textureButtons.clear();
    }

    private void closePage() {
        closeTextureButtons();
    }

    private int page(int current, int maximum, double delta) {
        var next = delta > 0 ? Math.max(0, current - 1) : Math.min(maximum, current + 1);
        if (next != current) {
            getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
        }
        return next;
    }

    private boolean inViewRange(double mouseX, double mouseY) {
        return mouseX > x + 93 && mouseX < x + 299 && mouseY > y && mouseY < y + 235;
    }

    private boolean inAnimationRange(double mouseX, double mouseY) {
        return mouseX > x && mouseX < x + 90 && mouseY > y + 22 && mouseY < y + 235;
    }

    private boolean inTextureRange(double mouseX, double mouseY) {
        return mouseX > x + 302 && mouseX < x + 420 && mouseY > y && mouseY < y + 235;
    }
}
