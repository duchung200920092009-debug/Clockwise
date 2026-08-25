package com.zenpulse.wear.data

import android.content.Context
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thin wrapper around Health Services [androidx.health.services.client.MeasureClient].
 *
 * Week 1 scope: real-time (active) heart-rate streaming only. IBI + accelerometer
 * are added in Week 2. Passive/background measurement (battery-friendly) comes in Week 6.
 *
 * The stream is exposed as a cold [Flow]: the underlying sensor callback is registered when a
 * collector starts and unregistered when it stops (awaitClose), so we never leak a measurement
 * session or drain the battery while nothing is listening.
 */
class HealthServicesManager(context: Context) {

    private val measureClient = HealthServices.getClient(context).measureClient

    /**
     * True only if this watch can actively measure heart rate through Health Services.
     * On a Galaxy Watch this should be true; on an emulator without sensors it is often false.
     */
    suspend fun hasHeartRateCapability(): Boolean {
        val capabilities = measureClient.getCapabilitiesAsync().await()
        return DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
    }

    /**
     * Emits [HeartRateMessage]s until the collector cancels.
     *
     * Two kinds of message arrive from the sensor:
     *  - [HeartRateMessage.Availability] — whether the PPG sensor currently has skin contact / a
     *    usable signal. Surface this in the UI; a raw BPM means nothing while availability is
     *    UNAVAILABLE (watch off wrist, loose band).
     *  - [HeartRateMessage.Sample] — one or more BPM samples in this batch.
     */
    fun heartRateFlow(): Flow<HeartRateMessage> = callbackFlow {
        val dataType: DeltaDataType<Double, *> = DataType.HEART_RATE_BPM

        val callback = object : MeasureCallback {
            override fun onRegistered() {
                // Registration acknowledged by Health Services. Nothing to do yet.
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                close(throwable)
            }

            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                if (availability is DataTypeAvailability) {
                    trySend(HeartRateMessage.Availability(availability))
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val samples = data.getData(DataType.HEART_RATE_BPM)
                if (samples.isNotEmpty()) {
                    trySend(HeartRateMessage.Sample(samples.map { it.value }))
                }
            }
        }

        measureClient.registerMeasureCallback(dataType, callback)

        awaitClose {
            // Suspending unregister isn't allowed inside awaitClose; fire-and-forget the future.
            measureClient.unregisterMeasureCallbackAsync(dataType, callback)
        }
    }
}

/** A single event coming off the heart-rate sensor stream. */
sealed interface HeartRateMessage {
    /** Sensor signal quality changed (skin contact acquired/lost). */
    data class Availability(val availability: DataTypeAvailability) : HeartRateMessage

    /** One batch of BPM samples. Usually a single value, occasionally several. */
    data class Sample(val bpm: List<Double>) : HeartRateMessage
}
