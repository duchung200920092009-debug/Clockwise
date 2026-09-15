package com.zenpulse.mobile

import org.json.JSONArray

/**
 * A stress episode as received from the watch.
 *
 * This deliberately duplicates the watch's `StressEpisode` rather than sharing a module with it.
 * The two apps install and update independently — a user can easily run last month's phone app
 * against this week's watch app — so the JSON below is a *wire contract* between two separately
 * versioned programs, not an internal data structure. Parsing defensively here (every field
 * optional, unknown values tolerated) is what lets an older phone app keep working when the watch
 * app starts sending a field it has never heard of.
 */
data class Episode(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val peakLevel: String,
    val peakScore: Double,
    val meanBpm: Double?,
    val meanHrvMs: Double?,
    val breathingCompleted: Boolean,
    val userFeedback: String,
) {
    val id: String get() = "episode_$startedAtMs"
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0L)

    companion object {
        fun parseList(json: String): List<Episode> = runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        Episode(
                            startedAtMs = obj.optLong("startedAtMs"),
                            endedAtMs = obj.optLong("endedAtMs"),
                            peakLevel = obj.optString("peakLevel", "ELEVATED"),
                            peakScore = obj.optDouble("peakScore", 0.0),
                            meanBpm = if (obj.has("meanBpm")) obj.optDouble("meanBpm") else null,
                            meanHrvMs = if (obj.has("meanHrvMs")) obj.optDouble("meanHrvMs") else null,
                            breathingCompleted = obj.optBoolean("breathingCompleted", false),
                            userFeedback = obj.optString("userFeedback", "NONE"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        /** CSV for export/sharing — the format a researcher or clinician can actually open. */
        fun toCsv(episodes: List<Episode>): String = buildString {
            append("started_at_ms,ended_at_ms,duration_ms,peak_level,peak_score,mean_bpm,mean_hrv_ms,breathing_completed,user_feedback\n")
            episodes.forEach { e ->
                append(e.startedAtMs).append(',')
                append(e.endedAtMs).append(',')
                append(e.durationMs).append(',')
                append(e.peakLevel).append(',')
                append(e.peakScore).append(',')
                append(e.meanBpm ?: "").append(',')
                append(e.meanHrvMs ?: "").append(',')
                append(e.breathingCompleted).append(',')
                append(e.userFeedback).append('\n')
            }
        }
    }
}
