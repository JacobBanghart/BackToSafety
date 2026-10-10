package com.backtosafety.core

import com.backtosafety.core.data.Store
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Puts countdown alerts on the phone as local notifications. Each app has one: AlarmManager
 * on Android, UNUserNotificationCenter on iOS.
 */
interface AlertScheduler {
    /** Replaces whatever is scheduled with [alerts]. */
    fun schedule(alerts: List<ScheduledAlert>)

    fun cancelAll()
}

/**
 * The countdown alerts on and off the emergency screen. On the screen, its countdown tick
 * gives them in-app ([inAppAlerts]). Off the screen (the app in the background, or another
 * screen showing), they're scheduled as notifications ([left]). Back on the screen, an alert
 * that came due while away is caught up ([shown]).
 *
 * Both apps drive this from the screen's lifecycle and do nothing but deliver what it decides,
 * so they can't drift apart. The time away starts at the last tick, not at leaving: an alert
 * due between that tick and leaving is caught up instead of lost.
 */
class EmergencyAway(private val store: Store, private val scheduler: AlertScheduler) {
    private val lock = Mutex()
    private var lastTickMs: Long? = null

    /**
     * The screen's countdown moved from [prevSecondsLeft] to [nextSecondsLeft] at [nowMs]: the
     * alerts to give now (vibration), reported as countdown_alert.
     */
    fun inAppAlerts(prevSecondsLeft: Int, nextSecondsLeft: Int, nowMs: Long): List<CountdownAlert> {
        lastTickMs = nowMs
        return countdownAlerts(prevSecondsLeft, nextSecondsLeft).onEach {
            Analytics.track(AnalyticsEvent.COUNTDOWN_ALERT, countdownAlertProperties(it, AlertDelivery.IN_APP))
        }
    }

    /**
     * The emergency screen stopped showing at [nowMs]. If an emergency is running, notes when
     * the time away began (unless already away) and schedules the alerts still to come.
     */
    suspend fun left(nowMs: Long) {
        lock.withLock {
            val started = store.activeEmergency()?.startedAtMs() ?: return
            if (store.setting(Store.EMERGENCY_AWAY_FROM).isNullOrEmpty()) {
                store.putSetting(Store.EMERGENCY_AWAY_FROM, (lastTickMs ?: nowMs).toString())
            }
            scheduler.schedule(alertsToSchedule(started, nowMs))
        }
    }

    /**
     * The emergency screen is showing at [nowMs]: cancels the scheduled alerts, reports the
     * time away (emergency_resumed) and returns the alert to catch up on, if one came due.
     */
    suspend fun shown(nowMs: Long): CountdownAlert? = lock.withLock {
        scheduler.cancelAll()
        lastTickMs = nowMs
        val awayFrom = store.setting(Store.EMERGENCY_AWAY_FROM)?.toLongOrNull()
        if (awayFrom != null) store.putSetting(Store.EMERGENCY_AWAY_FROM, "")
        val started = store.activeEmergency()?.startedAtMs()
        if (awayFrom == null || started == null) return@withLock null
        val alert = catchUpAlert(started, awayFrom, nowMs)
        Analytics.track(AnalyticsEvent.EMERGENCY_RESUMED, emergencyResumedProperties(awayFrom, nowMs, alert))
        alert?.also { Analytics.track(AnalyticsEvent.COUNTDOWN_ALERT, countdownAlertProperties(it, AlertDelivery.CATCH_UP)) }
    }

    /** The emergency ended (found, ended, or the account deleted): nothing more to deliver. */
    suspend fun ended() {
        lock.withLock {
            scheduler.cancelAll()
            lastTickMs = null
            store.putSetting(Store.EMERGENCY_AWAY_FROM, "")
        }
    }

    /** Whether to ask for notification permission: once, after onboarding. */
    suspend fun shouldAskForNotifications(): Boolean = store.setting(Store.NOTIFICATIONS_PROMPTED).isNullOrEmpty()

    /** The answer to the notification permission prompt (notifications_permission). */
    suspend fun notificationsAnswered(granted: Boolean) {
        store.putSetting(Store.NOTIFICATIONS_PROMPTED, "1")
        Analytics.track(AnalyticsEvent.NOTIFICATIONS_PERMISSION, mapOf("granted" to granted))
    }

    private fun ActiveEmergency.startedAtMs(): Long? = runCatching { Instant.parse(startedAt).toEpochMilliseconds() }.getOrNull()
}
