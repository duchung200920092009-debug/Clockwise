package com.zenpulse.wear.monitor

import androidx.health.services.client.data.DataTypeAvailability
import com.zenpulse.wear.data.AccelSample
import com.zenpulse.wear.data.AccelerometerManager
import com.zenpulse.wear.data.EpisodeStore
import com.zenpulse.wear.data.HealthServicesManager
import com.zenpulse.wear.data.HeartRateMessage
import com.zenpulse.wear.data.PhoneSyncManager
import com.zenpulse.wear.data.SessionLogger
import com.zenpulse.wear.data.SessionRow
import com.zenpulse.wear.data.SettingsStore
import com.zenpulse.wear.domain.BaselineSnapshot
import com.zenpulse.wear.domain.BaselineTracker
import com.zenpulse.wear.domain.BreathingPattern
import com.zenpulse.wear.domain.HrvEstimator
import com.zenpulse.wear.domain.SensorWindow
import com.zenpulse.wear.domain.StressAssessment
import com.zenpulse.wear.domain.StressDetector
import com.zenpulse.wear.domain.StressEpisode
import com.zenpulse.wear.domain.StressLevel
import com.zenpulse.wear.domain.StressSensitivity
import com.zenpulse.wear.domain.WindowAggregator
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the UI needs to render the current monitoring state. */
data class MonitorState(
    val running: Boolean = false,
    val bpm: Double? = null,
    val hrvProxyMs: Double? = null,
    val motionIntensity: Double? = null,
    val availability: DataTypeAvailability = DataTypeAvailability.UNKNOWN,
    val assessment: StressAssessment = StressAssessment.Learning(0f),
    val baseline: BaselineSnapshot = BaselineSnapshot(),
    /** Non-null while an actionable episode is in progress. */
    val episodeStartedAtMs: Long? = null,
)

/** Emitted when the app wants to gently get the user's attention. */
data class StressAlert(
    val level: StressLevel,
    val suggestedPattern: BreathingPattern,
    val atMs: Long,
)

/**
 * The runtime core: owns the sensors, turns their output into windows, keeps the personal
 * baseline current, runs detection, records episodes, and decides when to speak up.
 *
 * Deliberately a single application-scoped object rather than something a screen owns. The watch
 * display turns off constantly, and monitoring that stopped every time the user dropped their
 * wrist would be useless for catching an episode — so the lifecycle belongs to the app and its
 * foreground service, not to any UI.
 */
class StressMonitor(
    private val healthServices: HealthServicesManager,
    private val accelerometer: AccelerometerManager,
    private val settingsStore: SettingsStore,
    private val episodeStore: EpisodeStore,
    private val phoneSync: PhoneSyncManager,
    private val sessionLogger: SessionLogger,
    private val scope: CoroutineScope,
) {

    private val hrvEstimator = HrvEstimator()
    private val aggregator = WindowAggregator()
    private val baselineTracker = BaselineTracker()
    private val detector = StressDetector()

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    /** replay = 0: an alert is a moment, not a state — a late subscriber shouldn't get an old one. */
    private val _alerts = MutableSharedFlow<StressAlert>(replay = 0, extraBufferCapacity = 4)
    val alerts: SharedFlow<StressAlert> = _alerts.asSharedFlow()

    /** One parent job; cancelling it tears down every sensor collector as a child. */
    private var monitorJob: Job? = null

    @Volatile
    private var latestAccel: AccelSample? = null

    private var openEpisode: OpenEpisode? = null
    private var calmWindowsSinceEpisode = 0
    private var acceptedSinceBaselineSave = 0
    private var lastAlertAtMs = 0L

    val isRunning: Boolean get() = monitorJob?.isActive == true

    fun start() {
        if (isRunning) return

        monitorJob = scope.launch {
            val settings = settingsStore.current()

            // Restore the learned baseline before the first window is assessed — otherwise every
            // reboot would cost the user another ten minutes of "still learning".
            baselineTracker.restore(settingsStore.loadBaseline())
            detector.sensitivity = settings.sensitivity
            detector.reset()
            hrvEstimator.reset()
            aggregator.reset()
            latestAccel = null

            _state.update {
                it.copy(running = true, baseline = baselineTracker.snapshot)
            }

            if (settings.sessionLoggingEnabled) {
                sessionLogger.start(scope)
            }

            launch { collectHeartRate() }
            launch { collectMotion() }
            launch { runTickLoop(logRawSamples = settings.sessionLoggingEnabled) }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        sessionLogger.stop()
        closeEpisodeIfOpen(System.currentTimeMillis())
        scope.launch { settingsStore.saveBaseline(baselineTracker.snapshot) }
        _state.update { it.copy(running = false, episodeStartedAtMs = null) }
    }

    /** Apply a sensitivity change without restarting monitoring. */
    fun updateSensitivity(sensitivity: StressSensitivity) {
        detector.sensitivity = sensitivity
    }

    /**
     * Fold a heart-rate sample gathered by passive (background) monitoring into the baseline.
     *
     * Passive samples train the baseline but never drive detection: they arrive without any
     * accompanying motion reading, and calling someone stressed without knowing whether they are
     * simply walking is exactly the false positive this app is built to avoid.
     */
    fun offerPassiveSample(bpm: Double, timestampMs: Long) {
        val accepted = baselineTracker.offer(
            SensorWindow(timestampMs = timestampMs, bpm = bpm, hrvProxyMs = null, motionIntensity = null)
        )
        if (accepted) {
            _state.update { it.copy(baseline = baselineTracker.snapshot) }
            scope.launch { settingsStore.saveBaseline(baselineTracker.snapshot) }
        }
    }

    private suspend fun collectHeartRate() {
        healthServices.heartRateFlow().collect { message ->
            when (message) {
                is HeartRateMessage.Availability ->
                    _state.update { it.copy(availability = message.availability) }

                is HeartRateMessage.Sample -> {
                    val bpm = message.bpm.lastOrNull() ?: return@collect
                    aggregator.addHeartRate(bpm)

                    // Exactly one call per sample: feeding the estimator twice would fabricate a
                    // zero-difference interval and silently flatten the variability signal.
                    val hrvProxy = hrvEstimator.addSample(bpm)
                    hrvProxy?.let { aggregator.addHrv(it) }

                    _state.update { it.copy(bpm = bpm, hrvProxyMs = hrvProxy ?: it.hrvProxyMs) }
                }
            }
        }
    }

    private suspend fun collectMotion() {
        if (!accelerometer.isAvailable) return
        accelerometer.accelFlow().collect { sample ->
            latestAccel = sample
            aggregator.addMotion(sample.magnitude.toDouble())
        }
    }

    private suspend fun runTickLoop(logRawSamples: Boolean) {
        val sessionStart = System.currentTimeMillis()
        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()

            if (logRawSamples) {
                val snapshot = _state.value
                val accel = latestAccel
                sessionLogger.log(
                    SessionRow(
                        elapsedMs = now - sessionStart,
                        bpm = snapshot.bpm,
                        availability = snapshot.availability.name,
                        hrvProxyMs = snapshot.hrvProxyMs,
                        accelX = accel?.x,
                        accelY = accel?.y,
                        accelZ = accel?.z,
                    )
                )
            }

            aggregator.tick(now)?.let { processWindow(it) }
            delay(TICK_INTERVAL_MS)
        }
    }

    private suspend fun processWindow(window: SensorWindow) {
        if (baselineTracker.offer(window)) {
            acceptedSinceBaselineSave++
            // Batched rather than per-window: DataStore writes hit flash and this runs all day.
            if (acceptedSinceBaselineSave >= BASELINE_SAVE_EVERY_N_WINDOWS) {
                acceptedSinceBaselineSave = 0
                settingsStore.saveBaseline(baselineTracker.snapshot)
            }
        }

        val assessment = detector.update(window, baselineTracker.snapshot)
        _state.update {
            it.copy(
                motionIntensity = window.motionIntensity,
                assessment = assessment,
                baseline = baselineTracker.snapshot,
            )
        }

        if (assessment is StressAssessment.Assessed) {
            trackEpisode(window, assessment)
        }
    }

    private suspend fun trackEpisode(window: SensorWindow, assessment: StressAssessment.Assessed) {
        if (!assessment.level.isActionable) {
            if (openEpisode != null) {
                calmWindowsSinceEpisode++
                if (calmWindowsSinceEpisode >= WINDOWS_TO_CLOSE_EPISODE) {
                    closeEpisodeIfOpen(window.timestampMs)
                }
            }
            return
        }

        calmWindowsSinceEpisode = 0
        val existing = openEpisode
        if (existing == null) {
            val episode = OpenEpisode(window.timestampMs)
            openEpisode = episode
            _state.update { it.copy(episodeStartedAtMs = episode.startedAtMs) }
            episode.record(assessment.level, assessment.score, window.bpm, window.hrvProxyMs)
            maybeAlert(assessment.level, window.timestampMs)
            return
        }

        // A second nudge only when the episode genuinely deepens to HIGH, not on every window.
        val wasHigh = existing.peakLevel == StressLevel.HIGH
        existing.record(assessment.level, assessment.score, window.bpm, window.hrvProxyMs)
        if (!wasHigh && existing.peakLevel == StressLevel.HIGH) {
            maybeAlert(StressLevel.HIGH, window.timestampMs)
        }
    }

    private fun closeEpisodeIfOpen(endedAtMs: Long) {
        val episode = openEpisode ?: return
        openEpisode = null
        calmWindowsSinceEpisode = 0
        _state.update { it.copy(episodeStartedAtMs = null) }

        val record = episode.toEpisode(endedAtMs)
        // Very short blips aren't episodes; recording them would bury the real ones in noise.
        if (record.durationMs < MIN_EPISODE_DURATION_MS) return

        scope.launch {
            episodeStore.add(record)
            phoneSync.syncEpisodes(episodeStore.toJson())
        }
    }

    private suspend fun maybeAlert(level: StressLevel, nowMs: Long) {
        val settings = settingsStore.current()
        if (!settings.alertsEnabled) return
        if (nowMs < settings.alertsSnoozedUntilMs) return
        if (nowMs - lastAlertAtMs < MIN_ALERT_INTERVAL_MS) return

        lastAlertAtMs = nowMs
        _alerts.emit(
            StressAlert(
                level = level,
                suggestedPattern = BreathingPattern.forLevel(level),
                atMs = nowMs,
            )
        )
    }

    /** Mutable accumulator for an episode that is still in progress. */
    private class OpenEpisode(val startedAtMs: Long) {
        var peakLevel: StressLevel = StressLevel.CALM
            private set

        private var peakScore = 0.0
        private var bpmSum = 0.0
        private var bpmCount = 0
        private var hrvSum = 0.0
        private var hrvCount = 0

        fun record(level: StressLevel, score: Double, bpm: Double?, hrv: Double?) {
            if (level.ordinal > peakLevel.ordinal) peakLevel = level
            if (score > peakScore) peakScore = score
            bpm?.let { bpmSum += it; bpmCount++ }
            hrv?.let { hrvSum += it; hrvCount++ }
        }

        fun toEpisode(endedAtMs: Long) = StressEpisode(
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            peakLevel = peakLevel,
            peakScore = peakScore,
            meanBpm = if (bpmCount > 0) bpmSum / bpmCount else null,
            meanHrvMs = if (hrvCount > 0) hrvSum / hrvCount else null,
        )
    }

    private companion object {
        const val TICK_INTERVAL_MS = 200L
        const val BASELINE_SAVE_EVERY_N_WINDOWS = 10

        /** Two consecutive calm windows (~20s) end an episode. */
        const val WINDOWS_TO_CLOSE_EPISODE = 2
        const val MIN_EPISODE_DURATION_MS = 30_000L

        /**
         * Never more than one nudge per 20 minutes. An app that buzzes repeatedly at someone who
         * is already activated becomes another stressor — the exact opposite of the point.
         */
        const val MIN_ALERT_INTERVAL_MS = 20 * 60 * 1000L
    }
}
