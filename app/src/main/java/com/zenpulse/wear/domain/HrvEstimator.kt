package com.zenpulse.wear.domain

import kotlin.math.sqrt

/**
 * Rolling RMSSD-style HRV proxy computed from consecutive heart-rate samples.
 *
 * **Why a proxy and not real HRV:** Health Services does not expose true beat-to-beat
 * inter-beat intervals (IBI) on most Wear OS hardware, including current Galaxy Watch
 * generations through its public API — only an already-smoothed instantaneous BPM. This
 * estimator converts each BPM sample to a synthetic IBI (`60_000 / bpm` ms) and tracks the
 * root-mean-square of successive differences over a sliding window — the same shape of
 * computation as clinical RMSSD, but fed lower-resolution input.
 *
 * This will under-read true beat-to-beat variability, because it inherits whatever smoothing
 * the sensor firmware already applied to BPM before we ever see it. Treat the output as a
 * relative, personal-baseline signal ("more/less variable than my own recent average") for the
 * Week 4 rule-based detector — never as a clinical HRV value, and never compare it across
 * people or devices.
 */
class HrvEstimator(private val windowSize: Int = 20) {

    init {
        require(windowSize >= 3) { "windowSize must be >= 3 to compute a variance-like measure" }
    }

    private val ibiWindowMs = ArrayDeque<Double>()

    /** Feed one BPM sample. Returns the current RMSSD-proxy in ms, or null until enough data. */
    fun addSample(bpm: Double): Double? {
        if (bpm > 0.0) {
            val ibiMs = 60_000.0 / bpm
            ibiWindowMs.addLast(ibiMs)
            if (ibiWindowMs.size > windowSize) ibiWindowMs.removeFirst()
        }
        return currentRmssd()
    }

    /** Clear the window — call at the start of a new measuring session. */
    fun reset() = ibiWindowMs.clear()

    private fun currentRmssd(): Double? {
        if (ibiWindowMs.size < 3) return null
        val squaredDiffs = ibiWindowMs.zipWithNext { a, b -> (b - a) * (b - a) }
        return sqrt(squaredDiffs.average())
    }
}
