package com.zenpulse.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StressDetectorTest {

    /** A ready baseline of 70 BPM / 50 ms HRV, both with floor-level spread. */
    private fun readyBaseline(): BaselineSnapshot {
        val tracker = BaselineTracker()
        repeat(BaselineSnapshot.MIN_SAMPLES_FOR_READY) { i ->
            tracker.offer(
                SensorWindow(
                    timestampMs = i * 10_000L,
                    bpm = 70.0,
                    hrvProxyMs = 50.0,
                    motionIntensity = 0.1,
                )
            )
        }
        return tracker.snapshot
    }

    private fun window(bpm: Double?, hrv: Double?, motion: Double?) =
        SensorWindow(timestampMs = 0L, bpm = bpm, hrvProxyMs = hrv, motionIntensity = motion)

    @Test
    fun `movement is never assessed as stress`() {
        val detector = StressDetector()
        // Heart rate far above baseline, but the user is clearly moving.
        val result = detector.update(window(120.0, 20.0, 3.0), readyBaseline())

        assertTrue(result is StressAssessment.NotAssessable)
        assertEquals(
            NotAssessableReason.MOVING,
            (result as StressAssessment.NotAssessable).reason,
        )
    }

    @Test
    fun `missing heart rate reports no signal rather than calm`() {
        val detector = StressDetector()
        val result = detector.update(window(null, null, 0.1), readyBaseline())

        assertEquals(
            NotAssessableReason.NO_SIGNAL,
            (result as StressAssessment.NotAssessable).reason,
        )
    }

    @Test
    fun `an unready baseline reports learning with progress`() {
        val detector = StressDetector()
        val tracker = BaselineTracker()
        repeat(10) { tracker.offer(window(70.0, 50.0, 0.1).copy(timestampMs = it * 10_000L)) }

        val result = detector.update(window(90.0, 30.0, 0.1), tracker.snapshot)

        assertTrue(result is StressAssessment.Learning)
        val progress = (result as StressAssessment.Learning).progress
        assertTrue("progress should be partial, was $progress", progress > 0f && progress < 1f)
    }

    @Test
    fun `escalation requires sustained signal`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        val stressed = window(85.0, 30.0, 0.1)

        // Two windows of strong signal are not enough to escalate off CALM.
        repeat(StressDetector.WINDOWS_TO_ESCALATE - 1) {
            val step = detector.update(stressed, baseline) as StressAssessment.Assessed
            assertEquals(StressLevel.CALM, step.level)
        }

        val escalated = detector.update(stressed, baseline) as StressAssessment.Assessed
        assertEquals(StressLevel.HIGH, escalated.level)
    }

    @Test
    fun `a single noisy window cannot escalate`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        val calm = window(70.0, 50.0, 0.1)
        val spike = window(110.0, 10.0, 0.1)

        detector.update(calm, baseline)
        val afterSpike = detector.update(spike, baseline) as StressAssessment.Assessed
        assertEquals(StressLevel.CALM, afterSpike.level)

        val afterRecovery = detector.update(calm, baseline) as StressAssessment.Assessed
        assertEquals(StressLevel.CALM, afterRecovery.level)
    }

    @Test
    fun `movement clears pending escalation`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        val stressed = window(85.0, 30.0, 0.1)

        // Build up to one window short of escalating...
        repeat(StressDetector.WINDOWS_TO_ESCALATE - 1) { detector.update(stressed, baseline) }
        // ...then move, which must discard that progress rather than carry it over.
        detector.update(window(85.0, 30.0, 3.0), baseline)

        val next = detector.update(stressed, baseline) as StressAssessment.Assessed
        assertEquals(StressLevel.CALM, next.level)
    }

    @Test
    fun `heart rate alone never reaches HIGH`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        // Absurdly elevated heart rate, but no HRV available at all.
        val hrOnly = window(160.0, null, 0.1)

        var last: StressAssessment.Assessed? = null
        repeat(10) { last = detector.update(hrOnly, baseline) as StressAssessment.Assessed }

        assertNotNull(last)
        assertFalse("HR alone must not be enough for HIGH", last!!.level == StressLevel.HIGH)
        assertEquals(StressLevel.ELEVATED, last!!.level)
        assertFalse(last!!.hrvContributed)
        assertTrue(last!!.score <= StressDetector.MAX_SCORE_WITHOUT_HRV)
    }

    @Test
    fun `suppressed HRV with elevated HR reaches HIGH`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        val stressed = window(85.0, 30.0, 0.1)

        var last: StressAssessment.Assessed? = null
        repeat(StressDetector.WINDOWS_TO_ESCALATE) {
            last = detector.update(stressed, baseline) as StressAssessment.Assessed
        }

        assertEquals(StressLevel.HIGH, last!!.level)
        assertTrue(last!!.hrvContributed)
        assertTrue("hrZ should be positive when HR is above baseline", last!!.hrZ > 0)
        assertTrue("hrvZ should be positive when HRV is below baseline", last!!.hrvZ!! > 0)
    }

    @Test
    fun `calmer than baseline scores zero and stays CALM`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        // Below baseline HR and above baseline HRV: the opposite of stress.
        val relaxed = window(62.0, 70.0, 0.05)

        val result = detector.update(relaxed, baseline) as StressAssessment.Assessed
        assertEquals(StressLevel.CALM, result.level)
        assertEquals(0.0, result.score, 1e-9)
        assertTrue(result.hrZ < 0)
    }

    @Test
    fun `de-escalation is faster than escalation`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        val stressed = window(85.0, 30.0, 0.1)
        val calm = window(70.0, 50.0, 0.1)

        repeat(StressDetector.WINDOWS_TO_ESCALATE) { detector.update(stressed, baseline) }
        assertEquals(StressLevel.HIGH, detector.level)

        repeat(StressDetector.WINDOWS_TO_DE_ESCALATE) { detector.update(calm, baseline) }
        assertEquals(StressLevel.CALM, detector.level)
        assertTrue(StressDetector.WINDOWS_TO_DE_ESCALATE < StressDetector.WINDOWS_TO_ESCALATE)
    }

    @Test
    fun `high sensitivity escalates on weaker evidence than low sensitivity`() {
        val baseline = readyBaseline()
        // Chosen to land between the LOW and HIGH sensitivity thresholds.
        val mild = window(75.0, 45.0, 0.1)

        val sensitive = StressDetector(StressSensitivity.HIGH)
        val stoic = StressDetector(StressSensitivity.LOW)

        var sensitiveLevel = StressLevel.CALM
        var stoicLevel = StressLevel.CALM
        repeat(5) {
            sensitiveLevel = (sensitive.update(mild, baseline) as StressAssessment.Assessed).level
            stoicLevel = (stoic.update(mild, baseline) as StressAssessment.Assessed).level
        }

        assertTrue(
            "high sensitivity ($sensitiveLevel) should not be below low sensitivity ($stoicLevel)",
            sensitiveLevel.ordinal >= stoicLevel.ordinal,
        )
    }

    @Test
    fun `reset returns the detector to calm`() {
        val detector = StressDetector()
        val baseline = readyBaseline()
        repeat(StressDetector.WINDOWS_TO_ESCALATE) {
            detector.update(window(85.0, 30.0, 0.1), baseline)
        }
        assertEquals(StressLevel.HIGH, detector.level)

        detector.reset()
        assertEquals(StressLevel.CALM, detector.level)
    }
}
