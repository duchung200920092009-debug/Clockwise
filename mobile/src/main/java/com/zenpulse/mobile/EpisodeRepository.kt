package com.zenpulse.mobile

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Holds the episodes received from the watch.
 *
 * A process-wide singleton because the data arrives in a [WearableListenerService] that runs with
 * no activity attached — the phone app is usually closed when the watch decides to sync. The
 * payload is cached to disk so opening the app later shows history rather than an empty screen
 * waiting for the next sync.
 */
object EpisodeRepository {

    private const val CACHE_FILE = "episodes_cache.json"

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, CACHE_FILE)
        if (file.exists()) {
            _episodes.value = Episode.parseList(runCatching { file.readText() }.getOrDefault("[]"))
        }
    }

    suspend fun onPayloadReceived(context: Context, json: String) = withContext(Dispatchers.IO) {
        val parsed = Episode.parseList(json).sortedByDescending { it.startedAtMs }
        _episodes.value = parsed
        runCatching { File(context.filesDir, CACHE_FILE).writeText(json) }
        Unit
    }

    /** Writes a CSV into cache for sharing, returning the file to hand to a share intent. */
    suspend fun exportCsv(context: Context): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(exportDir, "zenpulse_episodes.csv").apply {
            writeText(Episode.toCsv(_episodes.value))
        }
    }
}
