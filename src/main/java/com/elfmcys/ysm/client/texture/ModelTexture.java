package com.elfmcys.ysm.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

/** Render-owner texture publication; the caller retains ownership of decoded pixels. */
public abstract class ModelTexture extends AbstractTexture {
    public abstract void load(ResourceManager resources) throws IOException;

    final void uploadPixels(NativeImage pixels) {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        var uploadedSampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        GpuTexture uploaded = device.createTexture("YSM model texture",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, pixels.getWidth(), pixels.getHeight(), 1, 1);
        GpuTextureView view = null;
        boolean adopted = false;
        try {
            view = device.createTextureView(uploaded);
            device.createCommandEncoder().writeToTexture(uploaded, pixels);
            releaseTextures();
            texture = uploaded;
            textureView = view;
            sampler = uploadedSampler;
            adopted = true;
        } finally {
            if (!adopted) {
                try {
                    if (view != null) {
                        view.close();
                    }
                } finally {
                    uploaded.close();
                }
            }
        }
    }
}
