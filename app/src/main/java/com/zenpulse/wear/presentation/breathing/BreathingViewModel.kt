package com.zenpulse.wear.presentation.breathing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zenpulse.wear.AppContainer
import com.zenpulse.wear.ZenPulseApplication
import kotlinx.coroutines.launch

class BreathingViewModel(private val container: AppContainer) : ViewModel() {

    fun onPhaseChange() = container.notifier.tickHaptic()

    /**
     * Links a completed exercise back to the episode that prompted it, so history can show
     * whether the intervention was actually used — the one piece of evidence that says whether
     * any of this helps in practice.
     */
    fun onCompleted() {
        viewModelScope.launch { container.episodeStore.markBreathingCompletedForRecent() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as ZenPulseApplication
                BreathingViewModel(app.container)
            }
        }
    }
}
