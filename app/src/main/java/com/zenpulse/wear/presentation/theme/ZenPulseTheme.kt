package com.zenpulse.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.zenpulse.wear.domain.StressLevel

/**
 * Colours for the stress scale.
 *
 * Note what is deliberately missing: red. The top of this scale is amber, not alarm-red, because
 * the person reading it may already be anxious and a red screen reads as "something is wrong with
 * you". The palette climbs in warmth and saturation so the levels stay clearly distinguishable —
 * including for the most common forms of colour blindness, since it never relies on a red/green
 * distinction — without ever looking like an emergency.
 */
object StressColors {
    val Calm = Color(0xFF4DB6AC)
    val Rising = Color(0xFF7986CB)
    val Elevated = Color(0xFFFFB74D)
    val High = Color(0xFFFF8A65)

    /** Used when the app cannot assess: moving, no signal, still learning. */
    val Unknown = Color(0xFF90A4AE)

    fun forLevel(level: StressLevel): Color = when (level) {
        StressLevel.CALM -> Calm
        StressLevel.RISING -> Rising
        StressLevel.ELEVATED -> Elevated
        StressLevel.HIGH -> High
    }
}

private val ZenPulseColorPalette = Colors(
    primary = Color(0xFF4DB6AC),
    primaryVariant = Color(0xFF00867D),
    secondary = Color(0xFF7986CB),
    secondaryVariant = Color(0xFF49599A),
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    error = Color(0xFFFF8A65),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black,
)

@Composable
fun ZenPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = ZenPulseColorPalette, content = content)
}
