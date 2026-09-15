package com.zenpulse.wear.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zenpulse.wear.AppContainer
import com.zenpulse.wear.ZenPulseApplication
import com.zenpulse.wear.data.ZenPulseSettings
import com.zenpulse.wear.monitor.MonitorState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** The monitor is application-scoped, so this survives the screen being destroyed. */
    val monitorState: StateFlow<MonitorState> = container.stressMonitor.state

    val settings: StateFlow<ZenPulseSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZenPulseSettings())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as ZenPulseApplication
                HomeViewModel(app.container)
            }
        }
    }
}
