package com.betterzl.module.hook

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GlassMotionTest {
    @Test fun loadingFramesAndShrinkingTabNeverOverflowBar() {
        val motion = GlassMotion()
        motion.advance(1000, 0f, 314f, 0f, 1098f, false)
        var now = 1000L
        for (gap in listOf(16L, 16L, 160L, 300L, 16L, 48L, 500L, 16L)) {
            now += gap
            motion.advance(now, 902f, 196f, 0f, 1098f, true)
            assertTrue(motion.x.isFinite())
            assertTrue(motion.x >= 0f)
            assertTrue(motion.x + motion.width <= 1098.001f)
            assertTrue(motion.press in 0f..1f)
        }
    }

    @Test fun motionSettlesToExactNativeBoundsAfterDroppedFrames() {
        val motion = GlassMotion()
        motion.advance(1000, 0f, 314f, 0f, 1098f, false)
        var moving = true
        repeat(120) { i -> moving = motion.advance(1016 + i * 32L, 902f, 196f, 0f, 1098f, false) }
        assertFalse(moving)
        assertEquals(902f, motion.x, 0f)
        assertEquals(196f, motion.width, 0f)
        assertEquals(0f, motion.press, 0f)
    }

    @Test fun firstFrameAfterIdleStartsTransitionInsteadOfJumpingToNewTab() {
        val motion = GlassMotion()
        motion.advance(1000, 0f, 314f, 0f, 1098f, false)
        motion.advance(60000, 600f, 196f, 0f, 1098f, false)
        assertTrue(motion.x > 0f)
        assertTrue(motion.x < 100f)
    }

    @Test fun resizeAndFastDirectionReversalsRemainBounded() {
        val motion = GlassMotion()
        repeat(500) { i ->
            val right = if (i % 3 == 0) 600f else 1098f
            motion.advance(1000 + i * 17L, if (i % 2 == 0) 1000f else -200f,
                if (i % 4 == 0) 314f else 196f, 0f, right, i % 7 < 3)
            assertTrue(motion.x >= 0f)
            assertTrue(motion.width >= 1f)
            assertTrue(motion.x + motion.width <= right + 0.001f)
        }
    }

    @Test fun invalidLayoutDoesNotPoisonTheNextValidFrame() {
        val motion = GlassMotion()
        assertFalse(motion.advance(1000, Float.NaN, 0f, 0f, 0f, false))
        motion.advance(1016, 10f, 80f, 0f, 100f, false)
        assertEquals(10f, motion.x, 0f)
        assertEquals(80f, motion.width, 0f)
    }

    @Test fun pressReleasesWithoutOvershootingEvenAfterHostStall() {
        val motion = GlassMotion()
        repeat(20) { motion.advance(1000 + it * 16L, 0f, 196f, 0f, 1098f, true) }
        var previous = motion.press
        repeat(40) { i ->
            motion.advance(2000 + i * 33L, 0f, 196f, 0f, 1098f, false)
            assertTrue(motion.press <= previous + 0.0001f)
            assertTrue(motion.press >= 0f)
            previous = motion.press
        }
        assertTrue(abs(motion.press) < 0.002f)
    }

    @Test fun hidingOrDetachingMidPressCanResetWithoutAStuckEnlargement() {
        val motion = GlassMotion()
        repeat(20) { motion.advance(1000 + it * 16L, 0f, 196f, 0f, 1098f, true) }
        assertTrue(motion.press > 0.9f)
        motion.reset()
        assertEquals(0f, motion.press, 0f)
        motion.advance(3000, 902f, 196f, 0f, 1098f, false)
        assertEquals(0f, motion.press, 0f)
        assertEquals(902f, motion.x, 0f)
    }
}
