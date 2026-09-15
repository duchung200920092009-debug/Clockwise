package com.zenpulse.wear.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zenpulse.wear.R
import com.zenpulse.wear.ZenPulseApplication
import com.zenpulse.wear.presentation.MainActivity

/**
 * Keeps active monitoring alive while the watch screen is off.
 *
 * Without a foreground service the system suspends the app the moment the display sleeps, which
 * on a watch is within seconds — and an episode that happens while the user isn't looking at their
 * wrist is precisely the one worth catching. The persistent notification is the honest cost of
 * that: the user can always see that monitoring is on, and stop it in one tap.
 */
class MonitoringService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            },
        )

        container().stressMonitor.start()
        // START_STICKY: if the system reclaims us under memory pressure, monitoring should come
        // back on its own rather than silently staying off.
        return START_STICKY
    }

    override fun onDestroy() {
        container().stressMonitor.stop()
        super.onDestroy()
    }

    private fun container() = (application as ZenPulseApplication).container

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitoring_channel_name),
            // MIN: this notification is a disclosure, not a message. It should never make a sound.
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.monitoring_channel_description)
            setShowBadge(false)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.monitoring_notification_title))
            .setContentText(getString(R.string.monitoring_notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "zenpulse_monitoring"
        private const val NOTIFICATION_ID = 2002
        private const val ACTION_STOP = "com.zenpulse.wear.action.STOP_MONITORING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MonitoringService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
