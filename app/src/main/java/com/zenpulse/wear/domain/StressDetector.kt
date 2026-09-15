package com.zenpulse.wear.domain

/** How activated the body appears, relative to this person's own calm baseline. */
enum class StressLevel {
    CALM,
    RISING,
    ELEVATED,
    HIGH;

    val isActionable: Boolean get() = this == ELEVATED || this == HIGH
}

/** Why a window could not be assessed. Surfaced to the user so silence is never mysterious. */
enum class NotAssessableReason {
    /** Too much movement — exercise raises heart rate and would masquerade as stress. */
    MOVING,

    /** No usable heart rate: watch off wrist, loose band, PPG lost contact. */
    NO_SIGNAL,
}

sealed interface StressAssessment {
    /** Baseline is still being learned; [progress] is 0..1 for the UI. */
    data class Learning(val progress: Float) : StressAssessment

    data class NotAssessable(val reason: NotAssessableReason) : StressAssessment

    data class Assessed(
        val level: StressLevel,
        /** 0..1 continuous activation score, after hysteresis-free instantaneous computation. */
        val score: Double,
        /** How many SDs above calm the heart rate is. Negative means calmer than usual. */
        val hrZ: Double,
        /** How many SDs *below* calm the HRV proxy is (suppressed HRV = sympathetic activation). */
        val hrvZ: Double?,
        /** False when HRV was unavailable and the score rests on heart rate alone. */
        val hrvContributed: Boolean,
    ) : StressAssessment
}

/**
 * User-facing sensitivity. People differ enormously in what they want from this: some want to
 * catch every early warning, others find frequent alerts more stressful than the stress.
 */
enum class StressSensitivity(val thresholdShift: Double) {
    /** Needs stronger evidence before saying anything. */
    LOW(0.10),
    BALANCED(0.0),
    /** Speaks up earlier, at the cost of more false alarms. */
    HIGH(-0.10),
}

/**
 * Rule-based stress detection against a personal [BaselineSnapshot].
 *
 * Deliberately rule-based rather than a learned model. Three reasons: it runs in microseconds on a
 * watch battery, every alert can be explained to the user in one sentence ("your heart rate is up
 * and your HRV is down while you're sitting still"), and it needs no training data from a
 * population that — for this app's users — would be sensitive mental-health data.
 *
 * The design bias throughout is **against false positives**. For someone with anxiety, a watch
 * that wrongly announces they are panicking can *cause* the episode it claimed to detect. Every
 * threshold here errs towards staying quiet.
 *
 * Pure Kotlin, no Android dependencies, so the alerting logic is unit-testable on a plain JVM.
 */
class StressDetector(
    var sensitivity: StressSensitivity = StressSensitivity.BALANCED,
) {

    private var currentLevel: StressLevel = StressLevel.CALM
    private var pendingLevel: StressLevel? = null
    private var pendingCount: Int = 0

    /** The level last reported, after hysteresis. */
    val level: StressLevel get() = currentLevel

    fun reset() {
        currentLevel = StressLevel.CALM
        pendingLevel = null
        pendingCount = 0
    }

    fun update(window: SensorWindow, baseline: BaselineSnapshot): StressAssessment {
        // Gate 1: movement. A raised heart rate while walking upstairs is not stress, and no
        // amount of clever scoring downstream can recover from conflating the two. Pending state
        // is cleared so a burst of activity can't half-escalate the detector.
        val motion = window.motionIntensity
        if (motion != null && motion > MOTION_GATE) {
            clearPending()
            return StressAssessment.NotAssessable(NotAssessableReason.MOVING)
        }

        val bpm = window.bpm
        if (bpm == null) {
            clearPending()
            return StressAssessment.NotAssessable(NotAssessableReason.NO_SIGNAL)
        }

        // Gate 2: without a personal baseline there is no "unusual for you" to compare against.
        if (!baseline.isReady) {
            clearPending()
            return StressAssessment.Learning(baseline.learningProgress)
        }

        val hrZ = (bpm - baseline.hrMean) / baseline.hrSd

        // HRV is inverted on purpose: sympathetic ("fight or flight") activation *suppresses*
        // beat-to-beat variability, so a drop below baseline is the stress-positive direction.
        val hrv = window.hrvProxyMs
        val hrvZ = if (hrv != null && baseline.hrvMean > 0.0) {
            (baseline.hrvMean - hrv) / baseline.hrvSd
        } else {
            null
        }

        val score = computeScore(hrZ, hrvZ)
        val instantLevel = levelFor(score)
        val stableLevel = applyHysteresis(instantLevel)

        return StressAssessment.Assessed(
            level = stableLevel,
            score = score,
            hrZ = hrZ,
            hrvZ = hrvZ,
            hrvContributed = hrvZ != null,
        )
    }

    /**
     * Blends the two z-scores into a 0..1 activation score.
     *
     * HRV carries more weight than heart rate because it is the more *specific* stress marker:
     * heart rate rises for a dozen benign reasons (caffeine, standing up, a warm room), whereas
     * a simultaneous HRV drop points at autonomic arousal specifically.
     */
    private fun computeScore(hrZ: Double, hrvZ: Double?): Double {
        val hrComponent = (hrZ / Z_SATURATION).coerceIn(0.0, 1.0)

        if (hrvZ == null) {
            // Heart rate alone is too unspecific to justify the top of the scale, so it is capped
            // below the HIGH threshold: without HRV this detector may say "something's up", never
            // "you are highly activated".
            return (hrComponent * HR_ONLY_WEIGHT).coerceAtMost(MAX_SCORE_WITHOUT_HRV)
        }

        val hrvComponent = (hrvZ / Z_SATURATION).coerceIn(0.0, 1.0)
        return (HR_WEIGHT * hrComponent + HRV_WEIGHT * hrvComponent).coerceIn(0.0, 1.0)
    }

    private fun levelFor(score: Double): StressLevel {
        val shift = sensitivity.thresholdShift
        return when {
            score >= HIGH_THRESHOLD + shift -> StressLevel.HIGH
            score >= ELEVATED_THRESHOLD + shift -> StressLevel.ELEVATED
            score >= RISING_THRESHOLD + shift -> StressLevel.RISING
            else -> StressLevel.CALM
        }
    }

    /**
     * Requires a level to persist across several windows before it is adopted, which is what stops
     * the UI (and any alert) from flickering on a single noisy reading.
     *
     * Escalation is slower than de-escalation on purpose: being told you are stressed when you
     * aren't is more harmful than being told you've calmed down a few seconds early.
     */
    private fun applyHysteresis(instantLevel: StressLevel): StressLevel {
        if (instantLevel == currentLevel) {
            clearPending()
            return currentLevel
        }

        if (pendingLevel == instantLevel) {
            pendingCount++
        } else {
            pendingLevel = instantLevel
            pendingCount = 1
        }

        val escalating = instantLevel.ordinal > currentLevel.ordinal
        val required = if (escalating) WINDOWS_TO_ESCALATE else WINDOWS_TO_DE_ESCALATE
        if (pendingCount >= required) {
            currentLevel = instantLevel
            clearPending()
        }
        return currentLevel
    }

    private fun clearPending() {
        pendingLevel = null
        pendingCount = 0
    }

    companion object {
        /** Accelerometer SD above which we decline to assess rather than risk calling motion stress. */
        const val MOTION_GATE = 1.2

        /** z-score treated as "as extreme as this scale goes". 3 SDs above personal calm is a lot. */
        const val Z_SATURATION = 3.0

        const val HR_WEIGHT = 0.45
        const val HRV_WEIGHT = 0.55

        /** Weight applied when HRV is missing, and the ceiling that keeps HR-only out of HIGH. */
        const val HR_ONLY_WEIGHT = 0.9
        const val MAX_SCORE_WITHOUT_HRV = 0.75

        const val RISING_THRESHOLD = 0.35
        const val ELEVATED_THRESHOLD = 0.60
        const val HIGH_THRESHOLD = 0.80

        /** 3 windows ≈ 30 seconds of sustained signal before escalating. */
        const val WINDOWS_TO_ESCALATE = 3
        const val WINDOWS_TO_DE_ESCALATE = 2
    }
}
