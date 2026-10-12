package com.backtosafety.core

import com.backtosafety.core.data.Store
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Puts countdown alerts on the phone as local notifications. Each app has one: on Android a
 * timer under the emergency's foreground service, with alarms as the backstop; on iOS,
 * UNUserNotificationCenter.
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
 * due between that tick and leaving is caught up instead of lost. Once the countdown has
 * expired the ticks stop mattering, and it starts at leaving.
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
    @Throws(Exception::class)
    suspend fun left(nowMs: Long) {
        lock.withLock {
            val started = store.activeEmergency()?.startedAtMs ?: return
            if (store.setting(Store.EMERGENCY_AWAY_FROM).isNullOrEmpty()) {
                // The apps stop ticking at expiry, so a tick from then or later says nothing about
                // when the screen was left: the expiry alert was already shown in-app, and nothing
                // is left to catch up.
                val tickBeforeExpiry = lastTickMs?.takeIf { it < alertDueAtMs(CountdownAlert.EXPIRED, started) }
                store.putSetting(Store.EMERGENCY_AWAY_FROM, (tickBeforeExpiry ?: nowMs).toString())
            }
            scheduler.schedule(alertsToSchedule(started, nowMs))
        }
    }

    /**
     * The emergency screen is showing at [nowMs]: cancels the scheduled alerts, reports the
     * time away (emergency_resumed) and returns the alert to catch up on, if one came due.
     */
    @Throws(Exception::class)
    suspend fun shown(nowMs: Long): CountdownAlert? = lock.withLock {
        scheduler.cancelAll()
        lastTickMs = nowMs
        val awayFrom = store.setting(Store.EMERGENCY_AWAY_FROM)?.toLongOrNull()
        if (awayFrom != null) store.putSetting(Store.EMERGENCY_AWAY_FROM, "")
        val started = store.activeEmergency()?.startedAtMs
        if (awayFrom == null || started == null) return@withLock null
        val alert = catchUpAlert(started, awayFrom, nowMs)
        Analytics.track(AnalyticsEvent.EMERGENCY_RESUMED, emergencyResumedProperties(awayFrom, nowMs, alert))
        alert?.also { Analytics.track(AnalyticsEvent.COUNTDOWN_ALERT, countdownAlertProperties(it, AlertDelivery.CATCH_UP)) }
    }

    /**
     * The phone restarted at [nowMs] (Android's boot receiver): the scheduled alerts and the
     * notifications already shown went with it, and the screen isn't showing. Schedules the
     * alerts still to come, as [left] does, and returns the one to post now: the most serious
     * that came due while away, as [shown] would catch it up (it still will, on returning).
     *
     * Without a time away the screen was showing when the phone went off, at a moment nobody
     * noted, so the time away counts from the emergency's start: an alert given twice beats a
     * call-911 alert that never came.
     */
    @Throws(Exception::class)
    suspend fun restarted(nowMs: Long): CountdownAlert? = lock.withLock {
        val started = store.activeEmergency()?.startedAtMs ?: return@withLock null
        val awayFrom = store.setting(Store.EMERGENCY_AWAY_FROM)?.toLongOrNull()
            ?: started.also { store.putSetting(Store.EMERGENCY_AWAY_FROM, it.toString()) }
        scheduler.schedule(alertsToSchedule(started, nowMs))
        catchUpAlert(started, awayFrom, nowMs)
    }

    /** The emergency ended (found, ended, or the account deleted): nothing more to deliver. */
    @Throws(Exception::class)
    suspend fun ended() {
        lock.withLock {
            scheduler.cancelAll()
            lastTickMs = null
            store.putSetting(Store.EMERGENCY_AWAY_FROM, "")
        }
    }

    /** Whether to ask for notification permission: once, after onboarding. */
    @Throws(Exception::class)
    suspend fun shouldAskForNotifications(): Boolean = store.setting(Store.NOTIFICATIONS_PROMPTED).isNullOrEmpty()

    /** The answer to the notification permission prompt (notifications_permission). */
    @Throws(Exception::class)
    suspend fun notificationsAnswered(granted: Boolean) {
        store.putSetting(Store.NOTIFICATIONS_PROMPTED, "1")
        Analytics.track(AnalyticsEvent.NOTIFICATIONS_PERMISSION, mapOf("granted" to granted))
    }
}
