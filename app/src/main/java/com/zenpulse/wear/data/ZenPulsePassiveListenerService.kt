package com.zenpulse.wear.data

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.zenpulse.wear.ZenPulseApplication

/**
 * Receives the sparse heart-rate samples Health Services delivers while the app isn't in the
 * foreground, and folds them into the personal baseline.
 *
 * This is what makes the app usable on day two: the baseline keeps learning during ordinary wear
 * instead of only while the user has the app open, so by the time they actually need detection
 * the app already knows what their calm looks like.
 */
class ZenPulsePassiveListenerService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val monitor = (application as? ZenPulseApplication)?.container?.stressMonitor ?: return
        val now = System.currentTimeMillis()

        dataPoints.getData(DataType.HEART_RATE_BPM).forEach { sample ->
            monitor.offerPassiveSample(bpm = sample.value, timestampMs = now)
        }
    }
}
