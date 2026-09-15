package com.zenpulse.wear.presentation.breathing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zenpulse.wear.domain.BreathingPattern
import com.zenpulse.wear.presentation.theme.StressColors
import kotlin.math.roundToInt

/**
 * The guided-breathing intervention: a circle that expands and contracts at the pattern's pace,
 * with a haptic pulse at every phase change.
 *
 * The haptics are not decoration. Someone mid-episode often cannot focus on a small screen, and
 * looking at a device can itself feel like pressure — so the exercise is designed to be followable
 * with the wrist down and eyes closed, using the taps alone.
 */
@Composable
fun BreathingScreen(
    pattern: BreathingPattern,
    onPhaseChange: () -> Unit,
    onCompleted: () -> Unit,
    onExit: () -> Unit,
    targetDurationMs: Long = DEFAULT_SESSION_MS,
) {
    var elapsedMs by remember(pattern) { mutableStateOf(0L) }
    var finished by remember(pattern) { mutableStateOf(false) }

    // Driven by the frame clock rather than a fixed delay, so the animation stays smooth and
    // automatically pauses when the screen is not composing (i.e. when the display sleeps).
    LaunchedEffect(pattern) {
        val startMs = withFrameMillis { it }
        while (!finished) {
            val frameMs = withFrameMillis { it }
            elapsedMs = frameMs - startMs
            if (elapsedMs >= targetDurationMs) finished = true
        }
    }

    val state = pattern.stateAt(elapsedMs)

    // One tap per phase boundary. Keyed on the phase so it fires on change, not every frame.
    LaunchedEffect(state.phase, finished) {
        if (!finished) onPhaseChange()
    }

    LaunchedEffect(finished) {
        if (finished) onCompleted()
    }

    if (finished) {
        CompletionView(onExit = onExit)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val maxRadius = size.minDimension / 2f
                // Never shrinks to nothing: a circle that vanishes on the exhale reads as the
                // exercise stopping rather than as breath leaving.
                val radius = maxRadius * (MIN_SCALE + (1f - MIN_SCALE) * state.expansion)
                drawCircle(color = StressColors.Calm.copy(alpha = 0.35f), radius = radius)
                drawCircle(
                    color = StressColors.Calm,
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                )
            }

            Text(
                text = "${(state.phaseRemainingMs / 1000.0).roundToInt()}",
                style = MaterialTheme.typography.display2,
            )
        }

        Text(
            text = state.phase.label,
            style = MaterialTheme.typography.title3,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            text = "${state.cycleIndex + 1} of ${pattern.cyclesIn(targetDurationMs)}",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompletionView(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Done",
            style = MaterialTheme.typography.title2,
            color = StressColors.Calm,
        )
        Text(
            // No praise, no streaks, no score. Turning self-regulation into a performance to be
            // graded is exactly how a calming tool becomes another source of pressure.
            text = "However you feel now is fine.",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(onClick = onExit, modifier = Modifier.padding(top = 12.dp)) {
            Text("Close")
        }
    }
}

private const val MIN_SCALE = 0.35f
private const val DEFAULT_SESSION_MS = 90_000L
