// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.animation.AnimationParallelTicker;
import com.elfmcys.ysm.util.RenderUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void ysm$beforeExtract(DeltaTracker deltaTracker, net.minecraft.client.Camera camera, float partialTick, CallbackInfo callback) {
        if (YesSteveModel.isAvailable()) {
            RenderUtil.setRenderingLevel(true);
            AnimationParallelTicker.scheduleAll(partialTick);
        }
    }
}

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void ysm$afterRender(CallbackInfo callback) {
        if (YesSteveModel.isAvailable()) {
            AnimationParallelTicker.waitAll();
            RenderUtil.setRenderingLevel(false);
        }
    }
}
