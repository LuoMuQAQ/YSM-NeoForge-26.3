package com.elfmcys.ysm.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationRouletteScreenTest {
    @Test
    void parsesSupportedConfigResults() {
        assertEquals(0f, AnimationRouletteScreen.transformNumber("null"));
        assertEquals(1f, AnimationRouletteScreen.transformNumber("true"));
        assertEquals(0f, AnimationRouletteScreen.transformNumber("false"));
        assertEquals(1.5f, AnimationRouletteScreen.transformNumber("1.5"));
    }

    @Test
    void rejectsFailedAndNonFiniteConfigResults() {
        assertNull(AnimationRouletteScreen.transformNumber("Error: failed"));
        assertNull(AnimationRouletteScreen.transformNumber("not-a-number"));
        assertNull(AnimationRouletteScreen.transformNumber("1e999"));
    }

    @Test
    void acceptsOnlyExistingRadioIndices() {
        assertEquals(0, AnimationRouletteScreen.radioIndex("0", 3));
        assertEquals(2, AnimationRouletteScreen.radioIndex("2", 3));
        assertEquals(-1, AnimationRouletteScreen.radioIndex("-1", 3));
        assertEquals(-1, AnimationRouletteScreen.radioIndex("3", 3));
        assertEquals(-1, AnimationRouletteScreen.radioIndex("Error: failed", 3));
    }
}
