package com.elfmcys.ysm.model.resource.client;

import com.elfmcys.ysm.format.schema.model.views.SoundStreamView;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;

import java.util.Objects;

/** Metadata-only locator for one sound in an exact activated representation. */
public record SoundSource(ModelFileIdentity representation, SoundStreamView stream) {
    public SoundSource {
        Objects.requireNonNull(representation, "representation");
        Objects.requireNonNull(stream, "stream");
    }
}
