package com.elfmcys.ysm.capability;

import java.util.Optional;
import java.util.function.Supplier;

/** Transient holder for one client entity owner, without client classes in the common registry. */
public final class ClientRuntimeAttachment {
    private Object state;

    public synchronized <T> T getOrCreate(Class<T> type, Supplier<T> factory) {
        if (state == null) {
            state = java.util.Objects.requireNonNull(factory.get());
        }
        return type.cast(state);
    }

    public synchronized <T> Optional<T> existing(Class<T> type) {
        return Optional.ofNullable(state).map(type::cast);
    }
}
