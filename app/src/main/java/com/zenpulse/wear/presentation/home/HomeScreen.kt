package com.zenpulse.wear.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.services.client.data.DataTypeAvailability
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zenpulse.wear.domain.NotAssessableReason
import com.zenpulse.wear.domain.StressAssessment
import com.zenpulse.wear.domain.StressLevel
import com.zenpulse.wear.monitor.MonitorState
import com.zenpulse.wear.presentation.theme.StressColors

/** What the dial should show right now, resolved from the current assessment. */
private data class StressDisplay(
    val title: String,
    val subtitle: String,
    val color: Color,
    val progress: Float,
)

@Composable
fun HomeScreen(
    state: MonitorState,
    onToggleMonitoring: () -> Unit,
    onBreathe: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val display = state.toDisplay()
    val listState = rememberScalingLazyListState()

    // A lazy column rather than a plain one: on a 1.2" round screen the dial plus four chips do
    // not fit, and Wear users expect rotary-crown scrolling with the scaling/fading edges.
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { StressDial(display) }

        item {
            Text(
                text = display.subtitle,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        item { Vitals(state) }

        item {
            Chip(
                onClick = onToggleMonitoring,
                label = {
                    Text(if (state.running) "Stop monitoring" else "Start monitoring")
                },
                colors = if (state.running) {
                    ChipDefaults.secondaryChipColors()
                } else {
                    ChipDefaults.primaryChipColors()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Chip(
                onClick = onBreathe,
                label = { Text("Breathe") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Chip(
                onClick = onHistory,
                label = { Text("History") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Chip(
                onClick = onSettings,
                label = { Text("Settings") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StressDial(display: StressDisplay) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = display.progress,
            modifier = Modifier.size(90.dp),
            indicatorColor = display.color,
            trackColor = MaterialTheme.colors.surface,
            strokeWidth = 6.dp,
        )
        Text(
            text = display.title,
            style = MaterialTheme.typography.title3,
            color = display.color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Vitals(state: MonitorState) {
    val bpm = state.bpm?.let { "%.0f bpm".format(it) } ?: "-- bpm"
    val hrv = state.hrvProxyMs?.let { "%.0f ms".format(it) } ?: "-- ms"

    Text(
        text = "$bpm · HRV $hrv",
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Copy matters as much as the maths here. The user may be anxious, so every string below states
 * an observation about the body ("your body looks activated") rather than a verdict about the
 * person ("you are stressed"), and always leaves them somewhere to go next.
 */
private fun MonitorState.toDisplay(): StressDisplay {
    if (!running) {
        return StressDisplay(
            title = "Off",
            subtitle = "Monitoring is off",
            color = StressColors.Unknown,
            progress = 0f,
        )
    }

    return when (val current = assessment) {
        is StressAssessment.Learning -> StressDisplay(
            title = "${(current.progress * 100).toInt()}%",
            subtitle = "Learning what calm looks like for you",
            color = StressColors.Unknown,
            progress = current.progress,
        )

        is StressAssessment.NotAssessable -> when (current.reason) {
            NotAssessableReason.MOVING -> StressDisplay(
                title = "Active",
                subtitle = "Can't read stress while you're moving",
                color = StressColors.Unknown,
                progress = 0f,
            )

            NotAssessableReason.NO_SIGNAL -> StressDisplay(
                title = "No signal",
                subtitle = if (availability == DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY) {
                    "Watch is off your wrist"
                } else {
                    "Try tightening the band a little"
                },
                color = StressColors.Unknown,
                progress = 0f,
            )
        }

        is StressAssessment.Assessed -> StressDisplay(
            title = current.level.title(),
            subtitle = current.level.subtitle(current.hrvContributed),
            color = StressColors.forLevel(current.level),
            progress = current.score.toFloat().coerceIn(0f, 1f),
        )
    }
}

private fun StressLevel.title(): String = when (this) {
    StressLevel.CALM -> "Calm"
    StressLevel.RISING -> "Rising"
    StressLevel.ELEVATED -> "Elevated"
    StressLevel.HIGH -> "Activated"
}

private fun StressLevel.subtitle(hrvContributed: Boolean): String = when (this) {
    StressLevel.CALM -> "Your body looks settled"
    StressLevel.RISING -> "Slightly above your usual calm"
    StressLevel.ELEVATED -> "Your body looks activated — a minute of breathing may help"
    StressLevel.HIGH -> "Strongly activated. Breathe with me?"
}.let { base ->
    // Be honest when the reading rests on heart rate alone; the user deserves to know the
    // difference between a confident signal and a partial one.
    if (!hrvContributed && this != StressLevel.CALM) "$base (heart rate only)" else base
}
