package com.elfmcys.ysm.client.sound.instance;

public interface HostAwareAudioStream {
    /** Linearizes the actual channel attachment against stop/release. */
    boolean ysm$tryHostAdopt();
}
