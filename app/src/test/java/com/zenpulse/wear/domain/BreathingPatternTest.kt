package com.zenpulse.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingPatternTest {

    @Test
    fun `cycle length is the sum of its phases`() {
        assertEquals(16_000L, BreathingPattern.BOX.cycleMs)
        assertEquals(11_000L, BreathingPattern.COHERENT.cycleMs)
        assertEquals(10_000L, BreathingPattern.EXTENDED_EXHALE.cycleMs)
    }

    @Test
    fun `a pattern without holds never reports a hold phase`() {
        val pattern = BreathingPattern.COHERENT

        // Walk a whole cycle in 100ms steps.
        for (t in 0 until pattern.cycleMs step 100) {
            val phase = pattern.stateAt(t).phase
            assertTrue(
                "coherent breathing has no holds, but reported $phase at ${t}ms",
                phase == BreathPhase.INHALE || phase == BreathPhase.EXHALE,
            )
        }
    }

    @Test
    fun `box breathing walks all four phases in order`() {
        val pattern = BreathingPattern.BOX

        assertEquals(BreathPhase.INHALE, pattern.stateAt(0).phase)
        assertEquals(BreathPhase.HOLD_IN, pattern.stateAt(4_500).phase)
        assertEquals(BreathPhase.EXHALE, pattern.stateAt(8_500).phase)
        assertEquals(BreathPhase.HOLD_OUT, pattern.stateAt(12_500).phase)
    }

    @Test
    fun `phase boundaries land on the next phase`() {
        val pattern = BreathingPattern.EXTENDED_EXHALE

        assertEquals(BreathPhase.INHALE, pattern.stateAt(3_999).phase)
        assertEquals(BreathPhase.EXHALE, pattern.stateAt(4_000).phase)
    }

    @Test
    fun `expansion rises through the inhale and falls through the exhale`() {
        val pattern = BreathingPattern.EXTENDED_EXHALE

        assertEquals(0f, pattern.stateAt(0).expansion, 1e-6f)
        assertEquals(0.5f, pattern.stateAt(2_000).expansion, 1e-6f)
        // Start of the exhale: fully expanded, about to come down.
        assertEquals(1f, pattern.stateAt(4_000).expansion, 1e-6f)
        assertEquals(0.5f, pattern.stateAt(7_000).expansion, 1e-6f)
    }

    @Test
    fun `expansion stays full through an inhale hold`() {
        val pattern = BreathingPattern.BOX

        assertEquals(1f, pattern.stateAt(4_000).expansion, 1e-6f)
        assertEquals(1f, pattern.stateAt(6_000).expansion, 1e-6f)
        assertEquals(1f, pattern.stateAt(7_999).expansion, 1e-6f)
    }

    @Test
    fun `cycles repeat and are counted`() {
        val pattern = BreathingPattern.BOX

        assertEquals(0, pattern.stateAt(0).cycleIndex)
        assertEquals(0, pattern.stateAt(15_999).cycleIndex)
        assertEquals(1, pattern.stateAt(16_000).cycleIndex)
        assertEquals(3, pattern.stateAt(16_000 * 3 + 10).cycleIndex)

        // The same point in the second cycle looks identical to the first.
        assertEquals(pattern.stateAt(1_000).phase, pattern.stateAt(17_000).phase)
        assertEquals(pattern.stateAt(1_000).expansion, pattern.stateAt(17_000).expansion, 1e-6f)
    }

    @Test
    fun `negative elapsed time is clamped rather than crashing`() {
        val state = BreathingPattern.BOX.stateAt(-500)

        assertEquals(BreathPhase.INHALE, state.phase)
        assertEquals(0, state.cycleIndex)
    }

    @Test
    fun `remaining time counts down within a phase`() {
        val pattern = BreathingPattern.EXTENDED_EXHALE

        assertEquals(4_000L, pattern.stateAt(0).phaseRemainingMs)
        assertEquals(1_000L, pattern.stateAt(3_000).phaseRemainingMs)
    }

    @Test
    fun `cyclesIn counts whole cycles only`() {
        assertEquals(3, BreathingPattern.BOX.cyclesIn(60_000))
        assertEquals(0, BreathingPattern.BOX.cyclesIn(15_000))
    }

    @Test
    fun `the most activated state is offered a hold-free pattern`() {
        // Breath-holding can worsen air hunger during panic, so HIGH must not get a hold pattern.
        val forHigh = BreathingPattern.forLevel(StressLevel.HIGH)

        assertEquals(0L, forHigh.holdInMs)
        assertEquals(0L, forHigh.holdOutMs)
    }

    @Test
    fun `byId round-trips known patterns and falls back safely`() {
        BreathingPattern.ALL.forEach { pattern ->
            assertEquals(pattern, BreathingPattern.byId(pattern.id))
        }
        assertEquals(BreathingPattern.COHERENT, BreathingPattern.byId("nonsense"))
    }
}
