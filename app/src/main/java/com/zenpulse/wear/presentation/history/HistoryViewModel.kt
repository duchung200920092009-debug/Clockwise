package com.zenpulse.wear.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zenpulse.wear.AppContainer
import com.zenpulse.wear.ZenPulseApplication
import com.zenpulse.wear.domain.StressEpisode
import com.zenpulse.wear.domain.UserFeedback
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val episodes: StateFlow<List<StressEpisode>> = container.episodeStore.episodes

    fun setFeedback(episodeId: String, feedback: UserFeedback) = viewModelScope.launch {
        container.episodeStore.setFeedback(episodeId, feedback)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as ZenPulseApplication
                HistoryViewModel(app.container)
            }
        }
    }
}
