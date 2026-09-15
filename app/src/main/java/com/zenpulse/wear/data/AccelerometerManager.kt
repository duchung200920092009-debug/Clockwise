package com.zenpulse.wear.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One raw accelerometer reading (gravity-inclusive, device axes, m/s^2). */
data class AccelSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long,
) {
    /** Cheap motion-intensity signal: vector magnitude. Near 9.8 at rest (gravity only). */
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

/**
 * Thin wrapper around the platform accelerometer (`Sensor.TYPE_ACCELEROMETER`), exposed as a
 * cold [Flow] the same way [HealthServicesManager.heartRateFlow] is: registered on collection,
 * unregistered on cancellation.
 *
 * Why this matters for stress detection: a raised heart rate during a workout is not the same
 * signal as a raised heart rate at rest. Week 4's rule-based detector needs a motion signal to
 * tell those apart, so we start collecting it now even though nothing consumes it yet beyond
 * logging.
 *
 * Uses `SENSOR_DELAY_GAME` (~50 Hz), which stays under the 200 Hz threshold Android 12+ gates
 * behind the `HIGH_SAMPLING_RATE_SENSORS` permission — so no extra manifest permission needed.
 */
class AccelerometerManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** False on hardware with no accelerometer (shouldn't happen on a real Galaxy Watch). */
    val isAvailable: Boolean get() = accelerometer != null

    fun accelFlow(): Flow<AccelSample> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    AccelSample(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampNanos = event.timestamp,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Not surfaced yet — accelerometer accuracy rarely affects a motion-intensity proxy.
            }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
