package com.zenpulse.wear.data

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * Battery-friendly background heart-rate monitoring.
 *
 * Active measurement (the `MeasureClient` used during a session) keeps the PPG sensor running
 * continuously and would flatten a watch battery in hours — unusable for something meant to be
 * worn all day. Passive monitoring instead piggybacks on the readings the system already takes,
 * at a much lower rate and a fraction of the power.
 *
 * The trade-off is resolution: passive samples are sparse and carry no motion context, so they
 * are used only to keep the personal baseline fresh, never to trigger an alert. See
 * [com.zenpulse.wear.monitor.StressMonitor.offerPassiveSample].
 */
class PassiveMonitorManager(context: Context) {

    private val passiveClient = HealthServices.getClient(context).passiveMonitoringClient

    suspend fun isSupported(): Boolean = runCatching {
        val capabilities = passiveClient.getCapabilitiesAsync().await()
        DataType.HEART_RATE_BPM in capabilities.supportedDataTypesPassiveMonitoring
    }.getOrDefault(false)

    suspend fun register() {
        runCatching {
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()
            passiveClient.setPassiveListenerServiceAsync(
                ZenPulsePassiveListenerService::class.java,
                config,
            ).await()
        }.onFailure { Log.w(TAG, "Could not register passive monitoring: ${it.message}") }
    }

    suspend fun unregister() {
        runCatching { passiveClient.clearPassiveListenerServiceAsync().await() }
            .onFailure { Log.w(TAG, "Could not clear passive monitoring: ${it.message}") }
    }

    private companion object {
        const val TAG = "PassiveMonitorManager"
    }
}
