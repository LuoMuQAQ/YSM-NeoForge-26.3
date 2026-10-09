package com.elfmcys.ysm.client.sound.instance;

import com.elfmcys.ysm.client.sound.stream.AudioStreamProvider;
import com.elfmcys.ysm.mixin.client.SoundEngineAccessor;
import com.elfmcys.ysm.mixin.client.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class CustomSoundInstance extends MinecraftSoundInstance {
    private final AudioStreamProvider provider;
    private volatile HostAudioHandoff handoff;

    public CustomSoundInstance(SoundEvent soundEvent, AudioStreamProvider provider, Entity entity) {
        super(soundEvent, entity);
        this.provider = provider;
        provider.stopped().thenRun(() -> Minecraft.getInstance().execute(() -> {
            if (!super.isStopped()) {
                setStopped();
            }
        }));
    }

    @Override
    public @NotNull CompletableFuture<AudioStream> getStream(@NotNull SoundBufferLibrary soundBuffers, @NotNull Sound sound, boolean looping) {
        var ticket = new HostAudioHandoff(provider, this::hostTerminated);
        handoff = ticket;
        var soundManager = Minecraft.getInstance().getSoundManager();
        var engine = ((SoundManagerAccessor) soundManager).ysm$getSoundEngine();
        var handle = ((SoundEngineAccessor) engine)
                .ysm$getInstanceToChannel().get(this);
        if (!(handle instanceof SoundChannelHandleExtension extension)) {
            ticket.failed();
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Model audio has no live channel handoff"));
        }
        extension.ysm$bindHandoff(ticket);
        return provider.openStream(looping)
                .thenApply(stream -> (AudioStream) ticket.offer(stream))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        ticket.failed();
                    }
                });
    }

    @Override
    public void tick() {
        super.tick();
        if (super.isStopped()) {
            sealPlayback();
        }
    }

    @Override
    public void setStopped() {
        sealPlayback();
        super.setStopped();
    }

    private void sealPlayback() {
        var ticket = handoff;
        if (ticket != null) {
            ticket.stop();
        } else {
            provider.stop();
        }
    }

    private void hostTerminated() {
        if (!super.isStopped()) {
            stop();
        }
    }
}
