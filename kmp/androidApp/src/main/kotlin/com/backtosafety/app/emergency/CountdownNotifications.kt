package com.backtosafety.app.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.HandlerCompat
import com.backtosafety.app.MainActivity
import com.backtosafety.app.R
import com.backtosafety.core.AlertScheduler
import com.backtosafety.core.AppClock
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.EmergencyAway
import com.backtosafety.core.ScheduledAlert
import com.backtosafety.core.Translate
import com.backtosafety.core.invoke

/**
 * The countdown alerts as notifications while the emergency screen isn't showing (shared
 * EmergencyAway decides which and when; this only delivers). Each alert is set up twice:
 *
 * - A timer in the process, with a partial wake lock held until the last alert is due (the
 *   15 minutes at most). While an emergency runs the process has a foreground service
 *   ([CountdownService]), whose wake locks Android honours even in Doze, so it's on time.
 * - An alarm, the backstop for a process that's gone (stopped from the task manager, or by a
 *   maker's battery saver). Exact before Android 12; inexact since, as the app doesn't ask for
 *   exact alarms, so Android can deliver it some minutes late. The catch-up on returning to the
 *   app covers that.
 *
 * The alarm's PendingIntent is one-shot, so while it exists the alert hasn't been delivered:
 * the timer posts only if it's still there, and cancels it. The text is translated when
 * scheduled, in the app's language then.
 */
class CountdownNotifications(private val context: Context, private val emergencyT: () -> Translate, private val emergencyNumber: () -> String) : AlertScheduler {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val timers = Handler(Looper.getMainLooper())
    private val wakeLock by lazy {
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackToSafety:countdown")
            .apply { setReferenceCounted(false) }
    }

    // Exact alarms only where canScheduleExact says they're allowed without the permission.
    @SuppressLint("MissingPermission")
    override fun schedule(alerts: List<ScheduledAlert>) {
        cancelAll()
        for ((alert, fireAtMs) in alerts) {
            val intent = alertIntent(alert)
            val pending = PendingIntent.getBroadcast(context, alert.ordinal, intent, ALARM_FLAGS or PendingIntent.FLAG_UPDATE_CURRENT)
            // On the app's clock, which test builds can move, as a delay from now: an elapsed-time
            // alarm, which changing the phone's time doesn't move either.
            val delayMs = fireAtMs - AppClock.nowMs()
            val at = SystemClock.elapsedRealtime() + delayMs
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
            HandlerCompat.postDelayed(timers, { deliver(alert, intent) }, TIMERS, delayMs)
        }
        // Awake until the last alert is out, then not (the timeout, should the release not run).
        val lastDelayMs = (alerts.maxOfOrNull { it.fireAtMs } ?: return) - AppClock.nowMs()
        wakeLock.acquire(lastDelayMs + WAKE_LOCK_SPARE_MS)
        HandlerCompat.postDelayed(timers, ::releaseWakeLock, TIMERS, lastDelayMs)
    }

    override fun cancelAll() {
        timers.removeCallbacksAndMessages(TIMERS)
        releaseWakeLock()
        for (alert in CountdownAlert.entries) {
            alarm(alert)?.let { alarms.cancel(it); it.cancel() }
            NotificationManagerCompat.from(context).cancel(alert.ordinal + NOTIFICATION_ID_BASE)
        }
    }

    /** Posts [alert] now: one that came due while the phone was off ([EmergencyAway.restarted]). */
    fun post(alert: CountdownAlert) = CountdownAlertReceiver.post(context, alertIntent(alert))

    /**
     * Whether the backstop alarms are exact: before Android 12, where it's allowed outright.
     * Since then it takes a permission the app doesn't ask for; the timer is on time instead.
     */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    /** [alert]'s timer is up: posts it, unless its alarm went off first. */
    private fun deliver(alert: CountdownAlert, intent: Intent) {
        val pending = alarm(alert) ?: return
        alarms.cancel(pending)
        pending.cancel()
        CountdownAlertReceiver.post(context, intent)
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    /** [alert]'s alarm, if it's set and hasn't gone off. */
    private fun alarm(alert: CountdownAlert): PendingIntent? = PendingIntent.getBroadcast(
        context, alert.ordinal, Intent(context, CountdownAlertReceiver::class.java), ALARM_FLAGS or PendingIntent.FLAG_NO_CREATE,
    )

    private fun alertIntent(alert: CountdownAlert): Intent {
        val t = emergencyT()
        val number = mapOf("emergencyNumber" to emergencyNumber())
        return Intent(context, CountdownAlertReceiver::class.java)
            .putExtra(EXTRA_ALERT, alert.key)
            .putExtra(EXTRA_TITLE, t("alerts.${alert.key}.title", number))
            .putExtra(EXTRA_BODY, t("alerts.${alert.key}.body", number))
            .putExtra(EXTRA_CHANNEL, t("alerts.${alert.key}.channel"))
    }

    companion object {
        const val EXTRA_ALERT = "alert"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_CHANNEL = "channel"

        /** MainActivity opens the emergency screen for an intent with this extra (a tapped alert). */
        const val EXTRA_OPEN_EMERGENCY = "open_emergency"
        const val NOTIFICATION_ID_BASE = 100

        /** One-shot: an alarm's PendingIntent is used up when it goes off, which is how the timer knows. */
        private const val ALARM_FLAGS = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE

        /** What the timers are posted with, to cancel them together. */
        private val TIMERS = Any()

        /** How long the wake lock outlasts the last alert, should its release not run. */
        private const val WAKE_LOCK_SPARE_MS = 30_000L

        /**
         * One channel per alert kind: from Android 8 the channel, not the notification, decides
         * how a notification vibrates, so each alert's pattern is set on its own channel.
         */
        fun channelId(alert: CountdownAlert) = "emergency_countdown_${alert.key}"

        /** The alert's vibration: a short double tap for the warning, long pulses once time is up. */
        fun vibrationPattern(alert: CountdownAlert): LongArray = when (alert) {
            CountdownAlert.WARNING -> longArrayOf(0, 30, 80, 30)
            CountdownAlert.EXPIRED -> longArrayOf(0, 500, 200, 500)
        }

        /** Whether the app may post notifications (asked for since Android 13). */
        fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

/** Posts a countdown alert when its alarm fires; tapping it opens the emergency screen. */
class CountdownAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = post(context, intent)

    companion object {
        /** Posts the alert [intent] holds (made by [CountdownNotifications]), if the app may post notifications. */
        fun post(context: Context, intent: Intent) {
            val alert = CountdownAlert.entries.firstOrNull { it.key == intent.getStringExtra(CountdownNotifications.EXTRA_ALERT) } ?: return
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    CountdownNotifications.channelId(alert),
                    intent.getStringExtra(CountdownNotifications.EXTRA_CHANNEL) ?: "Emergency countdown",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { vibrationPattern = CountdownNotifications.vibrationPattern(alert) }
                manager.createNotificationChannel(channel)
            }
            val open = Intent(context, MainActivity::class.java)
                .putExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val content = PendingIntent.getActivity(context, alert.ordinal, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CountdownNotifications.channelId(alert))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(intent.getStringExtra(CountdownNotifications.EXTRA_TITLE))
                .setContentText(intent.getStringExtra(CountdownNotifications.EXTRA_BODY))
                .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(CountdownNotifications.EXTRA_BODY)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                // Before Android 8 there are no channels: the notification sets the vibration itself.
                .setVibrate(CountdownNotifications.vibrationPattern(alert))
                // Posted again while it's still showing (the timer and an exact alarm at the same
                // moment, say), it doesn't buzz twice.
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .setAutoCancel(true)
                .build()
            manager.notify(alert.ordinal + CountdownNotifications.NOTIFICATION_ID_BASE, notification)
        }
    }
}
