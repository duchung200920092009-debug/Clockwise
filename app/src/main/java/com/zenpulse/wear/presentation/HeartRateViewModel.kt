package com.zenpulse.wear.presentation

import android.app.Application
import android.os.SystemClock
import androidx.health.services.client.data.DataTypeAvailability
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenpulse.wear.data.AccelSample
import com.zenpulse.wear.data.AccelerometerManager
import com.zenpulse.wear.data.HealthServicesManager
import com.zenpulse.wear.data.HeartRateMessage
import com.zenpulse.wear.data.SessionLogger
import com.zenpulse.wear.data.SessionRow
import com.zenpulse.wear.domain.HrvEstimator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the heart-rate screen renders. */
data class HeartRateUiState(
    val capabilityChecked: Boolean = false,
    val hasCapability: Boolean = true,
    val bpm: Double? = null,
    val availability: DataTypeAvailability = DataTypeAvailability.UNKNOWN,
    val measuring: Boolean = false,
    /** RMSSD-style proxy in ms, derived from BPM — see [HrvEstimator]'s doc for the caveat. */
    val hrvProxyMs: Double? = null,
    /** Accelerometer vector magnitude — a cheap "are you moving right now" signal. */
    val accelMagnitude: Float? = null,
    /** Name of the CSV file the current session is logging to, or null if not logging. */
    val loggingFileName: String? = null,
)

class HeartRateViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** How often a merged row is written to the session log. 5 Hz is plenty for offline HRV/motion analysis without bloating the file. */
        private const val LOG_SAMPLE_INTERVAL_MS = 200L
    }

    private val healthServicesManager = HealthServicesManager(app)
    private val accelerometerManager = AccelerometerManager(app)
    private val hrvEstimator = HrvEstimator()
    private val sessionLogger = SessionLogger(app)

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()

    private var measureJob: Job? = null
    private var accelJob: Job? = null
    private var loggingJob: Job? = null

    /** Latest accelerometer sample, read by the logging loop; heart rate comes from uiState. */
    @Volatile
    private var latestAccel: AccelSample? = null

    fun checkCapability() {
        viewModelScope.launch {
            val hasCapability = runCatching { healthServicesManager.hasHeartRateCapability() }
                .getOrDefault(false)
            _uiState.update { it.copy(capabilityChecked = true, hasCapability = hasCapability) }
        }
    }

    /**
     * Start streaming heart rate + accelerometer and logging both to a new session file.
     * Call only after BODY_SENSORS is granted. Calling again while running is a no-op.
     */
    fun startMeasuring() {
        if (measureJob?.isActive == true) return

        hrvEstimator.reset()
        latestAccel = null
        sessionLogger.start(viewModelScope)
        _uiState.update {
            it.copy(measuring = true, loggingFileName = sessionLogger.sessionFile?.name)
        }

        val sessionStartElapsed = SystemClock.elapsedRealtime()

        measureJob = viewModelScope.launch {
            healthServicesManager.heartRateFlow().collect { message ->
                when (message) {
                    is HeartRateMessage.Availability ->
                        _uiState.update { it.copy(availability = message.availability) }
                    is HeartRateMessage.Sample -> {
                        val bpm = message.bpm.lastOrNull()
                        val hrvProxyMs = bpm?.let { hrvEstimator.addSample(it) }
                        _uiState.update { it.copy(bpm = bpm, hrvProxyMs = hrvProxyMs) }
                    }
                }
            }
        }

        accelJob = viewModelScope.launch {
            if (!accelerometerManager.isAvailable) return@launch
            accelerometerManager.accelFlow().collect { sample ->
                latestAccel = sample
                _uiState.update { it.copy(accelMagnitude = sample.magnitude) }
            }
        }

        // Merges the latest HR/HRV state with the latest accel sample into one row, on a fixed
        // cadence independent of either sensor's own rate (accel arrives much faster than HR).
        loggingJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = _uiState.value
                val accel = latestAccel
                sessionLogger.log(
                    SessionRow(
                        elapsedMs = SystemClock.elapsedRealtime() - sessionStartElapsed,
                        bpm = snapshot.bpm,
                        availability = snapshot.availability.name,
                        hrvProxyMs = snapshot.hrvProxyMs,
                        accelX = accel?.x,
                        accelY = accel?.y,
                        accelZ = accel?.z,
                    )
                )
                delay(LOG_SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun stopMeasuring() {
        measureJob?.cancel()
        measureJob = null
        accelJob?.cancel()
        accelJob = null
        loggingJob?.cancel()
        loggingJob = null
        sessionLogger.stop()
        _uiState.update { it.copy(measuring = false, loggingFileName = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopMeasuring()
    }
}
