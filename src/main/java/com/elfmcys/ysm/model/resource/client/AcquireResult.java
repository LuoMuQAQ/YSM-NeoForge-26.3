package com.elfmcys.ysm.model.resource.client;

import java.util.Objects;

public sealed interface AcquireResult {
    record Ready(ModelRenderTarget target) implements AcquireResult {
        public Ready {
            Objects.requireNonNull(target, "target");
        }
    }

    record Pending() implements AcquireResult {
    }

    record Failed(ResourceFailure failure) implements AcquireResult {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
