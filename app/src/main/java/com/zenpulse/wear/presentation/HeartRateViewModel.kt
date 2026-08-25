package com.zenpulse.wear.presentation

import android.app.Application
import androidx.health.services.client.data.DataTypeAvailability
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenpulse.wear.data.HealthServicesManager
import com.zenpulse.wear.data.HeartRateMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the heart-rate screen renders. */
data class HeartRateUiState(
    val capabilityChecked: Boolean = false,
    val hasCapability: Boolean = true,
    val bpm: Double? = null,
    val availability: DataTypeAvailability = DataTypeAvailability.UNKNOWN,
    val measuring: Boolean = false,
)

class HeartRateViewModel(app: Application) : AndroidViewModel(app) {

    private val healthServicesManager = HealthServicesManager(app)

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()

    private var measureJob: Job? = null

    fun checkCapability() {
        viewModelScope.launch {
            val hasCapability = runCatching { healthServicesManager.hasHeartRateCapability() }
                .getOrDefault(false)
            _uiState.update { it.copy(capabilityChecked = true, hasCapability = hasCapability) }
        }
    }

    /**
     * Start streaming heart rate. Call only after BODY_SENSORS is granted.
     * Collection is tied to [measureJob]; calling again while running is a no-op.
     */
    fun startMeasuring() {
        if (measureJob?.isActive == true) return
        _uiState.update { it.copy(measuring = true) }
        measureJob = viewModelScope.launch {
            healthServicesManager.heartRateFlow().collect { message ->
                when (message) {
                    is HeartRateMessage.Availability ->
                        _uiState.update { it.copy(availability = message.availability) }
                    is HeartRateMessage.Sample ->
                        _uiState.update { it.copy(bpm = message.bpm.lastOrNull()) }
                }
            }
        }
    }

    fun stopMeasuring() {
        measureJob?.cancel()
        measureJob = null
        _uiState.update { it.copy(measuring = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopMeasuring()
    }
}
