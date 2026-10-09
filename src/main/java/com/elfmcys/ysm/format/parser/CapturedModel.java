package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.model.domain.Hash256;
import java.util.List;
import java.util.Objects;

public record CapturedModel(Hash256 modelId, CaptureData data,
                            List<RawModelDiagnostic> diagnostics)
        implements AutoCloseable {
    public CapturedModel {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(data, "data");
        diagnostics = List.copyOf(diagnostics);
    }

    public CapturedModel(Hash256 modelId, CaptureData data) {
        this(modelId, data, List.of());
    }

    @Override
    public void close() {
        data.close();
    }
}
