package com.zenpulse.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowAggregatorTest {

    @Test
    fun `the first tick opens the window without emitting`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        assertNull(aggregator.tick(1_000))
    }

    @Test
    fun `no window is emitted before the duration elapses`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        aggregator.addHeartRate(70.0)

        assertNull(aggregator.tick(9_999))
        assertNotNull(aggregator.tick(10_000))
    }

    @Test
    fun `heart rate and HRV are averaged over the window`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        aggregator.addHeartRate(60.0)
        aggregator.addHeartRate(80.0)
        aggregator.addHrv(40.0)
        aggregator.addHrv(60.0)

        val window = aggregator.tick(10_000)!!
        assertEquals(70.0, window.bpm!!, 1e-9)
        assertEquals(50.0, window.hrvProxyMs!!, 1e-9)
        assertEquals(10_000L, window.timestampMs)
    }

    @Test
    fun `motion is reported as spread, not mean magnitude`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        // Both samples sit near gravity; what matters is that they differ by ±1.
        aggregator.addMotion(9.0)
        aggregator.addMotion(11.0)

        val window = aggregator.tick(10_000)!!
        assertEquals(1.0, window.motionIntensity!!, 1e-9)
    }

    @Test
    fun `a perfectly still wrist reports near-zero motion despite gravity`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        repeat(50) { aggregator.addMotion(9.81) }

        val window = aggregator.tick(10_000)!!
        // A mean-based measure would report ~9.81 here and look like constant movement.
        assertTrue(
            "still wrist should read near zero, got ${window.motionIntensity}",
            window.motionIntensity!! < 0.001,
        )
    }

    @Test
    fun `missing sensors produce nulls rather than zeros`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)

        val window = aggregator.tick(10_000)!!
        // Zeros here would read as "heart rate of 0" and "perfectly still" downstream.
        assertNull(window.bpm)
        assertNull(window.hrvProxyMs)
        assertNull(window.motionIntensity)
    }

    @Test
    fun `a single motion sample is not enough to claim a spread`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        aggregator.addMotion(9.81)

        assertNull(aggregator.tick(10_000)!!.motionIntensity)
    }

    @Test
    fun `accumulators do not leak into the next window`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        aggregator.addHeartRate(120.0)
        aggregator.tick(10_000)

        aggregator.addHeartRate(60.0)
        val second = aggregator.tick(20_000)!!
        assertEquals(60.0, second.bpm!!, 1e-9)
    }

    @Test
    fun `reset discards the open window`() {
        val aggregator = WindowAggregator(windowDurationMs = 10_000)
        aggregator.tick(0)
        aggregator.addHeartRate(70.0)
        aggregator.reset()

        // After a reset the next tick re-opens rather than emitting a stale partial window.
        assertNull(aggregator.tick(20_000))
        assertNotNull(aggregator.tick(30_000))
    }
}
