package com.zenpulse.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTrackerTest {

    private fun window(bpm: Double?, hrv: Double? = 50.0, motion: Double? = 0.1, t: Long = 0L) =
        SensorWindow(timestampMs = t, bpm = bpm, hrvProxyMs = hrv, motionIntensity = motion)

    @Test
    fun `first sample seeds the mean instead of averaging up from zero`() {
        val tracker = BaselineTracker()
        tracker.offer(window(72.0))

        // A naive EWMA starting at 0.0 would report ~0.7 here and take hundreds of samples to recover.
        assertEquals(72.0, tracker.snapshot.hrMean, 1e-9)
        assertEquals(1, tracker.snapshot.sampleCount)
    }

    @Test
    fun `windows with too much motion are rejected`() {
        val tracker = BaselineTracker()
        val accepted = tracker.offer(window(70.0, motion = BaselineTracker.CALM_MOTION_MAX + 0.5))

        assertFalse(accepted)
        assertEquals(0, tracker.snapshot.sampleCount)
    }

    @Test
    fun `implausible heart rates are rejected`() {
        val tracker = BaselineTracker()

        assertFalse(tracker.offer(window(5.0)))
        assertFalse(tracker.offer(window(400.0)))
        assertFalse(tracker.offer(window(null)))
        assertEquals(0, tracker.snapshot.sampleCount)
    }

    @Test
    fun `a window with heart rate but no HRV still trains the HR baseline`() {
        val tracker = BaselineTracker()
        tracker.offer(window(70.0, hrv = null))

        assertEquals(70.0, tracker.snapshot.hrMean, 1e-9)
        assertEquals(0.0, tracker.snapshot.hrvMean, 1e-9)
        assertEquals(1, tracker.snapshot.sampleCount)
    }

    @Test
    fun `becomes ready only after enough calm windows`() {
        val tracker = BaselineTracker()
        repeat(BaselineSnapshot.MIN_SAMPLES_FOR_READY - 1) { i ->
            tracker.offer(window(70.0, t = i * 10_000L))
        }
        assertFalse(tracker.snapshot.isReady)

        tracker.offer(window(70.0))
        assertTrue(tracker.snapshot.isReady)
        assertEquals(1f, tracker.snapshot.learningProgress, 1e-6f)
    }

    @Test
    fun `standard deviation floors prevent hair-trigger z-scores`() {
        val tracker = BaselineTracker()
        // A perfectly constant heart rate has zero variance...
        repeat(BaselineSnapshot.MIN_SAMPLES_FOR_READY) { tracker.offer(window(70.0)) }

        // ...but the floor keeps the SD usable, so a 1 BPM rise isn't an infinite z-score.
        assertEquals(BaselineSnapshot.HR_SD_FLOOR, tracker.snapshot.hrSd, 1e-9)
        assertEquals(BaselineSnapshot.HRV_SD_FLOOR, tracker.snapshot.hrvSd, 1e-9)
    }

    @Test
    fun `baseline adapts slowly towards a sustained new normal`() {
        val tracker = BaselineTracker()
        repeat(100) { tracker.offer(window(60.0)) }
        val before = tracker.snapshot.hrMean

        // Ten windows at a much higher rate should nudge, not redefine, the baseline.
        repeat(10) { tracker.offer(window(100.0)) }
        val after = tracker.snapshot.hrMean

        assertTrue("baseline should move up", after > before)
        assertTrue("baseline should not chase recent samples, was $after", after < 65.0)
    }

    @Test
    fun `reset clears the learned baseline`() {
        val tracker = BaselineTracker()
        repeat(BaselineSnapshot.MIN_SAMPLES_FOR_READY) { tracker.offer(window(70.0)) }
        assertTrue(tracker.snapshot.isReady)

        tracker.reset()
        assertFalse(tracker.snapshot.isReady)
        assertEquals(0, tracker.snapshot.sampleCount)
    }

    @Test
    fun `restore resumes learning from a persisted snapshot`() {
        val original = BaselineTracker()
        repeat(BaselineSnapshot.MIN_SAMPLES_FOR_READY) { original.offer(window(70.0)) }

        // Simulates a reboot: a fresh tracker rehydrated from what was written to disk.
        val rehydrated = BaselineTracker()
        rehydrated.restore(original.snapshot)

        assertTrue(rehydrated.snapshot.isReady)
        assertEquals(70.0, rehydrated.snapshot.hrMean, 1e-9)

        // And it keeps counting from there rather than starting over.
        rehydrated.offer(window(70.0))
        assertEquals(
            BaselineSnapshot.MIN_SAMPLES_FOR_READY + 1,
            rehydrated.snapshot.sampleCount,
        )
    }

    @Test
    fun `variance grows when calm readings genuinely vary`() {
        val tracker = BaselineTracker()
        repeat(200) { i -> tracker.offer(window(if (i % 2 == 0) 65.0 else 80.0)) }

        assertTrue(
            "alternating readings should produce real spread, got ${tracker.snapshot.hrSd}",
            tracker.snapshot.hrSd > BaselineSnapshot.HR_SD_FLOOR,
        )
    }
}
