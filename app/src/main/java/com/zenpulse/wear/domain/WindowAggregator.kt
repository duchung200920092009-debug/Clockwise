package com.zenpulse.wear.domain

import kotlin.math.sqrt

/**
 * Collects raw sensor samples and emits one [SensorWindow] per [windowDurationMs].
 *
 * This is the boundary between "noisy hardware reality" and "something worth reasoning about".
 * The accelerometer alone delivers ~50 samples a second; feeding that straight into detection
 * logic would be both wasteful and unstable. Averaging over a window also means a single dropped
 * or absurd reading can't move a decision on its own.
 *
 * Not thread-safe by design — it is driven from a single coroutine in the data layer. Keeping it
 * lock-free keeps it pure Kotlin and unit-testable.
 */
class WindowAggregator(
    private val windowDurationMs: Long = SensorWindow.WINDOW_DURATION_MS,
) {

    private var windowStartMs: Long = -1L

    private var hrSum = 0.0
    private var hrCount = 0

    private var hrvSum = 0.0
    private var hrvCount = 0

    // Running moments for the accelerometer, so motion SD needs no sample buffer.
    private var motionSum = 0.0
    private var motionSumSq = 0.0
    private var motionCount = 0

    fun addHeartRate(bpm: Double) {
        hrSum += bpm
        hrCount++
    }

    fun addHrv(hrvMs: Double) {
        hrvSum += hrvMs
        hrvCount++
    }

    /** Feed the accelerometer *magnitude* (not the raw axes). */
    fun addMotion(magnitude: Double) {
        motionSum += magnitude
        motionSumSq += magnitude * magnitude
        motionCount++
    }

    /**
     * Call on every tick with the current time. Returns a window once [windowDurationMs] has
     * passed since the window opened, otherwise null.
     *
     * The first call only opens the window — it does not emit — so a window is never built from a
     * fraction of its intended duration.
     */
    fun tick(nowMs: Long): SensorWindow? {
        if (windowStartMs < 0L) {
            windowStartMs = nowMs
            return null
        }
        if (nowMs - windowStartMs < windowDurationMs) return null

        val window = SensorWindow(
            timestampMs = nowMs,
            bpm = if (hrCount > 0) hrSum / hrCount else null,
            hrvProxyMs = if (hrvCount > 0) hrvSum / hrvCount else null,
            motionIntensity = motionStandardDeviation(),
        )
        resetAccumulators(nowMs)
        return window
    }

    /** Drop everything, including the open window — used when measurement stops. */
    fun reset() {
        windowStartMs = -1L
        resetAccumulators(-1L)
        windowStartMs = -1L
    }

    /**
     * Standard deviation of accelerometer magnitude. Mean magnitude is useless here because
     * gravity pins it near 9.8 m/s² regardless of activity; the spread is what moves when the
     * user does.
     */
    private fun motionStandardDeviation(): Double? {
        if (motionCount < 2) return null
        val mean = motionSum / motionCount
        val variance = (motionSumSq / motionCount) - (mean * mean)
        // Catastrophic cancellation can push a mathematically non-negative variance just below 0.
        return sqrt(variance.coerceAtLeast(0.0))
    }

    private fun resetAccumulators(nowMs: Long) {
        windowStartMs = nowMs
        hrSum = 0.0
        hrCount = 0
        hrvSum = 0.0
        hrvCount = 0
        motionSum = 0.0
        motionSumSq = 0.0
        motionCount = 0
    }
}
