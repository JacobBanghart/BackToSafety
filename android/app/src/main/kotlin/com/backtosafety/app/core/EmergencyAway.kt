package com.backtosafety.app.core

import com.backtosafety.core.ffi.AlertScheduler
import com.backtosafety.core.ffi.CountdownAlert
import kotlinx.coroutines.withContext
import com.backtosafety.core.ffi.EmergencyAway as CoreEmergencyAway

/**
 * The countdown alerts on and off the emergency screen (see the core's EmergencyAway). Both
 * apps drive it from the screen's lifecycle and do nothing but deliver what it decides.
 */
class EmergencyAway(store: Store, scheduler: AlertScheduler) {
    private val core = CoreEmergencyAway(store.core, scheduler)

    /** The countdown moved from [prevSecondsLeft] to [nextSecondsLeft] at [nowMs]: the alerts to give now. */
    fun inAppAlerts(prevSecondsLeft: Int, nextSecondsLeft: Int, nowMs: Long): List<CountdownAlert> =
        core.inAppAlerts(prevSecondsLeft, nextSecondsLeft, nowMs)

    /** The emergency screen stopped showing at [nowMs]. */
    suspend fun left(nowMs: Long) = io { core.left(nowMs) }

    /** The emergency screen is showing at [nowMs]; returns the alert to catch up on, if one came due. */
    suspend fun shown(nowMs: Long): CountdownAlert? = io { core.shown(nowMs) }

    /** The emergency ended: nothing more to deliver. */
    suspend fun ended() = io { core.ended() }

    /** Whether to ask for notification permission: once, after onboarding. */
    suspend fun shouldAskForNotifications(): Boolean = io { core.shouldAskForNotifications() }

    /** The answer to the notification permission prompt. */
    suspend fun notificationsAnswered(granted: Boolean) = io { core.notificationsAnswered(granted) }

    private suspend fun <T> io(block: () -> T): T = withContext(Store.dispatcher) { block() }
}
