package com.zenpulse.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HrvEstimatorTest {

    @Test
    fun `needs at least three samples before reporting`() {
        val estimator = HrvEstimator()

        assertNull(estimator.addSample(60.0))
        assertNull(estimator.addSample(60.0))
        assertNotNull(estimator.addSample(60.0))
    }

    @Test
    fun `a perfectly steady heart rate has zero variability`() {
        val estimator = HrvEstimator()
        repeat(5) { estimator.addSample(60.0) }

        assertEquals(0.0, estimator.addSample(60.0)!!, 1e-9)
    }

    @Test
    fun `a varying heart rate produces variability`() {
        val estimator = HrvEstimator()
        var last: Double? = null
        repeat(10) { i -> last = estimator.addSample(if (i % 2 == 0) 60.0 else 66.0) }

        assertTrue("alternating rates should show variability, got $last", last!! > 0.0)
    }

    @Test
    fun `more variation yields a larger value`() {
        fun rmssdFor(delta: Double): Double {
            val estimator = HrvEstimator()
            var last: Double? = null
            // The first two samples legitimately return null — only the final value is asserted on.
            repeat(10) { i -> last = estimator.addSample(60.0 + if (i % 2 == 0) 0.0 else delta) }
            return last!!
        }

        assertTrue(rmssdFor(10.0) > rmssdFor(2.0))
    }

    @Test
    fun `non-positive heart rates are ignored`() {
        val estimator = HrvEstimator()
        repeat(4) { estimator.addSample(60.0) }

        // A zero would otherwise become an infinite synthetic interval and poison the window.
        val result = estimator.addSample(0.0)
        assertEquals(0.0, result!!, 1e-9)
    }

    @Test
    fun `the window slides so old values stop counting`() {
        val estimator = HrvEstimator(windowSize = 4)
        // Big swings first...
        estimator.addSample(50.0)
        estimator.addSample(100.0)
        estimator.addSample(50.0)
        // ...then a steady run long enough to push them all out.
        repeat(4) { estimator.addSample(60.0) }

        assertEquals(0.0, estimator.addSample(60.0)!!, 1e-9)
    }

    @Test
    fun `reset clears the window`() {
        val estimator = HrvEstimator()
        repeat(5) { i -> estimator.addSample(60.0 + i) }
        estimator.reset()

        assertNull(estimator.addSample(60.0))
    }

    @Test
    fun `rejects a window too small to hold a difference`() {
        val error = runCatching { HrvEstimator(windowSize = 2) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
