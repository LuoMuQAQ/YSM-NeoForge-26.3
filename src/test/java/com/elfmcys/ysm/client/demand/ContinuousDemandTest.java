package com.elfmcys.ysm.client.demand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousDemandTest {
    @Test
    void hoverRequiresStrictlyMoreThanThreeHundredMilliseconds() {
        var demand = new ContinuousDemand<String>();
        demand.observe("model", 1_000);

        assertFalse(demand.elapsedStrictlyExceeds(1_300,
                ContinuousDemand.HOVER_DWELL_MILLIS));
        assertTrue(demand.elapsedStrictlyExceeds(1_301,
                ContinuousDemand.HOVER_DWELL_MILLIS));
    }

    @Test
    void shortPreviousIntentRequiresStrictlyMoreThanSevenHundredMilliseconds() {
        var demand = new ContinuousDemand<String>();
        demand.observe("A", 0);
        demand.observe("B", 700);

        assertTrue(demand.requiresDwell(true, ContinuousDemand.SWITCH_DWELL_MILLIS));
        assertFalse(demand.effectEligible(true, 1_400,
                ContinuousDemand.SWITCH_DWELL_MILLIS));
        assertTrue(demand.effectEligible(true, 1_401,
                ContinuousDemand.SWITCH_DWELL_MILLIS));
    }

    @Test
    void firstLongPreviousAndCompleteCurrentBypassSwitchDwell() {
        var first = new ContinuousDemand<String>();
        first.observe("first", 0);
        assertTrue(first.effectEligible(true, 0,
                ContinuousDemand.SWITCH_DWELL_MILLIS));

        var longPrevious = new ContinuousDemand<String>();
        longPrevious.observe("A", 0);
        longPrevious.observe("B", 701);
        assertTrue(longPrevious.effectEligible(true, 701,
                ContinuousDemand.SWITCH_DWELL_MILLIS));

        var complete = new ContinuousDemand<String>();
        complete.observe("A", 0);
        complete.observe("B", 100);
        assertTrue(complete.effectEligible(false, 100,
                ContinuousDemand.SWITCH_DWELL_MILLIS));
    }

    @Test
    void repeatedObservationDoesNotResetTheRealStart() {
        var demand = new ContinuousDemand<String>();
        demand.observe("model", 0);
        assertFalse(demand.observe("model", 250));
        assertTrue(demand.elapsedStrictlyExceeds(301,
                ContinuousDemand.HOVER_DWELL_MILLIS));
    }
}
