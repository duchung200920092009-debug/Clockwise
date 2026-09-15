package com.zenpulse.wear.presentation.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zenpulse.wear.domain.StressEpisode
import com.zenpulse.wear.domain.StressLevel
import com.zenpulse.wear.domain.UserFeedback
import com.zenpulse.wear.presentation.theme.StressColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Recent episodes, newest first.
 *
 * The screen shows *what happened*, never a running total or a weekly score. A dashboard counting
 * "stress episodes this week" invites the user to compete with their own nervous system, which for
 * this audience reliably makes things worse.
 */
@Composable
fun HistoryScreen(
    episodes: List<StressEpisode>,
    onFeedback: (episodeId: String, feedback: UserFeedback) -> Unit,
) {
    val listState = rememberScalingLazyListState()

    if (episodes.isEmpty()) {
        Text(
            text = "No episodes recorded yet.",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        return
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(episode = episode, onFeedback = onFeedback)
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: StressEpisode,
    onFeedback: (String, UserFeedback) -> Unit,
) {
    var expanded by remember(episode.id) { mutableStateOf(false) }

    // One Column per list item: a lazy-list slot should emit a single layout node, and an expanded
    // row emits several children.
    Column(modifier = Modifier.fillMaxWidth()) {
        EpisodeRowContent(episode, expanded, onToggle = { expanded = !expanded }, onFeedback)
    }
}

@Composable
private fun EpisodeRowContent(
    episode: StressEpisode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFeedback: (String, UserFeedback) -> Unit,
) {
    Chip(
        onClick = onToggle,
        label = {
            Text(
                text = "${episode.peakLevel.label()} · ${episode.durationMs.asMinutes()}",
                style = MaterialTheme.typography.button,
            )
        },
        secondaryLabel = { Text(episode.startedAtMs.asTimeLabel()) },
        colors = ChipDefaults.secondaryChipColors(
            contentColor = StressColors.forLevel(episode.peakLevel),
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    if (!expanded) return

    episode.meanBpm?.let { bpm ->
        Text(
            text = "Avg ${"%.0f".format(bpm)} bpm" +
                (episode.meanHrvMs?.let { " · HRV ${"%.0f".format(it)} ms" } ?: ""),
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }

    if (episode.breathingCompleted) {
        Text(
            text = "You breathed through this one",
            style = MaterialTheme.typography.caption3,
            color = StressColors.Calm,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }

    // The honesty check: was the app right? Offered once, never nagged for.
    if (episode.userFeedback == UserFeedback.NONE) {
        Text(
            text = "Did this match how you felt?",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        CompactChip(
            onClick = { onFeedback(episode.id, UserFeedback.ACCURATE) },
            label = { Text("Yes") },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        CompactChip(
            onClick = { onFeedback(episode.id, UserFeedback.FALSE_ALARM) },
            label = { Text("No, I was fine") },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    } else {
        Text(
            text = when (episode.userFeedback) {
                UserFeedback.ACCURATE -> "You said this matched"
                UserFeedback.FALSE_ALARM -> "You said this was a false alarm"
                UserFeedback.NONE -> ""
            },
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun StressLevel.label(): String = when (this) {
    StressLevel.CALM -> "Calm"
    StressLevel.RISING -> "Rising"
    StressLevel.ELEVATED -> "Elevated"
    StressLevel.HIGH -> "Activated"
}

private fun Long.asMinutes(): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    return if (minutes < 1) "under a minute" else "$minutes min"
}

private fun Long.asTimeLabel(): String =
    SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(this))
