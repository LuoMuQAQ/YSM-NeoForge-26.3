// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.texture.CustomPBRTextureSet;
import com.elfmcys.ysm.info.type.PBRTextureType;
import com.elfmcys.ysm.natives.render.VertexFormatType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pbr.loader.PBRTextureLoader;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;

import static com.elfmcys.ysm.natives.render.VertexFormatType.IRIS_54;
import static com.elfmcys.ysm.natives.render.VertexFormatType.IRIS_55;
import static com.elfmcys.ysm.natives.render.VertexFormatType.IRIS_56;
import static com.elfmcys.ysm.natives.render.VertexFormatType.IRIS_56_AR;

public class IrisCompat {
    private static final String MOD_ID = "iris";
    private static boolean INSTALLED = false;
    private static LongSupplier ENTITY_ID_GETTER;
    private static VertexFormat ENTITY_FORMAT;

    public static void init() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(mod -> {
            try {
                ENTITY_FORMAT = IrisVertexFormats.ENTITY;
                ENTITY_ID_GETTER = IrisCompat::getEntityIdModern;
                ENTITY_ID_GETTER.getAsLong();
                IrisApi.getInstance().isRenderingShadowPass();
                PBRLoader.register();
                INSTALLED = true;
            } catch (RuntimeException | LinkageError e) {
                YesSteveModel.LOGGER.error("Failed to setup Iris compat", e);
                ENTITY_FORMAT = null;
                ENTITY_ID_GETTER = null;
                INSTALLED = false;
            }
        });
    }

    public static Optional<VertexFormatType> determineVertexFormatType(VertexFormat vertexFormat) {
        if (isInstalled() && ENTITY_FORMAT == vertexFormat) {
            if (vertexFormat.getVertexSize() == 56) {
                return Optional.of(ARCompat.isInstalled() ? IRIS_56_AR : IRIS_56);
            }
            if (vertexFormat.getVertexSize() == 55) {   // 逆天设计
                return Optional.of(IRIS_55);
            }
            if (vertexFormat.getVertexSize() == 54) {
                return Optional.of(IRIS_54);
            }
            // 低于 1.20 的版本还有 48 字节的格式，暂不做支持
        }
        return Optional.empty();
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }

    public static boolean isRenderingShadow() {
        return isInstalled() && IrisApi.getInstance().isRenderingShadowPass();
    }

    public static void requirePbrTextures(
            CustomPBRTextureSet texture,
            Consumer<AbstractTexture> hostOwnership) {
        if (!isInstalled()) {
            throw new IllegalStateException("Iris PBR integration is not installed");
        }
        if (!(texture.getTexture() instanceof com.mojang.renderpearl.backend.opengl.GlTexture gpu)) {
            throw new IllegalStateException("Iris PBR requires an OpenGL texture");
        }
        int id = gpu.glId();
        // Custom model textures are not ReloadableTexture; publish their actual GPU identity.
        net.irisshaders.iris.pbr.TextureTracker.INSTANCE.trackTexture(id, texture);
        var holder = net.irisshaders.iris.pbr.texture.PBRTextureManager.INSTANCE.getOrLoadHolder(id);
        requireExpected(texture, holder.normalTexture(), holder.specularTexture(), hostOwnership);
    }

    private static void requireExpected(
            CustomPBRTextureSet texture,
            AbstractTexture actualNormal,
            AbstractTexture actualSpecular,
            Consumer<AbstractTexture> hostOwnership) {
        IllegalStateException failure = null;
        if (texture.getNormal() != null) {
            if (texture.getNormal() == actualNormal) {
                hostOwnership.accept(texture.getNormal());
            } else {
                failure = new IllegalStateException(
                        "Iris did not adopt the expected normal texture");
            }
        }
        if (texture.getSpecular() != null) {
            if (texture.getSpecular() == actualSpecular) {
                hostOwnership.accept(texture.getSpecular());
            } else {
                var specularFailure = new IllegalStateException(
                        "Iris did not adopt the expected specular texture");
                if (failure == null) {
                    failure = specularFailure;
                } else {
                    failure.addSuppressed(specularFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public static long getEntityId() {
        return isInstalled() ? ENTITY_ID_GETTER.getAsLong() : 0;
    }

    private static long getEntityIdModern() {
        short s0 = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        short s1 = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        short s2 = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
        return s0 | ((long) s1 << 16) | ((long) s2 << 32);     // little endian
    }

    private static class PBRLoader implements PBRTextureLoader<CustomPBRTextureSet> {
        private static final PBRLoader INSTANCE = new PBRLoader();

        private PBRLoader(){
        }

        @Override
        public void load(CustomPBRTextureSet texture, ResourceManager resourceManager, PBRTextureLoader.PBRTextureConsumer pbrTextureConsumer) {
            loadPbr(texture, resourceManager);
            var normalTexture = texture.getPBRTextures().get(PBRTextureType.NORMAL);
            if (normalTexture != null) {
                pbrTextureConsumer.acceptNormalTexture(normalTexture);
            }
            var specularTexture = texture.getPBRTextures().get(PBRTextureType.SPECULAR);
            if (specularTexture != null) {
                pbrTextureConsumer.acceptSpecularTexture(specularTexture);
            }
        }

        public static void register() {
            PBRTextureLoaderRegistry.INSTANCE.register(CustomPBRTextureSet.class, INSTANCE);
        }
    }

    private static void loadPbr(CustomPBRTextureSet texture, ResourceManager resourceManager) {
        try {
            texture.loadPbr(resourceManager);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load model PBR textures", error);
        }
    }
}
