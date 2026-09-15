package com.zenpulse.wear.domain

import kotlin.math.max
import kotlin.math.sqrt

/**
 * A person's learned calm-state baseline: what their heart rate and HRV proxy look like when
 * nothing is wrong.
 *
 * Stress can only be detected *relative to the individual*. A resting heart rate of 85 is alarming
 * for one person and completely normal for another, so an absolute threshold ("alert above 100
 * BPM") would fire constantly for some users and never for others. This tracks both the mean and
 * the spread of each signal during calm periods, which lets [StressDetector] ask the only question
 * that generalises across people: *how unusual is this, for you?*
 */
data class BaselineSnapshot(
    val hrMean: Double = 0.0,
    val hrVariance: Double = 0.0,
    val hrvMean: Double = 0.0,
    val hrvVariance: Double = 0.0,
    /** Number of calm windows folded in so far. Drives [isReady]. */
    val sampleCount: Int = 0,
    val updatedAtMs: Long = 0L,
) {
    /**
     * Standard deviation floors matter more than they look. Someone whose calm heart rate barely
     * moves would otherwise get a huge z-score from a 3 BPM rise — a guaranteed false alarm
     * factory. The floor says: below this much natural variation, we refuse to treat small
     * absolute changes as meaningful.
     */
    val hrSd: Double get() = max(sqrt(hrVariance), HR_SD_FLOOR)
    val hrvSd: Double get() = max(sqrt(hrvVariance), HRV_SD_FLOOR)

    /** True once enough calm windows have been seen to trust the numbers. */
    val isReady: Boolean get() = sampleCount >= MIN_SAMPLES_FOR_READY

    /** 0..1 progress towards [isReady], for the onboarding/"still learning" UI. */
    val learningProgress: Float
        get() = (sampleCount.toFloat() / MIN_SAMPLES_FOR_READY).coerceIn(0f, 1f)

    companion object {
        const val HR_SD_FLOOR = 2.0
        const val HRV_SD_FLOOR = 3.0

        /**
         * ~60 calm windows ≈ 10 minutes of accumulated calm time (windows are 10s). Calm time
         * accumulates across sessions and days because the snapshot is persisted, so this is
         * reached on the first day of normal wear without making day-one useless.
         */
        const val MIN_SAMPLES_FOR_READY = 60
    }
}

/**
 * Folds calm sensor windows into a [BaselineSnapshot] using exponentially weighted mean and
 * variance, so the baseline tracks slow real changes (fitness improving, medication starting,
 * seasons) while still being dominated by the person's recent normal.
 *
 * Deliberately pure Kotlin with no Android dependencies: this is the logic that decides whether
 * someone gets alerted, so it must be unit-testable on a plain JVM.
 */
class BaselineTracker(
    initial: BaselineSnapshot = BaselineSnapshot(),
    /**
     * Smoothing factor. 0.01 gives a time constant of ~100 calm windows (~17 minutes of
     * accumulated calm), slow enough that a single bad afternoon doesn't redefine "normal".
     */
    private val alpha: Double = DEFAULT_ALPHA,
) {

    var snapshot: BaselineSnapshot = initial
        private set

    /**
     * Offer a window to the baseline. Returns true if it was folded in.
     *
     * Rejected windows are the whole point of this method: a baseline learned from windows where
     * the user was walking, or where the PPG was returning garbage, is not a calm baseline, and
     * every downstream decision inherits that error.
     */
    fun offer(window: SensorWindow): Boolean {
        val bpm = window.bpm ?: return false
        if (bpm !in PLAUSIBLE_HR_RANGE) return false

        // Stricter than the detector's own motion gate: to *learn* what calm looks like we want
        // genuinely still moments, not merely "not exercising".
        val motion = window.motionIntensity
        if (motion != null && motion > CALM_MOTION_MAX) return false

        val previous = snapshot
        val (hrMean, hrVariance) = updateEw(previous.hrMean, previous.hrVariance, bpm, previous.sampleCount)

        // HRV is optional: it drops out more often than HR, and a window with good HR and no HRV
        // is still useful for the HR half of the baseline.
        val hrv = window.hrvProxyMs?.takeIf { it in PLAUSIBLE_HRV_RANGE }
        val (hrvMean, hrvVariance) = if (hrv != null) {
            updateEw(previous.hrvMean, previous.hrvVariance, hrv, previous.sampleCount)
        } else {
            previous.hrvMean to previous.hrvVariance
        }

        snapshot = previous.copy(
            hrMean = hrMean,
            hrVariance = hrVariance,
            hrvMean = hrvMean,
            hrvVariance = hrvVariance,
            sampleCount = previous.sampleCount + 1,
            updatedAtMs = window.timestampMs,
        )
        return true
    }

    /** Wipe the learned baseline — exposed in settings for "my normal has changed, relearn it". */
    fun reset() {
        snapshot = BaselineSnapshot()
    }

    /**
     * Resume from a persisted snapshot, so learning continues across reboots instead of restarting
     * from nothing every time the watch is rebooted or the app is killed.
     */
    fun restore(saved: BaselineSnapshot) {
        snapshot = saved
    }

    /**
     * Standard exponentially weighted mean/variance recursion. The first sample seeds the mean
     * directly (with zero variance) rather than dragging it up from 0.0, which would otherwise
     * take hundreds of windows to recover from.
     */
    private fun updateEw(
        mean: Double,
        variance: Double,
        value: Double,
        priorCount: Int,
    ): Pair<Double, Double> {
        if (priorCount == 0 || mean == 0.0) return value to 0.0
        val diff = value - mean
        val increment = alpha * diff
        val newMean = mean + increment
        val newVariance = (1 - alpha) * (variance + diff * increment)
        return newMean to newVariance
    }

    companion object {
        const val DEFAULT_ALPHA = 0.01

        /** Above this much accelerometer variation, the user is moving too much to call it calm. */
        const val CALM_MOTION_MAX = 0.6

        /** Outside these ranges the sensor is lying to us, not reporting an unusual human. */
        val PLAUSIBLE_HR_RANGE = 30.0..220.0
        val PLAUSIBLE_HRV_RANGE = 1.0..400.0
    }
}
