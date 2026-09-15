package com.zenpulse.wear.domain

/**
 * One aggregated window of sensor data — the unit both [BaselineTracker] and [StressDetector]
 * reason about.
 *
 * Raw sensor events arrive far too fast (accelerometer ~50 Hz) and far too noisily (a single BPM
 * sample swings several beats) to make decisions on. The data layer aggregates roughly
 * [WINDOW_DURATION_MS] of raw samples into one window and hands it here.
 *
 * Every field is nullable because sensors drop out constantly in real life: the watch comes off
 * the wrist, the PPG loses contact, the user showers. A null means "we genuinely don't know",
 * which the detector treats very differently from a low value.
 */
data class SensorWindow(
    /** Wall-clock time at the end of the window, for episode timestamps and history. */
    val timestampMs: Long,
    /** Mean heart rate over the window, in BPM. */
    val bpm: Double?,
    /** Mean HRV proxy over the window, in ms. See [HrvEstimator] for what this is and isn't. */
    val hrvProxyMs: Double?,
    /**
     * Motion intensity: the standard deviation of accelerometer magnitude over the window, in
     * m/s². Standard deviation rather than mean, because mean magnitude is ~9.8 (gravity) whether
     * you are sprinting or asleep — it is the *variation* that indicates movement.
     */
    val motionIntensity: Double?,
) {
    companion object {
        /**
         * Target window length. Long enough that one bad PPG reading can't move the average much,
         * short enough that a rising episode is caught within a minute or so of onset.
         */
        const val WINDOW_DURATION_MS = 10_000L
    }
}
