package com.zenpulse.wear

import android.app.Application
import android.content.Context
import com.zenpulse.wear.data.AccelerometerManager
import com.zenpulse.wear.data.EpisodeStore
import com.zenpulse.wear.data.HealthServicesManager
import com.zenpulse.wear.data.PassiveMonitorManager
import com.zenpulse.wear.data.PhoneSyncManager
import com.zenpulse.wear.data.SessionLogger
import com.zenpulse.wear.data.SettingsStore
import com.zenpulse.wear.monitor.StressMonitor
import com.zenpulse.wear.notification.StressNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual dependency container.
 *
 * No Hilt or Dagger on purpose: this app has roughly a dozen singletons and one graph, and an
 * annotation processor would add build complexity and time for nothing. If the graph ever gets
 * genuinely complicated, that is the moment to reconsider — not before.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Application-scoped, because monitoring must outlive every screen. [SupervisorJob] so one
     * failing collector (a sensor dropping out) can't take the whole graph down with it.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore = SettingsStore(appContext)
    val episodeStore = EpisodeStore(appContext)
    val notifier = StressNotifier(appContext)
    val passiveMonitorManager = PassiveMonitorManager(appContext)

    val stressMonitor = StressMonitor(
        healthServices = HealthServicesManager(appContext),
        accelerometer = AccelerometerManager(appContext),
        settingsStore = settingsStore,
        episodeStore = episodeStore,
        phoneSync = PhoneSyncManager(appContext),
        sessionLogger = SessionLogger(appContext),
        scope = applicationScope,
    )
}

class ZenPulseApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()

        container.applicationScope.launch {
            container.episodeStore.load()

            // Alerts are raised by the monitor and rendered here, so a nudge still reaches the
            // user when no screen is open — which is precisely when it matters most.
            launch {
                container.stressMonitor.alerts.collect { alert ->
                    container.notifier.notifyStress(alert)
                }
            }

            // Keep passive baseline learning registered according to the user's setting.
            val settings = container.settingsStore.settings.first()
            if (settings.passiveMonitoringEnabled && container.passiveMonitorManager.isSupported()) {
                container.passiveMonitorManager.register()
            }
        }
    }
}
