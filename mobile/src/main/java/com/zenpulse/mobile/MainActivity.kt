package com.zenpulse.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * The phone companion: a readable history of what the watch recorded.
 *
 * Its job is pattern-spotting on a screen big enough to do it on — "three episodes this week, all
 * before my 9am seminar" is the kind of observation that actually changes something, and it is
 * impossible to see on a 1.2" display.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EpisodeListScreen()
                }
            }
        }
    }
}

@Composable
private fun EpisodeListScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val episodes by EpisodeRepository.episodes.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { EpisodeRepository.load(context) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "ZenPulse", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Episodes recorded on your watch",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (episodes.isEmpty()) {
            Text(
                text = "Nothing synced yet. Episodes appear here once your watch records one and " +
                    "both devices are connected.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes, key = { it.id }) { episode -> EpisodeCard(episode) }
        }

        FilledTonalButton(
            onClick = {
                scope.launch {
                    val file = EpisodeRepository.exportCsv(context)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Export episodes"))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text("Export as CSV")
        }

        Text(
            // Export hands mental-health data to whichever app the user picks, and from there it
            // is out of ZenPulse's control entirely. Saying so at the button is the honest place.
            text = "Exporting sends this history to another app of your choosing. It leaves " +
                "ZenPulse at that point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EpisodeCard(episode: Episode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${episode.peakLevel.lowercase().replaceFirstChar { it.uppercase() }} · " +
                    formatDuration(episode.durationMs),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatTimestamp(episode.startedAtMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            episode.meanBpm?.let { bpm ->
                Text(
                    text = "Avg ${"%.0f".format(bpm)} bpm" +
                        (episode.meanHrvMs?.let { " · HRV ${"%.0f".format(it)} ms" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (episode.breathingCompleted) {
                Text(
                    text = "Breathing exercise completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    return if (minutes < 1) "under a minute" else "$minutes min"
}

private fun formatTimestamp(timestampMs: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(timestampMs))
