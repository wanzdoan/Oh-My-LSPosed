package com.betterzl.module.hook

import org.junit.Assert.*
import org.junit.Test

class BackdropStabilityTest {
    @Test fun firstTabLoadingKeepsRefractionSoft() {
        val state = BackdropStability()
        for (now in 1000L..16000L step 16) {
            state.advance(now, true)
            assertEquals(0f, state.mix, 0f)
        }
    }
    @Test fun waitsForQuietFramesThenFadesBackToGlass() {
        val state = BackdropStability()
        state.advance(1000, true)
        for (now in 1016L..1176L step 16) state.advance(now, false)
        assertEquals(0f, state.mix, 0f)
        state.advance(1192, false)
        assertTrue(state.mix > 0f && state.mix < 1f)
        for (now in 1208L..1500L step 16) state.advance(now, false)
        assertEquals(1f, state.mix, 0f)
    }
    @Test fun aSecondLayoutOrTabChangeRestartsTheGuard() {
        val state = BackdropStability()
        for (now in 1000L..2000L step 16) state.advance(now, false)
        assertEquals(1f, state.mix, 0f)
        state.invalidate(2010)
        assertEquals(1f, state.mix, 0f) // No sudden optical change during a native relayout.
        state.advance(2100, false)
        assertTrue(state.mix > 0f && state.mix < 1f)
    }
}
