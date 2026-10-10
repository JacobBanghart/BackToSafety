package com.backtosafety.app.emergency

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.backtosafety.app.MainActivity
import com.backtosafety.app.R
import com.backtosafety.core.AlertScheduler
import com.backtosafety.core.AppClock
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.ScheduledAlert
import com.backtosafety.core.Translate
import com.backtosafety.core.invoke

/**
 * The countdown alerts as notifications while the emergency screen isn't showing (shared
 * EmergencyAway decides when). An alarm per alert fires [CountdownAlertReceiver], which posts
 * it; the text is translated when scheduled, in the app's language then.
 *
 * Alarms are exact when the app may set exact alarms (Android 12+ asks the user; before that
 * it's allowed), otherwise inexact, which Android can deliver some minutes late. The catch-up
 * on returning to the app covers that.
 */
class CountdownNotifications(private val context: Context, private val emergencyT: () -> Translate, private val emergencyNumber: () -> String) : AlertScheduler {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(alerts: List<ScheduledAlert>) {
        cancelAll()
        val t = emergencyT()
        val number = mapOf("emergencyNumber" to emergencyNumber())
        for ((alert, fireAtMs) in alerts) {
            val intent = Intent(context, CountdownAlertReceiver::class.java)
                .putExtra(EXTRA_ALERT, alert.key)
                .putExtra(EXTRA_TITLE, t("alerts.${alert.key}.title", number))
                .putExtra(EXTRA_BODY, t("alerts.${alert.key}.body", number))
                .putExtra(EXTRA_CHANNEL, t("alerts.channel"))
            val pending = PendingIntent.getBroadcast(context, alert.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // On the app's clock, which test builds can move, as a delay from now: an elapsed-time
            // alarm, which changing the phone's time doesn't move either.
            val at = SystemClock.elapsedRealtime() + (fireAtMs - AppClock.nowMs())
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        }
    }

    override fun cancelAll() {
        for (alert in CountdownAlert.entries) {
            val intent = Intent(context, CountdownAlertReceiver::class.java)
            PendingIntent.getBroadcast(context, alert.ordinal, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { alarms.cancel(it); it.cancel() }
            NotificationManagerCompat.from(context).cancel(alert.ordinal + NOTIFICATION_ID_BASE)
        }
    }

    /** Exact alarms: allowed outright before Android 12, by the user's setting since. */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    companion object {
        const val EXTRA_ALERT = "alert"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_CHANNEL = "channel"

        /** MainActivity opens the emergency screen for an intent with this extra (a tapped alert). */
        const val EXTRA_OPEN_EMERGENCY = "open_emergency"
        const val CHANNEL_ID = "emergency_countdown"
        const val NOTIFICATION_ID_BASE = 100

        /** Whether the app may post notifications (asked for since Android 13). */
        fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

/** Posts a countdown alert when its alarm fires; tapping it opens the emergency screen. */
class CountdownAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alert = CountdownAlert.entries.firstOrNull { it.key == intent.getStringExtra(CountdownNotifications.EXTRA_ALERT) } ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CountdownNotifications.CHANNEL_ID,
                intent.getStringExtra(CountdownNotifications.EXTRA_CHANNEL) ?: "Emergency countdown",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { enableVibration(true) }
            manager.createNotificationChannel(channel)
        }
        val open = Intent(context, MainActivity::class.java)
            .putExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, alert.ordinal, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CountdownNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra(CountdownNotifications.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(CountdownNotifications.EXTRA_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(CountdownNotifications.EXTRA_BODY)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(if (alert == CountdownAlert.EXPIRED) longArrayOf(0, 500, 200, 500) else longArrayOf(0, 30, 80, 30))
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        manager.notify(alert.ordinal + CountdownNotifications.NOTIFICATION_ID_BASE, notification)
    }
}
