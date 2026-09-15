package com.zenpulse.wear.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zenpulse.wear.R
import com.zenpulse.wear.domain.StressLevel
import com.zenpulse.wear.monitor.StressAlert
import com.zenpulse.wear.presentation.MainActivity

/**
 * Turns a [StressAlert] into a nudge on the wrist.
 *
 * The tone here is a product decision, not a detail. The user is someone who may be anxious, and
 * a notification that announces a problem ("High stress detected!") can hand them something new
 * to panic about — the app would then be causing the state it claims to detect. So the copy never
 * diagnoses, never uses alarm language, and always offers a concrete, optional next step.
 *
 * The haptic follows the same logic: one soft double-tap, not an escalating buzz.
 */
class StressNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // DEFAULT, not HIGH: this should feel like a tap on the shoulder, never an emergency.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(false) // handled explicitly so the pattern stays gentle
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notifyStress(alert: StressAlert) {
        if (!hasNotificationPermission()) return

        val openBreathing = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_BREATHING, true)
            putExtra(MainActivity.EXTRA_PATTERN_ID, alert.suggestedPattern.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openBreathing,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleFor(alert.level)))
            .setContentText(context.getString(R.string.notification_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
        vibrateGently()
    }

    /** Two short, soft pulses — enough to notice, not enough to startle. */
    fun vibrateGently() {
        val vibrator = resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, 60, 120, 60),
            intArrayOf(0, 120, 0, 120),
            -1,
        )
        runCatching { vibrator.vibrate(effect) }
    }

    /** A single tick used to mark breathing phase changes without looking at the screen. */
    fun tickHaptic() {
        val vibrator = resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(40, 90))
        }
    }

    private fun resolveVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = ContextCompat.getSystemService(context, VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }

    private fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun titleFor(level: StressLevel): Int = when (level) {
        StressLevel.HIGH -> R.string.notification_title_high
        else -> R.string.notification_title_elevated
    }

    companion object {
        const val CHANNEL_ID = "zenpulse_stress"
        const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE = 1001
    }
}
