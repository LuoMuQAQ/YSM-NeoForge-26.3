package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.client.demand.ContinuousDemand;
import com.elfmcys.ysm.model.domain.Hash256;
import org.jetbrains.annotations.Nullable;

final class CatalogDemandTracker {
    private final ContinuousDemand<Long> page = new ContinuousDemand<>();
    private final ContinuousDemand<Hash256> hover = new ContinuousDemand<>();
    private long pageSequence;

    CatalogDemandTracker(long nowMillis) {
        page.observe(pageSequence, nowMillis);
    }

    void switchPage(long nowMillis) {
        page.observe(++pageSequence, nowMillis);
    }

    boolean maySubmitPage(boolean hasRemoteMiss, long nowMillis) {
        return page.effectEligible(hasRemoteMiss, nowMillis,
                ContinuousDemand.SWITCH_DWELL_MILLIS);
    }

    void observeHover(@Nullable Hash256 modelId, long nowMillis) {
        if (modelId == null) {
            hover.end(nowMillis);
        } else {
            hover.observe(modelId, nowMillis);
        }
    }

    boolean mayBake(Hash256 modelId, long nowMillis) {
        return hover.isCurrent(modelId)
                && hover.elapsedStrictlyExceeds(nowMillis,
                ContinuousDemand.HOVER_DWELL_MILLIS);
    }

    long hoverGeneration() {
        return hover.generation();
    }

    void close() {
        page.reset();
        hover.reset();
    }
}
