package com.zenpulse.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Pushes episode history to the paired phone over the Wearable Data Layer.
 *
 * Only *episodes* are synced, never the raw physiological trace. The phone companion exists to
 * make patterns readable ("three episodes this week, all before 9am"), and that needs summaries,
 * not a continuous heart-rate recording — which would be both far larger and far more sensitive.
 *
 * Every failure here is swallowed deliberately: there may be no phone paired, the companion app
 * may not be installed, or Bluetooth may simply be off. None of that should interrupt monitoring
 * on the watch, which is the part that actually matters to the user.
 */
class PhoneSyncManager(context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun syncEpisodes(episodesJson: String) {
        runCatching {
            val request = PutDataMapRequest.create(PATH_EPISODES).apply {
                dataMap.putString(KEY_PAYLOAD, episodesJson)
                // The Data Layer skips items whose bytes are unchanged, so a timestamp guarantees
                // the update is actually delivered even if the episode list happens to match.
                dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
        }.onFailure { error ->
            Log.d(TAG, "Episode sync skipped: ${error.message}")
        }
    }

    companion object {
        const val PATH_EPISODES = "/zenpulse/episodes"
        const val KEY_PAYLOAD = "payload"
        const val KEY_UPDATED_AT = "updated_at"
        private const val TAG = "PhoneSyncManager"
    }
}
