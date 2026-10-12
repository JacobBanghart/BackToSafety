package com.backtosafety.core

import com.backtosafety.core.data.Store
import com.backtosafety.core.db.DATABASE_NAME
import com.backtosafety.core.db.databaseBuilder
import com.backtosafety.core.db.openAppDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The countdown alerts both apps deliver: in-app on the emergency screen, scheduled as
 * notifications while it isn't showing, and caught up on returning. The apps' schedulers
 * only deliver what this decides, so these are the alert rules for both.
 */
class EmergencyAwayTest {
    private class FakeScheduler : AlertScheduler {
        var scheduled: List<ScheduledAlert> = emptyList()
        var cancels = 0
        override fun schedule(alerts: List<ScheduledAlert>) {
            scheduled = alerts
        }
        override fun cancelAll() {
            scheduled = emptyList()
            cancels++
        }
    }

    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()
    private val scheduler = FakeScheduler()
    private val store = File(Files.createTempDirectory("nijii").toFile(), DATABASE_NAME).absolutePath
        .let { Store(openAppDatabase(it, databaseBuilder(it))) }
    private val away = EmergencyAway(store, scheduler)

    private val start = ms("2026-10-06T15:00:00Z")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private fun at(minutes: Int, seconds: Int = 0) = start + (minutes * 60 + seconds) * 1000L

    @BeforeTest
    fun captureAnalytics() {
        Analytics.sink = { name, properties -> tracked += name to properties }
    }

    @AfterTest
    fun releaseAnalytics() {
        Analytics.sink = { _, _ -> }
    }

    private fun startEmergency() = runBlocking {
        store.saveActiveEmergency(ActiveEmergency("2026-10-06T15:00:00.000Z", "", emptyList()))
    }

    @Test
    fun leavingSchedulesWhatIsStillToCome() = runBlocking {
        startEmergency()
        away.left(at(2))
        assertEquals(
            listOf(ScheduledAlert(CountdownAlert.WARNING, at(10, 1)), ScheduledAlert(CountdownAlert.EXPIRED, at(15))),
            scheduler.scheduled,
        )
        away.shown(at(3))
        away.left(at(12))
        assertEquals(listOf(ScheduledAlert(CountdownAlert.EXPIRED, at(15))), scheduler.scheduled)
    }

    @Test
    fun noEmergencyNothingScheduled() = runBlocking {
        away.left(at(2))
        assertEquals(emptyList(), scheduler.scheduled)
        assertEquals(null, away.shown(at(20)))
        assertTrue(tracked.isEmpty())
    }

    @Test
    fun returningCancelsAndCatchesUp() = runBlocking {
        startEmergency()
        away.inAppAlerts(301, 300, at(9, 59))
        away.left(at(10))
        assertEquals(CountdownAlert.WARNING, away.shown(at(11)))
        assertEquals(emptyList(), scheduler.scheduled)
        assertEquals(
            listOf(
                "emergency_resumed" to mapOf<String, Any?>("away_s" to 61L, "catch_up" to "warning"),
                "countdown_alert" to mapOf<String, Any?>("kind" to "warning", "delivery" to "catch_up"),
            ),
            tracked,
        )
        // Caught up once: going back and forth doesn't repeat it.
        away.left(at(11, 30))
        assertEquals(null, away.shown(at(12)))
    }

    @Test
    fun expiryOutranksTheWarning() = runBlocking {
        startEmergency()
        away.left(at(5))
        assertEquals(CountdownAlert.EXPIRED, away.shown(at(40)))
    }

    @Test
    fun timeAwayStartsAtTheLastTick() = runBlocking {
        startEmergency()
        // The tick before the warning, then the screen leaves before the next one.
        assertEquals(emptyList(), away.inAppAlerts(300, 300, at(10)))
        away.left(at(10, 1))
        assertEquals(listOf(ScheduledAlert(CountdownAlert.EXPIRED, at(15))), scheduler.scheduled)
        assertEquals(CountdownAlert.WARNING, away.shown(at(10, 5)), "due between the last tick and leaving")
    }

    @Test
    fun timeAwayAfterExpiryStartsAtLeaving() = runBlocking {
        startEmergency()
        // The countdown expired at minute 15 and the apps stopped ticking; the screen stayed
        // open until minute 45, then the app went to the background for ten seconds.
        away.inAppAlerts(1, 0, at(15))
        away.left(at(45))
        assertEquals(emptyList(), scheduler.scheduled, "nothing is left to schedule")
        assertEquals(null, away.shown(at(45, 10)), "the expiry alert was already shown in-app")
        assertEquals(
            listOf("emergency_resumed" to mapOf<String, Any?>("away_s" to 10L, "catch_up" to null)),
            tracked.filter { it.first == "emergency_resumed" },
        )
    }

    @Test
    fun timeAwayAfterReturningPastExpiryStartsAtLeaving() = runBlocking {
        startEmergency()
        // Away across the expiry, back (caught up), and away again: the second time away
        // starts at leaving, not at the return that last set the tick.
        away.left(at(5))
        assertEquals(CountdownAlert.EXPIRED, away.shown(at(20)))
        away.left(at(50))
        assertEquals(null, away.shown(at(50, 10)))
        assertEquals(10L, tracked.last { it.first == "emergency_resumed" }.second["away_s"])
    }

    @Test
    fun leavingTwiceKeepsTheFirstTimeAway() = runBlocking {
        startEmergency()
        away.left(at(5)) // to the info sheet
        away.left(at(11)) // then the app to the background, from there
        assertEquals(CountdownAlert.WARNING, away.shown(at(12)))
    }

    @Test
    fun inAppAlertsAreReported() {
        assertEquals(listOf(CountdownAlert.WARNING), away.inAppAlerts(300, 299, at(10, 1)))
        assertEquals(listOf(CountdownAlert.EXPIRED), away.inAppAlerts(1, 0, at(15)))
        assertEquals(emptyList(), away.inAppAlerts(200, 199, at(11, 41)))
        assertEquals(
            listOf("warning", "expired"),
            tracked.filter { it.first == "countdown_alert" }.map { it.second["kind"] },
        )
        assertTrue(tracked.all { it.second["delivery"] == "in_app" })
    }

    @Test
    fun endingCancelsEverything() = runBlocking {
        startEmergency()
        away.left(at(2))
        away.ended()
        assertEquals(emptyList(), scheduler.scheduled)
        startEmergency()
        assertEquals(null, away.shown(at(20)), "the old time away is gone")
    }

    @Test
    fun clearingTheEmergencyForgetsTheTimeAway() = runBlocking {
        startEmergency()
        away.left(at(2))
        store.clearActiveEmergency()
        startEmergency()
        assertEquals(null, away.shown(at(20)))
    }

    @Test
    fun aRestartSchedulesWhatIsToComeAndPostsWhatCameDue() = runBlocking {
        startEmergency()
        away.left(at(2))
        // Off from minute 9 to 11: the warning came due, the expiry is still to come.
        assertEquals(CountdownAlert.WARNING, away.restarted(at(11)))
        assertEquals(listOf(ScheduledAlert(CountdownAlert.EXPIRED, at(15))), scheduler.scheduled)
        // Back on the screen, it's caught up there too, from the same time away.
        assertEquals(CountdownAlert.WARNING, away.shown(at(12)))
    }

    @Test
    fun aRestartPastExpiryPostsTheExpiry() = runBlocking {
        startEmergency()
        away.left(at(2))
        assertEquals(CountdownAlert.EXPIRED, away.restarted(at(30)))
        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun aRestartBeforeAnythingCameDuePostsNothing() = runBlocking {
        startEmergency()
        away.left(at(2))
        assertEquals(null, away.restarted(at(4)))
        assertEquals(
            listOf(ScheduledAlert(CountdownAlert.WARNING, at(10, 1)), ScheduledAlert(CountdownAlert.EXPIRED, at(15))),
            scheduler.scheduled,
        )
        assertTrue(tracked.isEmpty(), "a restart reports nothing; the return does")
    }

    @Test
    fun aRestartWithTheScreenShowingCountsFromTheStart() = runBlocking {
        startEmergency()
        // The screen was showing (no time away) when the phone went off.
        away.shown(at(1))
        assertEquals(CountdownAlert.EXPIRED, away.restarted(at(20)))
        assertEquals(CountdownAlert.EXPIRED, away.shown(at(21)), "and the return catches it up too")
        assertEquals(1260L, tracked.last { it.first == "emergency_resumed" }.second["away_s"])
    }

    @Test
    fun aRestartWithoutAnEmergencyDoesNothing() = runBlocking {
        assertEquals(null, away.restarted(at(20)))
        assertEquals(emptyList(), scheduler.scheduled)
        assertEquals(null, store.setting(Store.EMERGENCY_AWAY_FROM))
    }

    @Test
    fun notificationPermissionIsAskedOnce() = runBlocking {
        assertTrue(away.shouldAskForNotifications())
        away.notificationsAnswered(granted = false)
        assertFalse(away.shouldAskForNotifications())
        assertEquals(listOf("notifications_permission" to mapOf<String, Any?>("granted" to false)), tracked)
    }
}
