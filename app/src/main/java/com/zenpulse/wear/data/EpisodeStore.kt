package com.zenpulse.wear.data

import android.content.Context
import com.zenpulse.wear.domain.StressEpisode
import com.zenpulse.wear.domain.StressLevel
import com.zenpulse.wear.domain.UserFeedback
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores recorded [StressEpisode]s as a small JSON file on the watch.
 *
 * A JSON file rather than a database: the app keeps at most [MAX_EPISODES] episodes, the whole
 * history is read and written as one unit, and avoiding Room keeps the build free of annotation
 * processing. If history ever grows to "every reading, forever", this is the piece to replace.
 *
 * History is capped rather than unbounded on purpose. This is sensitive data about someone's
 * mental state, and keeping months of it on a watch — which gets lost, handed to a repair shop,
 * or shared with a parent — is a real risk with little added benefit over recent trends.
 */
class EpisodeStore(context: Context) {

    private val file = File(context.filesDir, "episodes.json")
    private val mutex = Mutex()

    private val _episodes = MutableStateFlow<List<StressEpisode>>(emptyList())
    /** Newest first. */
    val episodes: StateFlow<List<StressEpisode>> = _episodes.asStateFlow()

    /** Load from disk into memory. Call once at startup. */
    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _episodes.value = runCatching { readFile() }.getOrDefault(emptyList())
        }
    }

    suspend fun add(episode: StressEpisode) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = (listOf(episode) + _episodes.value).take(MAX_EPISODES)
            _episodes.value = updated
            runCatching { writeFile(updated) }
        }
        Unit
    }

    /** Record the user's own verdict on an episode — see [UserFeedback] for why this matters. */
    suspend fun setFeedback(episodeId: String, feedback: UserFeedback) = update { episodes ->
        episodes.map { episode ->
            if (episode.id == episodeId) episode.copy(userFeedback = feedback) else episode
        }
    }

    /**
     * Flag the most recent episode as having been followed by a breathing exercise, provided it
     * is recent enough to plausibly be the reason the user opened it. Breathing started out of the
     * blue, hours after the last episode, is not evidence that the intervention was used.
     */
    suspend fun markBreathingCompletedForRecent(
        withinMs: Long = DEFAULT_ATTRIBUTION_WINDOW_MS,
        nowMs: Long = System.currentTimeMillis(),
    ) = update { episodes ->
        val mostRecent = episodes.firstOrNull() ?: return@update episodes
        if (nowMs - mostRecent.endedAtMs > withinMs) return@update episodes
        episodes.toMutableList().also { it[0] = mostRecent.copy(breathingCompleted = true) }
    }

    /** Apply a transformation under the lock, persisting only when something actually changed. */
    private suspend fun update(
        transform: (List<StressEpisode>) -> List<StressEpisode>,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _episodes.value
            val updated = transform(current)
            if (updated != current) {
                _episodes.value = updated
                runCatching { writeFile(updated) }
            }
        }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _episodes.value = emptyList()
            runCatching { if (file.exists()) file.delete() }
        }
        Unit
    }

    /** Serialised form, shared with the phone companion over the Wearable Data Layer. */
    fun toJson(episodes: List<StressEpisode> = _episodes.value): String {
        val array = JSONArray()
        episodes.forEach { episode ->
            array.put(
                JSONObject().apply {
                    put("startedAtMs", episode.startedAtMs)
                    put("endedAtMs", episode.endedAtMs)
                    put("peakLevel", episode.peakLevel.name)
                    put("peakScore", episode.peakScore)
                    episode.meanBpm?.let { put("meanBpm", it) }
                    episode.meanHrvMs?.let { put("meanHrvMs", it) }
                    put("breathingCompleted", episode.breathingCompleted)
                    put("userFeedback", episode.userFeedback.name)
                }
            )
        }
        return array.toString()
    }

    private fun readFile(): List<StressEpisode> {
        if (!file.exists()) return emptyList()
        return parseJson(file.readText())
    }

    private fun writeFile(episodes: List<StressEpisode>) {
        file.writeText(toJson(episodes))
    }

    companion object {
        const val MAX_EPISODES = 200

        /** How long after an episode a breathing session still counts as a response to it. */
        const val DEFAULT_ATTRIBUTION_WINDOW_MS = 30 * 60 * 1000L

        /** Shared with the phone module's own parser, which reads the identical format. */
        fun parseJson(json: String): List<StressEpisode> {
            val array = JSONArray(json)
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        StressEpisode(
                            startedAtMs = obj.optLong("startedAtMs"),
                            endedAtMs = obj.optLong("endedAtMs"),
                            peakLevel = runCatching {
                                StressLevel.valueOf(obj.optString("peakLevel"))
                            }.getOrDefault(StressLevel.ELEVATED),
                            peakScore = obj.optDouble("peakScore", 0.0),
                            meanBpm = if (obj.has("meanBpm")) obj.optDouble("meanBpm") else null,
                            meanHrvMs = if (obj.has("meanHrvMs")) obj.optDouble("meanHrvMs") else null,
                            breathingCompleted = obj.optBoolean("breathingCompleted", false),
                            userFeedback = runCatching {
                                UserFeedback.valueOf(obj.optString("userFeedback"))
                            }.getOrDefault(UserFeedback.NONE),
                        )
                    )
                }
            }
        }
    }
}
