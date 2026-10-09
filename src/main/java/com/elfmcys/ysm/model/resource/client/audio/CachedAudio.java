package com.elfmcys.ysm.model.resource.client.audio;

interface CachedAudio extends AutoCloseable {
    int size();

    boolean pcm();

    CachedAudio acquire();

    @Override
    void close();
}
