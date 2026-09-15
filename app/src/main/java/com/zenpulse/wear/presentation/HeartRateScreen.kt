package com.zenpulse.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.services.client.data.DataTypeAvailability
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

/**
 * The single screen: live BPM, sensor status, HRV proxy, motion, a recording indicator while
 * logging, and a start/stop button. Deliberately minimal — the goal is a trustworthy real-time
 * readout on the watch, not a dashboard.
 */
@Composable
fun HeartRateScreen(
    state: HeartRateUiState,
    onToggleMeasuring: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TimeText()

        if (!state.hasCapability) {
            Text(
                text = "This watch cannot measure heart rate through Health Services.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
            )
            return@Column
        }

        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Heart rate",
            tint = if (state.availability == DataTypeAvailability.AVAILABLE) Color(0xFFE53935) else Color.Gray,
            modifier = Modifier.size(28.dp),
        )

        Text(
            text = state.bpm?.let { "%.0f".format(it) } ?: "--",
            style = MaterialTheme.typography.display1,
        )
        Text(text = "BPM", style = MaterialTheme.typography.caption1)

        Text(
            text = availabilityLabel(state),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (state.measuring) {
            Text(
                text = "HRV proxy: ${state.hrvProxyMs?.let { "%.0f ms".format(it) } ?: "…"}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            Text(
                text = "Motion: ${state.accelMagnitude?.let { "%.1f m/s²".format(it) } ?: "…"}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            if (state.loggingFileName != null) {
                Text(
                    text = "● Recording",
                    style = MaterialTheme.typography.caption2,
                    color = Color(0xFF4CAF50),
                )
            }
        }

        Button(
            onClick = onToggleMeasuring,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(if (state.measuring) "Stop" else "Start")
        }
    }
}

private fun availabilityLabel(state: HeartRateUiState): String {
    if (!state.measuring) return "Tap Start to measure"
    return when (state.availability) {
        DataTypeAvailability.AVAILABLE -> "Measuring…"
        DataTypeAvailability.ACQUIRING -> "Acquiring signal…"
        DataTypeAvailability.UNAVAILABLE -> "No signal — check watch is snug on wrist"
        DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> "Watch is off your wrist"
        else -> "Waiting for sensor…"
    }
}
