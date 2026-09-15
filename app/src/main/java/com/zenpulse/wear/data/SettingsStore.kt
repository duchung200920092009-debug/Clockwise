package com.zenpulse.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zenpulse.wear.domain.BaselineSnapshot
import com.zenpulse.wear.domain.BreathingPattern
import com.zenpulse.wear.domain.StressSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Everything the user can change about how ZenPulse behaves. */
data class ZenPulseSettings(
    val onboardingComplete: Boolean = false,
    val sensitivity: StressSensitivity = StressSensitivity.BALANCED,
    val alertsEnabled: Boolean = true,
    /** Background (passive) monitoring — the battery-friendly always-on mode. */
    val passiveMonitoringEnabled: Boolean = true,
    /**
     * Raw CSV session logging. Off by default: it is a research/debug feature, and writing a
     * continuous physiological trace to disk is not something to switch on for someone without
     * them choosing it.
     */
    val sessionLoggingEnabled: Boolean = false,
    val preferredPatternId: String = BreathingPattern.COHERENT.id,
    /** Milliseconds since epoch until which alerts stay silent. */
    val alertsSnoozedUntilMs: Long = 0L,
) {
    val preferredPattern: BreathingPattern get() = BreathingPattern.byId(preferredPatternId)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zenpulse_settings")

/**
 * Persists user settings and the learned [BaselineSnapshot].
 *
 * The baseline lives here rather than in a database because it is a handful of numbers that must
 * survive reboots — and because losing it is genuinely costly to the user: it means another ten
 * minutes of "still learning" before the app can say anything useful again.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<ZenPulseSettings> = context.dataStore.data.map { prefs ->
        ZenPulseSettings(
            onboardingComplete = prefs[KEY_ONBOARDING_COMPLETE] ?: false,
            sensitivity = prefs[KEY_SENSITIVITY]?.let { name ->
                runCatching { StressSensitivity.valueOf(name) }.getOrNull()
            } ?: StressSensitivity.BALANCED,
            alertsEnabled = prefs[KEY_ALERTS_ENABLED] ?: true,
            passiveMonitoringEnabled = prefs[KEY_PASSIVE_ENABLED] ?: true,
            sessionLoggingEnabled = prefs[KEY_SESSION_LOGGING] ?: false,
            preferredPatternId = prefs[KEY_PATTERN_ID] ?: BreathingPattern.COHERENT.id,
            alertsSnoozedUntilMs = prefs[KEY_SNOOZED_UNTIL] ?: 0L,
        )
    }

    suspend fun current(): ZenPulseSettings = settings.first()

    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[KEY_ONBOARDING_COMPLETE] = complete }

    suspend fun setSensitivity(sensitivity: StressSensitivity) =
        edit { it[KEY_SENSITIVITY] = sensitivity.name }

    suspend fun setAlertsEnabled(enabled: Boolean) = edit { it[KEY_ALERTS_ENABLED] = enabled }

    suspend fun setPassiveMonitoringEnabled(enabled: Boolean) =
        edit { it[KEY_PASSIVE_ENABLED] = enabled }

    suspend fun setSessionLoggingEnabled(enabled: Boolean) =
        edit { it[KEY_SESSION_LOGGING] = enabled }

    suspend fun setPreferredPattern(patternId: String) = edit { it[KEY_PATTERN_ID] = patternId }

    /** Silence alerts for a while without turning them off entirely. */
    suspend fun snoozeAlertsUntil(untilMs: Long) = edit { it[KEY_SNOOZED_UNTIL] = untilMs }

    // --- Baseline persistence -------------------------------------------------------------

    val baseline: Flow<BaselineSnapshot> = context.dataStore.data.map { prefs ->
        BaselineSnapshot(
            hrMean = prefs[KEY_BASELINE_HR_MEAN] ?: 0.0,
            hrVariance = prefs[KEY_BASELINE_HR_VAR] ?: 0.0,
            hrvMean = prefs[KEY_BASELINE_HRV_MEAN] ?: 0.0,
            hrvVariance = prefs[KEY_BASELINE_HRV_VAR] ?: 0.0,
            sampleCount = prefs[KEY_BASELINE_COUNT] ?: 0,
            updatedAtMs = prefs[KEY_BASELINE_UPDATED_AT] ?: 0L,
        )
    }

    suspend fun loadBaseline(): BaselineSnapshot = baseline.first()

    suspend fun saveBaseline(snapshot: BaselineSnapshot) = edit { prefs ->
        prefs[KEY_BASELINE_HR_MEAN] = snapshot.hrMean
        prefs[KEY_BASELINE_HR_VAR] = snapshot.hrVariance
        prefs[KEY_BASELINE_HRV_MEAN] = snapshot.hrvMean
        prefs[KEY_BASELINE_HRV_VAR] = snapshot.hrvVariance
        prefs[KEY_BASELINE_COUNT] = snapshot.sampleCount
        prefs[KEY_BASELINE_UPDATED_AT] = snapshot.updatedAtMs
    }

    /** "My normal has changed" — start learning the baseline from scratch. */
    suspend fun clearBaseline() = saveBaseline(BaselineSnapshot())

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_PASSIVE_ENABLED = booleanPreferencesKey("passive_enabled")
        val KEY_SESSION_LOGGING = booleanPreferencesKey("session_logging")
        val KEY_PATTERN_ID = stringPreferencesKey("pattern_id")
        val KEY_SNOOZED_UNTIL = longPreferencesKey("alerts_snoozed_until")

        val KEY_BASELINE_HR_MEAN = doublePreferencesKey("baseline_hr_mean")
        val KEY_BASELINE_HR_VAR = doublePreferencesKey("baseline_hr_var")
        val KEY_BASELINE_HRV_MEAN = doublePreferencesKey("baseline_hrv_mean")
        val KEY_BASELINE_HRV_VAR = doublePreferencesKey("baseline_hrv_var")
        val KEY_BASELINE_COUNT = intPreferencesKey("baseline_count")
        val KEY_BASELINE_UPDATED_AT = longPreferencesKey("baseline_updated_at")
    }
}
