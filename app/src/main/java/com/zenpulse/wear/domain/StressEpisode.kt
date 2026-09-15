package com.zenpulse.wear.domain

/**
 * A recorded period during which the detector held an actionable stress level.
 *
 * Episodes, not raw samples, are what the user and their phone companion see. Raw heart-rate
 * traces are both overwhelming to read and far more sensitive; a short list of "here's when your
 * body was activated, and what you did about it" is the part that's actually useful for spotting
 * patterns ("every Tuesday morning", "always before exams").
 */
data class StressEpisode(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val peakLevel: StressLevel,
    val peakScore: Double,
    /** Mean heart rate across the episode, for context in the history view. */
    val meanBpm: Double?,
    /** Mean HRV proxy across the episode. */
    val meanHrvMs: Double?,
    /** Whether the user actually ran a breathing exercise during or just after the episode. */
    val breathingCompleted: Boolean = false,
    /** Optional single-tap self-report, so the detector's output can be checked against felt experience. */
    val userFeedback: UserFeedback = UserFeedback.NONE,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0L)

    /** Stable identifier for syncing to the phone without a database. */
    val id: String get() = "episode_$startedAtMs"
}

/**
 * The user's own verdict on an episode.
 *
 * This exists to keep the app honest. A detector that is never checked against how the person
 * actually felt will drift into confident nonsense, and for this user group that is worse than
 * useless. Collected as one optional tap, never nagged for.
 */
enum class UserFeedback {
    NONE,

    /** "Yes, that matched how I felt." */
    ACCURATE,

    /** "No, I was fine." — the signal that matters most for tuning sensitivity down. */
    FALSE_ALARM,
}
