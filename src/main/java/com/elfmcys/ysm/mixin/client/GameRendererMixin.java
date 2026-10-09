// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.natives.NativeProfiler;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Unique
    private boolean ysm$profileFrameActive;

    @Inject(method = "render()V", at = @At("HEAD"))
    private void beforeRender(CallbackInfo ci) {
        ysm$profileFrameActive = NativeProfiler.beginFrame();
    }

    @Inject(method = "render()V", at = @At("RETURN"))
    private void afterRender(CallbackInfo ci) {
        NativeProfiler.endFrame(ysm$profileFrameActive);
        ysm$profileFrameActive = false;
    }
}
