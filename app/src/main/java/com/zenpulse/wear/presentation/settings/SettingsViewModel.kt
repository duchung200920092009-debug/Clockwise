package com.zenpulse.wear.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zenpulse.wear.AppContainer
import com.zenpulse.wear.ZenPulseApplication
import com.zenpulse.wear.data.ZenPulseSettings
import com.zenpulse.wear.domain.StressSensitivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<ZenPulseSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZenPulseSettings())

    fun markOnboardingComplete() = viewModelScope.launch {
        container.settingsStore.setOnboardingComplete(true)
    }

    fun setAlertsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsStore.setAlertsEnabled(enabled)
    }

    /** Registering/unregistering the passive listener has to follow the setting, not just store it. */
    fun setPassiveMonitoringEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsStore.setPassiveMonitoringEnabled(enabled)
        if (enabled && container.passiveMonitorManager.isSupported()) {
            container.passiveMonitorManager.register()
        } else {
            container.passiveMonitorManager.unregister()
        }
    }

    fun setSessionLoggingEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsStore.setSessionLoggingEnabled(enabled)
    }

    fun setSensitivity(sensitivity: StressSensitivity) = viewModelScope.launch {
        container.settingsStore.setSensitivity(sensitivity)
        // Apply live so the user can feel the change without restarting monitoring.
        container.stressMonitor.updateSensitivity(sensitivity)
    }

    /** "My normal has changed" — throw the baseline away and learn it again from scratch. */
    fun resetBaseline() = viewModelScope.launch {
        container.settingsStore.clearBaseline()
    }

    fun clearHistory() = viewModelScope.launch {
        container.episodeStore.clear()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as ZenPulseApplication
                SettingsViewModel(app.container)
            }
        }
    }
}
