package com.elfmcys.ysm.model.resource.client.render;

import com.elfmcys.ysm.model.catalog.builtin.BuiltinModelIndex;
import com.elfmcys.ysm.model.resource.client.AnimationStore;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Animation;
import com.elfmcys.ysm.proto.mixel.manifest.asset.RenderTarget;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Published only after a default target has fully bound its animations. */
public final class DefaultAnimationRuntime {
    private volatile Map<String, AnimationStore> stores = Map.of();
    private final CurrentAnimationValidator validator;

    public DefaultAnimationRuntime(BuiltinModelIndex contract) {
        this.validator = contract::requireCurrent;
    }

    DefaultAnimationRuntime() {
        validator = (target, animationSet, animation) -> { };
    }

    public synchronized void publish(String domain, AnimationStore store) {
        publishAll(Map.of(domain, store));
    }

    public synchronized void publishAll(Map<String, AnimationStore> additions) {
        Objects.requireNonNull(additions, "additions");
        var next = new LinkedHashMap<>(stores);
        for (var entry : additions.entrySet()) {
            var domain = Objects.requireNonNull(entry.getKey(), "domain");
            var store = Objects.requireNonNull(entry.getValue(), "store");
            var previous = next.putIfAbsent(domain, store);
            if (previous != null && previous != store
                    && !previous.keySet().equals(store.keySet())) {
                throw new IllegalStateException(
                        "Conflicting default animation domain: " + domain);
            }
        }
        stores = Map.copyOf(next);
    }

    public AnimationStore fallback(String domain) {
        return stores.get(domain);
    }

    public void requireCurrent(RenderTarget target,
                               String animationSet,
                               Animation animation) throws IOException {
        validator.requireCurrent(target, animationSet, animation);
    }

    public synchronized void clear() {
        stores = Map.of();
    }

    @FunctionalInterface
    private interface CurrentAnimationValidator {
        void requireCurrent(RenderTarget target,
                            String animationSet,
                            Animation animation) throws IOException;
    }
}
