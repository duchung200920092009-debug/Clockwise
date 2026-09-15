package com.zenpulse.wear.domain

/** The four possible phases of a paced-breathing cycle. */
enum class BreathPhase(val label: String) {
    INHALE("Breathe in"),
    HOLD_IN("Hold"),
    EXHALE("Breathe out"),
    HOLD_OUT("Rest"),
}

/** Where in the cycle the user is right now — everything the UI needs to render a frame. */
data class BreathingState(
    val phase: BreathPhase,
    /** 0..1 through the current phase. */
    val phaseProgress: Float,
    val phaseRemainingMs: Long,
    /** Completed cycles so far. */
    val cycleIndex: Int,
    /**
     * 0..1 size of the guide circle: 0 at full exhale, 1 at full inhale. Pre-computed here so the
     * UI layer holds no breathing logic and the animation can be verified in a unit test.
     */
    val expansion: Float,
)

/**
 * A paced-breathing exercise, defined purely by its phase durations.
 *
 * Paced breathing is the intervention this whole app builds towards: slow breathing with a
 * prolonged exhale raises vagal tone and is one of the few things a person can consciously do to
 * shift their own autonomic state within a minute. The watch's job is simply to give the pace
 * something to follow.
 */
data class BreathingPattern(
    val id: String,
    val displayName: String,
    /** One line explaining, to the user, when this pattern helps. */
    val description: String,
    val inhaleMs: Long,
    val holdInMs: Long,
    val exhaleMs: Long,
    val holdOutMs: Long,
) {
    val cycleMs: Long = inhaleMs + holdInMs + exhaleMs + holdOutMs

    /** Zero-length phases are dropped so a pattern without holds never reports a 0ms "Hold". */
    private val segments: List<Pair<BreathPhase, Long>> = listOf(
        BreathPhase.INHALE to inhaleMs,
        BreathPhase.HOLD_IN to holdInMs,
        BreathPhase.EXHALE to exhaleMs,
        BreathPhase.HOLD_OUT to holdOutMs,
    ).filter { (_, duration) -> duration > 0L }

    init {
        require(cycleMs > 0L) { "A breathing pattern needs at least one non-zero phase" }
        require(inhaleMs > 0L && exhaleMs > 0L) { "A cycle must include both an inhale and an exhale" }
    }

    /** How many full cycles fit in a session of [durationMs]. */
    fun cyclesIn(durationMs: Long): Int = (durationMs / cycleMs).toInt()

    /** Resolve the breathing state at [elapsedMs] since the exercise started. */
    fun stateAt(elapsedMs: Long): BreathingState {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val cycleIndex = (elapsed / cycleMs).toInt()
        var offset = elapsed % cycleMs

        for ((phase, duration) in segments) {
            if (offset < duration) {
                val progress = (offset.toDouble() / duration).toFloat().coerceIn(0f, 1f)
                return BreathingState(
                    phase = phase,
                    phaseProgress = progress,
                    phaseRemainingMs = duration - offset,
                    cycleIndex = cycleIndex,
                    expansion = expansionFor(phase, progress),
                )
            }
            offset -= duration
        }

        // Unreachable: offset < cycleMs and the segments sum to cycleMs. Kept total for safety.
        val (phase, duration) = segments.last()
        return BreathingState(phase, 1f, 0L, cycleIndex, expansionFor(phase, 1f))
    }

    private fun expansionFor(phase: BreathPhase, progress: Float): Float = when (phase) {
        BreathPhase.INHALE -> progress
        BreathPhase.HOLD_IN -> 1f
        BreathPhase.EXHALE -> 1f - progress
        BreathPhase.HOLD_OUT -> 0f
    }

    companion object {
        /**
         * Equal 5.5s in / 5.5s out — "resonance" or coherent breathing, around the rate at which
         * heart-rate oscillation and breathing synchronise for most adults.
         */
        val COHERENT = BreathingPattern(
            id = "coherent",
            displayName = "Coherent",
            description = "Even, slow breathing. Gentle — good when you feel panicky.",
            inhaleMs = 5_500,
            holdInMs = 0,
            exhaleMs = 5_500,
            holdOutMs = 0,
        )

        /** Longer out than in. The exhale is the half that engages the calming branch. */
        val EXTENDED_EXHALE = BreathingPattern(
            id = "extended_exhale",
            displayName = "Long exhale",
            description = "Breathe in for 4, out for 6. Settles a racing heart.",
            inhaleMs = 4_000,
            holdInMs = 0,
            exhaleMs = 6_000,
            holdOutMs = 0,
        )

        /** Equal four-count square. Familiar, easy to follow, good for general steadying. */
        val BOX = BreathingPattern(
            id = "box",
            displayName = "Box",
            description = "In, hold, out, hold — four counts each. Good for focus.",
            inhaleMs = 4_000,
            holdInMs = 4_000,
            exhaleMs = 4_000,
            holdOutMs = 4_000,
        )

        val ALL = listOf(COHERENT, EXTENDED_EXHALE, BOX)

        /**
         * Pick a pattern to offer for a given stress level.
         *
         * Note the deliberate choice at [StressLevel.HIGH]: no breath-holding. When someone is
         * close to panic, holding the breath can intensify the feeling of air hunger and make
         * things worse, so the most activated state gets the gentlest, hold-free pattern rather
         * than the most "advanced" one.
         */
        fun forLevel(level: StressLevel): BreathingPattern = when (level) {
            StressLevel.HIGH -> COHERENT
            StressLevel.ELEVATED -> EXTENDED_EXHALE
            else -> BOX
        }

        fun byId(id: String): BreathingPattern = ALL.firstOrNull { it.id == id } ?: COHERENT
    }
}
