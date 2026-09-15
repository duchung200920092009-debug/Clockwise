package com.zenpulse.mobile

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives episode syncs pushed from the watch over the Wearable Data Layer.
 *
 * The system starts this service on delivery whether or not the phone app is open, which is the
 * point: the user's watch decides when there is something to sync, and the phone should already
 * have it by the time they next look.
 */
class EpisodeSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != PATH_EPISODES) return@forEach

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val payload = dataMap.getString(KEY_PAYLOAD) ?: return@forEach

            scope.launch { EpisodeRepository.onPayloadReceived(applicationContext, payload) }
        }
    }

    private companion object {
        /** Must match `PhoneSyncManager` on the watch. */
        const val PATH_EPISODES = "/zenpulse/episodes"
        const val KEY_PAYLOAD = "payload"
    }
}
