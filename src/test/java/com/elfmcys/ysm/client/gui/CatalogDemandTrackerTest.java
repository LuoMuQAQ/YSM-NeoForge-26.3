package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.model.domain.Hash256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogDemandTrackerTest {
    @Test
    void pageAndHoverClocksAdvanceIndependently() {
        var model = hash(1);
        var demand = new CatalogDemandTracker(0);
        demand.observeHover(model, 0);
        demand.switchPage(100);

        assertFalse(demand.mayBake(model, 300));
        assertTrue(demand.mayBake(model, 301));
        assertFalse(demand.maySubmitPage(true, 800));
        assertTrue(demand.maySubmitPage(true, 801));
    }

    @Test
    void repeatedCatalogRebuildObservationPreservesHoverStart() {
        var model = hash(2);
        var demand = new CatalogDemandTracker(0);
        demand.observeHover(model, 0);
        demand.observeHover(model, 299);

        assertTrue(demand.mayBake(model, 301));
        demand.observeHover(null, 302);
        demand.observeHover(model, 303);
        assertFalse(demand.mayBake(model, 603));
        assertTrue(demand.mayBake(model, 604));
    }

    @Test
    void firstPageAndCacheHitsBypassThePageSwitchGate() {
        var demand = new CatalogDemandTracker(0);
        assertTrue(demand.maySubmitPage(true, 0));

        demand.switchPage(100);
        assertTrue(demand.maySubmitPage(false, 100));
    }

    private static Hash256 hash(int marker) {
        var bytes = new byte[Hash256.SIZE];
        bytes[0] = (byte) marker;
        return new Hash256(bytes);
    }
}
