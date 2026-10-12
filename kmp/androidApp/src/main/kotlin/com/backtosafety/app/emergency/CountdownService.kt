package com.backtosafety.app.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.backtosafety.app.MainActivity
import com.backtosafety.app.R
import com.backtosafety.core.AppClock
import com.backtosafety.core.CountdownAlert
import com.backtosafety.core.Translate
import com.backtosafety.core.alertDueAtMs
import com.backtosafety.core.invoke
import kotlin.time.Instant

private const val TAG = "BackToSafety"

/**
 * The emergency's countdown outside the app, on Android: an ongoing notification for as long as
 * the emergency runs, shown by [CountdownService]. The emergency screen starts it ([sync] with
 * the emergency's start), as does the app finding one under way at launch or after a restart;
 * Found, End emergency and deleting the account stop it ([sync] with null). Its text is
 * translated when started, in the app's language then.
 */
class OngoingCountdown(private val context: Context, private val emergencyT: () -> Translate, private val emergencyNumber: () -> String) {
    /** Shows the countdown of the emergency started at [startedAt] (ISO), or with null, ends it. */
    fun sync(startedAt: String?) = if (startedAt == null) stop() else start(startedAt)

    private fun start(startedAt: String) {
        val startedAtMs = runCatching { Instant.parse(startedAt).toEpochMilliseconds() }.getOrNull() ?: return
        val t = emergencyT()
        val number = mapOf("emergencyNumber" to emergencyNumber())
        val intent = Intent(context, CountdownService::class.java)
            .putExtra(CountdownService.EXTRA_STARTED_AT, startedAtMs)
            .putExtra(CountdownService.EXTRA_CHANNEL, t("ongoing.channel"))
            .putExtra(CountdownService.EXTRA_TITLE, t("ongoing.title"))
            .putExtra(CountdownService.EXTRA_BODY, t("ongoing.body", number))
            .putExtra(CountdownService.EXTRA_EXPIRED_TITLE, t("ongoing.expiredTitle", number))
            .putExtra(CountdownService.EXTRA_EXPIRED_BODY, t("ongoing.expiredBody", number))
        // Started from the foreground (the screen, the app opening) or the boot receiver, which
        // Android 12+ allows; anything else is refused, and the alerts' alarms stand in.
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { Log.w(TAG, "The countdown notification couldn't start", it) }
    }

    /**
     * As a command, not stopService: a service started for the foreground and stopped before it
     * got there crashes the app, and commands run in order, so this one comes after the start.
     */
    private fun stop() {
        runCatching { context.startService(Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_STOP)) }
            .onFailure { Log.w(TAG, "The countdown notification couldn't stop", it) }
    }
}

/**
 * The foreground service behind [OngoingCountdown]. Besides showing the countdown on the lock
 * screen and in the shade, it keeps the process in the foreground, which is what makes the
 * alerts' wake lock hold in Doze (CountdownNotifications).
 *
 * Type specialUse (the manifest says why): a caregiver's emergency countdown fits none of the
 * other types, shortService stops after 3 minutes, and systemExempted needs the exact-alarm
 * permission this replaces. It has no time limit and may start from BOOT_COMPLETED.
 */
class CountdownService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacksAndMessages(null)
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val startedAtMs = intent?.getLongExtra(EXTRA_STARTED_AT, -1L)?.takeIf { it >= 0 }
        if (intent == null || startedAtMs == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // How long is left on the app's clock, which test builds can move.
        val leftMs = alertDueAtMs(CountdownAlert.EXPIRED, startedAtMs) - AppClock.nowMs()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(intent, leftMs), type)
        } catch (e: RuntimeException) {
            // Refused (started from the background, say): the alerts' alarms still stand.
            Log.w(TAG, "The countdown notification was refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        // At expiry it turns into the call to 911, as the screen's countdown does.
        if (leftMs > 0) {
            handler.postDelayed({ getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(intent, 0)) }, leftMs)
        }
        // Killed with the process, it comes back with the same emergency.
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** The countdown with [leftMs] to go, or once it's up, the call to 911. */
    private fun notification(texts: Intent, leftMs: Long): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            // Low importance: silent and never on top of the screen. The alerts have their own channels.
            val channel = NotificationChannel(CHANNEL_ID, texts.getStringExtra(EXTRA_CHANNEL) ?: "Emergency countdown", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = Intent(this, MainActivity::class.java)
            .putExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(this, OPEN_REQUEST, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val expired = leftMs <= 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(texts.getStringExtra(if (expired) EXTRA_EXPIRED_TITLE else EXTRA_TITLE))
            .setContentText(texts.getStringExtra(if (expired) EXTRA_EXPIRED_BODY else EXTRA_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(texts.getStringExtra(if (expired) EXTRA_EXPIRED_BODY else EXTRA_BODY)))
            .apply {
                if (expired) {
                    setShowWhen(false)
                } else {
                    // The system counts down to a time on the phone's clock: as far from now as
                    // the expiry is on the app's. Its chronometer drops the part second that the
                    // screen's countdown (secondsRemaining) rounds up, so it aims 999 ms later
                    // to show the same.
                    setWhen(System.currentTimeMillis() + leftMs + 999)
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // Nothing private in it, so the lock screen shows it whole.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Shown at once, not after the 10 seconds Android 12+ may wait.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(content)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.backtosafety.app.STOP_COUNTDOWN"
        const val EXTRA_STARTED_AT = "started_at_ms"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_EXPIRED_TITLE = "expired_title"
        const val EXTRA_EXPIRED_BODY = "expired_body"

        const val CHANNEL_ID = "emergency_ongoing"
        const val NOTIFICATION_ID = 99
        private const val OPEN_REQUEST = 99
    }
}
