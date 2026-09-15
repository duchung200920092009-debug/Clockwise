package com.zenpulse.wear.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.zenpulse.wear.data.ZenPulseSettings
import com.zenpulse.wear.domain.StressSensitivity

@Composable
fun SettingsScreen(
    settings: ZenPulseSettings,
    onAlertsChanged: (Boolean) -> Unit,
    onPassiveChanged: (Boolean) -> Unit,
    onLoggingChanged: (Boolean) -> Unit,
    onSensitivityChanged: (StressSensitivity) -> Unit,
    onResetBaseline: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SettingToggle(
                label = "Nudges",
                secondary = "Buzz when your body looks activated",
                checked = settings.alertsEnabled,
                onCheckedChange = onAlertsChanged,
            )
        }

        item {
            SettingToggle(
                label = "Background learning",
                secondary = "Keeps your baseline fresh. Low battery cost.",
                checked = settings.passiveMonitoringEnabled,
                onCheckedChange = onPassiveChanged,
            )
        }

        item {
            SettingToggle(
                label = "Record raw data",
                secondary = "For research. Writes a detailed trace to the watch.",
                checked = settings.sessionLoggingEnabled,
                onCheckedChange = onLoggingChanged,
            )
        }

        item {
            Text(
                text = "How easily it speaks up",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        StressSensitivity.entries.forEach { sensitivity ->
            item {
                Chip(
                    onClick = { onSensitivityChanged(sensitivity) },
                    label = { Text(sensitivity.label()) },
                    secondaryLabel = { Text(sensitivity.description()) },
                    colors = if (settings.sensitivity == sensitivity) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Chip(
                onClick = onResetBaseline,
                label = { Text("Relearn my baseline") },
                secondaryLabel = { Text("If your normal has changed") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        item {
            Chip(
                onClick = onClearHistory,
                label = { Text("Delete history") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            // Deliberately the last word in the app. A wellbeing tool should be explicit about
            // what it is not, especially to a user who might otherwise wait for it to help.
            Text(
                text = "ZenPulse is a wellbeing tool, not a medical device, and not emergency " +
                    "support. If you're in crisis, please reach a person you trust.",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    secondary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(label) },
        secondaryLabel = { Text(secondary) },
        toggleControl = {
            Icon(
                imageVector = ToggleChipDefaults.switchIcon(checked),
                contentDescription = if (checked) "On" else "Off",
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun StressSensitivity.label(): String = when (this) {
    StressSensitivity.LOW -> "Rarely"
    StressSensitivity.BALANCED -> "Balanced"
    StressSensitivity.HIGH -> "Readily"
}

private fun StressSensitivity.description(): String = when (this) {
    StressSensitivity.LOW -> "Only when it's clear"
    StressSensitivity.BALANCED -> "Recommended"
    StressSensitivity.HIGH -> "Earlier, more false alarms"
}
