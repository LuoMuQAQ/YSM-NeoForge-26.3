package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.model.resource.client.BakeProfile;

import java.util.Objects;

public record RenderTargetKey(String targetId, String selectedTexture,
                              BakeProfile bakeProfile) {
    public RenderTargetKey {
        targetId = Objects.requireNonNull(targetId, "targetId");
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("Render target id cannot be blank");
        }
        selectedTexture = Objects.requireNonNullElse(selectedTexture, "");
        Objects.requireNonNull(bakeProfile, "bakeProfile");
    }
}
