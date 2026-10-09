package com.elfmcys.ysm.client.sound.stream;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface AudioStreamProvider {
    @NotNull
    CompletableFuture<CustomAudioStream> openStream(boolean looping);

    void stop();

    CompletionStage<Void> stopped();
}
