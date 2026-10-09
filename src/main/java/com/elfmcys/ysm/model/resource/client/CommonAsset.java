package com.elfmcys.ysm.model.resource.client;

import com.elfmcys.ysm.geckolib3.core.molang.value.IValue;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

import java.util.List;
import java.util.Map;

public class CommonAsset {
    private final Map<String, SoundSource> sounds;
    private final Object2ReferenceOpenHashMap<String, IValue> userFunctions;
    private final Object2ReferenceOpenHashMap<String, List<IValue>> eventHandlers;

    public CommonAsset(Map<String, SoundSource> sounds, Object2ReferenceOpenHashMap<String, IValue> userFunctions,
                       Object2ReferenceOpenHashMap<String, List<IValue>> eventHandlers) {
        this.sounds = Map.copyOf(sounds);
        this.userFunctions = userFunctions;
        this.eventHandlers = eventHandlers;
    }

    public Map<String, SoundSource> sounds() {
        return sounds;
    }

    public Object2ReferenceOpenHashMap<String, IValue> userFunctions() {
        return userFunctions;
    }

    public Object2ReferenceOpenHashMap<String, List<IValue>> eventHandlers() {
        return eventHandlers;
    }

}
