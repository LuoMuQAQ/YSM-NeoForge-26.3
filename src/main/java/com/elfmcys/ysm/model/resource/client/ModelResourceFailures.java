package com.elfmcys.ysm.model.resource.client;

public interface ModelResourceFailures {
    ModelResourceFailureGate texture(String resource);

    ModelResourceFailureGate animation(String resource);

    static ModelResourceFailures none() {
        return NoResourceFailures.INSTANCE;
    }

    enum NoResourceFailures implements ModelResourceFailures {
        INSTANCE;

        @Override
        public ModelResourceFailureGate texture(String resource) {
            return ModelResourceFailureGate.none();
        }

        @Override
        public ModelResourceFailureGate animation(String resource) {
            return ModelResourceFailureGate.none();
        }
    }
}
