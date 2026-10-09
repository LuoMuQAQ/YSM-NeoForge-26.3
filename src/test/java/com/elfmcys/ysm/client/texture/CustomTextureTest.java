package com.elfmcys.ysm.client.texture;

import com.elfmcys.ysm.natives.image.ImageSource;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomTextureTest {
    private static final ImageSource UNUSED_SOURCE = () -> {
        throw new IOException("unused");
    };

    @BeforeAll
    static void establishRenderOwner() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.initRenderThread();
        }
    }

    @Test
    void firstLoadAndReloadDecodeUploadSynchronouslyAndClosePixels() {
        var events = new ArrayList<String>();
        var images = new ArrayList<NativeImage>();
        var texture = new CustomTexture(UNUSED_SOURCE, source -> {
            events.add("decode");
            var image = new NativeImage(1, 1, false);
            images.add(image);
            return image;
        }, (ignored, image) -> {
            image.getPixelRGBA(0, 0);
            events.add("upload");
        });

        texture.load(null);
        assertEquals(List.of("decode", "upload"), events);
        assertClosed(images.get(0));

        texture.load(null);
        assertEquals(List.of("decode", "upload", "decode", "upload"), events);
        assertClosed(images.get(1));
        assertTrue(texture.failure().isEmpty());
    }

    @Test
    void decodeFailureIsVisibleBeforeLoadReturnsAndBlocksReload() {
        var attempts = new AtomicInteger();
        var failure = new IOException("broken");
        var texture = new CustomTexture(UNUSED_SOURCE, source -> {
            attempts.incrementAndGet();
            throw failure;
        }, (ignored, image) -> {
            throw new AssertionError("upload must not run after decode failure");
        });

        texture.load(null);
        texture.load(null);

        assertEquals(1, attempts.get());
        assertSame(failure, texture.failure().orElseThrow());
    }

    @Test
    void uploadFailureClosesPixelsAndRemainsVisibleAcrossClose() {
        var image = new NativeImage(1, 1, false);
        var failure = new IllegalStateException("upload failed");
        var texture = new CustomTexture(UNUSED_SOURCE, source -> image,
                (ignored, pixels) -> {
                    throw failure;
                });

        texture.load(null);
        texture.close();

        assertSame(failure, texture.failure().orElseThrow());
        assertClosed(image);
    }

    private static void assertClosed(NativeImage image) {
        assertThrows(IllegalStateException.class, () -> image.getPixelRGBA(0, 0));
    }
}
