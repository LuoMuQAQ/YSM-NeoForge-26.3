package com.elfmcys.ysm.model;

/** Lifecycle bridge between NeoForge setup/shutdown and the process model-system owner. */
public final class ModelRuntime {
    private static ModelSystem system;

    private ModelRuntime() {
    }

    public static synchronized void initialize() {
        if (system != null) {
            return;
        }
        // JVM shutdown hooks run after FML has closed the mod module. Host lifecycle
        // events close this owner while its classes can still be loaded.
        system = ModelSystem.openDefault();
    }

    public static synchronized ModelSystem system() {
        if (system == null) {
            throw new IllegalStateException("Model runtime has not been initialized");
        }
        return system;
    }

    public static synchronized void close() {
        var current = system;
        system = null;
        if (current != null) {
            current.close();
        }
    }
}
