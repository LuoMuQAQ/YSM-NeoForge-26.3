package com.elfmcys.ysm.model.resource.client;

import java.util.Objects;

public record BakeProfile(String key) {
    public BakeProfile {
        key = Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Bake profile key cannot be blank");
        }
    }
}
