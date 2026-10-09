// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.texture;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.natives.image.ImageSource;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class CustomTexture extends ModelTexture {
    private final ImageSource source;
    private final Decoder decoder;
    private final Uploader uploader;
    private @Nullable Throwable failure;

    public CustomTexture(ImageSource source) {
        this(source, CustomTexture::decode, CustomTexture::upload);
    }

    CustomTexture(ImageSource source, Decoder decoder, Uploader uploader) {
        this.source = Objects.requireNonNull(source, "source");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.uploader = Objects.requireNonNull(uploader, "uploader");
    }

    @Override
    public void load(ResourceManager resourceManager) {
        RenderSystem.assertOnRenderThread();
        if (texture != null) {
            return;
        }
        if (failure == null) {
            try (var pixels = decoder.decode(source)) {
                uploader.upload(this, pixels);
                return;
            } catch (Exception error) {
                failure = error;
                YesSteveModel.LOGGER.debug("Failed to load standalone GUI texture from {}", source, error);
            }
        }
        // Avatar/icon callers can still sample this mapping. Keep the diagnostic
        // and give them an owned checkerboard, including after delayed eviction.
        try (var missing = MissingTextureAtlasSprite.generateMissingImage()) {
            uploadPixels(missing);
        } catch (RuntimeException error) {
            error.addSuppressed(failure);
            throw error;
        }
    }

    private static NativeImage decode(ImageSource source) throws Exception {
        try (var image = source.open()) {
            return image.decode();
        }
    }

    private static void upload(CustomTexture texture, NativeImage img) {
        texture.uploadPixels(img);
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    @FunctionalInterface
    interface Decoder {
        NativeImage decode(ImageSource source) throws Exception;
    }

    @FunctionalInterface
    interface Uploader {
        void upload(CustomTexture texture, NativeImage image);
    }
}
