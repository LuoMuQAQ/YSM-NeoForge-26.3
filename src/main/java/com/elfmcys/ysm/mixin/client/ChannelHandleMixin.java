package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.client.sound.instance.HostAudioHandoff;
import com.elfmcys.ysm.client.sound.instance.SoundChannelHandleExtension;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChannelAccess.ChannelHandle.class)
public class ChannelHandleMixin implements SoundChannelHandleExtension {
    @Unique
    private HostAudioHandoff ysm$handoff;
    @Unique
    private boolean ysm$released;

    @Override
    public void ysm$bindHandoff(HostAudioHandoff handoff) {
        boolean released;
        synchronized (this) {
            if (ysm$handoff != null && ysm$handoff != handoff) {
                throw new IllegalStateException("Sound channel already has a handoff");
            }
            released = ysm$released;
            if (!released) {
                ysm$handoff = handoff;
            }
        }
        if (released) {
            handoff.hostReleased();
        }
    }

    @Inject(method = "release", at = @At("RETURN"))
    private void releaseAudioHandoff(CallbackInfo callback) {
        HostAudioHandoff handoff;
        synchronized (this) {
            ysm$released = true;
            handoff = ysm$handoff;
            ysm$handoff = null;
        }
        if (handoff != null) {
            handoff.hostReleased();
        }
    }
}
