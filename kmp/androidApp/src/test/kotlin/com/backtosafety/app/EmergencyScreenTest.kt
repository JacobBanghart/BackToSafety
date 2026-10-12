package com.backtosafety.app

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.CountdownService
import com.backtosafety.app.emergency.EmergencyScreen
import com.backtosafety.app.emergency.OngoingCountdown
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.AlertScheduler
import com.backtosafety.core.Analytics
import com.backtosafety.core.AppClock
import com.backtosafety.core.EmergencyAway
import com.backtosafety.core.ScheduledAlert
import com.backtosafety.core.Translate
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/**
 * How the emergency screen ends an emergency, with a database whose queries run only when the
 * test lets them: the order the writes and the screen's leaving interleave in is the test's,
 * not the machine's. The incident row and the saved emergency are what's asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class EmergencyScreenTest {
    private val translations: Translations by lazy {
        val locales = File(File(System.getProperty("repoRoot")!!), "i18n/locales")
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }.associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }

    /** Runs database work inline until [hold] is called, then queues it until [runNext]. */
    private class GatedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        private var held = false

        fun hold() {
            held = true
        }

        @Synchronized
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (held) queue.addLast(block) else block.run()
        }

        @Synchronized
        fun release() {
            held = false
            while (runNext()) continue
        }

        @Synchronized
        fun runNext(): Boolean = queue.removeFirstOrNull()?.also { it.run() } != null
    }

    private object NoScheduler : AlertScheduler {
        override fun schedule(alerts: List<ScheduledAlert>) {}
        override fun cancelAll() {}
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_791_302_400_000L
    private val startedAt = Instant.fromEpochMilliseconds(now - 60_000L).toString()
    private val gate = GatedDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var store: Store
    private lateinit var appScope: CoroutineScope

    /** Set by the screen's onLeave; the screen is then taken off the composition, as navigation does. */
    private var left by mutableStateOf(false)

    private fun t(namespace: String): Translate = translations.translator("en", namespace)

    /** What the screen reported, in order. */
    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()

    @Before
    fun setUp() {
        Analytics.sink = { name, properties -> tracked += name to properties }
        AppClock.testSeamsEnabled = true
        AppClock.freeze(now)
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(gate)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        runBlocking { store.seed() }
    }

    @After
    fun tearDown() {
        Analytics.sink = { _, _ -> }
        appScope.cancel()
        db.close()
    }

    /** The countdown outside the app, which the screen starts and stops. */
    private val countdown by lazy { OngoingCountdown(ApplicationProvider.getApplicationContext(), { t("emergency") }, { "911" }) }

    /** Shows the screen on the emergency that [saved] says is under way, or on a new one. */
    private fun showScreen(saved: ActiveEmergency?) {
        saved?.let { runBlocking { store.saveActiveEmergency(it) } }
        gate.hold()
        compose.setContent {
            if (!left) {
                EmergencyScreen(
                    t("emergency"), t("common"), store, EmergencyAway(store, NoScheduler), appScope, { left = true }, {},
                    onRunning = countdown::sync,
                )
            }
        }
        settle()
    }

    /** What the screen asked of the countdown's service, in order: the start time, or null to stop. */
    private fun countdownCommands(): List<Long?> = generateSequence { shadowOf(compose.activity.application).nextStartedService }
        .filter { it.component?.className == CountdownService::class.java.name }
        .map { if (it.action == CountdownService.ACTION_STOP) null else it.getLongExtra(CountdownService.EXTRA_STARTED_AT, -1) }
        .toList()

    /** Lets the screen and the database run, one query at a time, until neither has more to do. */
    private fun settle() {
        repeat(1_000) {
            compose.waitForIdle()
            if (!gate.runNext()) return
        }
        error("The screen never settled")
    }

    /** Reads the store once everything has settled: the queries go straight through. */
    private fun <T> read(query: suspend () -> T): T {
        gate.release()
        return runBlocking { query() }
    }

    private fun tap(tag: String, scroll: Boolean = true) {
        val node = compose.onNodeWithTag(tag)
        (if (scroll) node.performScrollTo() else node).performClick()
        settle()
    }

    @Test
    fun foundDoesNotBringBackTheEmergencyItClears() {
        // Started by the RN app before incidents existed: no incident row to update, so
        // recording the outcome makes one, which used to save the emergency again.
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = null))
        tap("emergency-found")
        assertNull(read { store.activeEmergency() })
        val incident = read { store.incidents() }.single()
        assertEquals("found", incident.outcome)
        assertNotNull(incident.endedAt)
    }

    @Test
    fun foundAfterAStepWasCheckedStillClears() {
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-step-call_911")
        tap("emergency-found")
        assertNull(read { store.activeEmergency() })
    }

    @Test
    fun endingTheEmergencyRecordsTheEndBeforeTheScreenIsGone() {
        val id = runBlocking { store.createIncident(startedAt) }
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = id))
        tap("emergency-back", scroll = false)
        tap("emergency-modal-leave-end", scroll = false)
        assertTrue("The screen should have been left", left)
        val incident = read { store.incidents() }.single()
        assertNotNull("The end time was never recorded", incident.endedAt)
        assertEquals("ongoing", incident.outcome)
        assertNull(read { store.activeEmergency() })
    }

    @Test
    fun theEndIsRecordedEvenWhenTheScreenIsGoneBeforeTheWritesLand() {
        val id = runBlocking { store.createIncident(startedAt) }
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = id))
        tap("emergency-back", scroll = false)
        // End emergency, and the screen goes (its scope with it) while the incident write is
        // still waiting on the database: what the user's leaving does to a write in flight.
        compose.onNodeWithTag("emergency-modal-leave-end").performClick()
        left = true
        compose.waitForIdle()
        settle()
        val incident = read { store.incidents() }.single()
        assertNotNull("The end time was never recorded", incident.endedAt)
        assertNull(read { store.activeEmergency() })
    }

    @Test
    fun aNewEmergencyShowsItsCountdownAndFoundEndsIt() {
        showScreen(null)
        tap("emergency-found")
        assertEquals(listOf(now, null), countdownCommands())
    }

    @Test
    fun aResumedEmergencyShowsItsCountdownAndEndingItEndsThat() {
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-back", scroll = false)
        tap("emergency-modal-leave-end", scroll = false)
        assertEquals(listOf(now - 60_000L, null), countdownCommands())
    }

    @Test
    fun leavingTheEmergencyRunningKeepsItsCountdown() {
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-back", scroll = false)
        tap("emergency-modal-leave-leave", scroll = false)
        assertTrue(left)
        assertEquals(listOf<Long?>(now - 60_000L), countdownCommands())
    }

    @Test
    fun backOnTheFoundModalLeavesTheScreenLikeOkDoes() {
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-found")
        compose.onNodeWithTag("emergency-modal-found-ok").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertTrue("Back left the finished emergency's screen up", left)
        assertNull(read { store.activeEmergency() })
    }

    /** A phone app that handles dialing 911, as a phone has. */
    private fun addDialer() {
        shadowOf(compose.activity.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "dialer"; name = "Dialer" } },
        )
    }

    private fun events(name: String) = tracked.filter { it.first == name }.map { it.second }

    @Test
    fun callingNineOneOneRecordsTheCallOnceTheDialerOpened() {
        addDialer()
        val id = runBlocking { store.createIncident(startedAt) }
        showScreen(ActiveEmergency(startedAt, "", listOf("home_search"), incidentId = id))
        tap("emergency-call-911")
        assertEquals(Intent.ACTION_DIAL, shadowOf(compose.activity).nextStartedActivity.action)
        assertEquals(listOf(mapOf<String, Any?>("seconds_elapsed" to 60L, "checked_count" to 1)), events("emergency_911_called"))
        assertEquals(listOf("home_search", "call_911"), read { store.activeEmergency() }!!.checkedSteps)
        assertEquals("911_called", read { store.incidents() }.single().outcome)
        assertTrue(events("dial_failed").isEmpty())
    }

    @Test
    fun theTimeSinceTheStartKeepsCountingPastTheSearchWindow() {
        addDialer()
        val late = Instant.fromEpochMilliseconds(now - 20 * 60_000L).toString()
        showScreen(ActiveEmergency(late, "", emptyList(), incidentId = runBlocking { store.createIncident(late) }))
        tap("emergency-call-911")
        assertEquals(listOf(mapOf<String, Any?>("seconds_elapsed" to 1200L, "checked_count" to 0)), events("emergency_911_called"))
    }

    @Test
    fun noPhoneAppMeansNoCallWasMade() {
        // A tablet: nothing handles the dialer, so the number is shown and dial_failed reported.
        shadowOf(compose.activity.application).checkActivities(true)
        val id = runBlocking { store.createIncident(startedAt) }
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = id))
        tap("emergency-call-911")
        assertEquals(1, events("dial_failed").size)
        assertTrue("No call was made, yet 911 was reported called", events("emergency_911_called").isEmpty())
        assertTrue("The call step was ticked for a call that wasn't made", read { store.activeEmergency() }!!.checkedSteps.isEmpty())
        assertEquals("ongoing", read { store.incidents() }.single().outcome)
        assertTrue(events("emergency_step_completed").isEmpty())
    }

    /** The settings table gone, so reading the saved emergency fails; [fixSettings] brings it back. */
    private fun breakSettings() = renameSettings("settings", "settings_away")

    private fun fixSettings() {
        gate.release() // queries go straight through, as in [read]
        renameSettings("settings_away", "settings")
    }

    private fun renameSettings(from: String, to: String) = runBlocking {
        db.useWriterConnection { it.usePrepared("ALTER TABLE $from RENAME TO $to") { statement -> statement.step() } }
    }

    @Test
    fun aFailedReadOfTheSavedEmergencyDoesNotStartANewOne() {
        val id = runBlocking { store.createIncident(startedAt) }
        runBlocking { store.saveActiveEmergency(ActiveEmergency(startedAt, "", listOf("home_search"), incidentId = id)) }
        breakSettings()
        showScreen(null)
        compose.onNodeWithTag("emergency-load-failed").assertExists()
        compose.onNodeWithTag("emergency-timer").assertDoesNotExist()
        assertEquals(1, events("save_failed").size)
        assertEquals("emergency", events("save_failed").single()["screen"])
        assertEquals("load", events("save_failed").single()["action"])
        assertTrue("A failed read started an emergency", events("emergency_started").isEmpty())
        assertTrue(countdownCommands().isEmpty())

        // Retry, with the store back: the running emergency, not a new one.
        fixSettings()
        tap("emergency-load-retry", scroll = false)
        compose.onNodeWithTag("emergency-timer").assertExists()
        compose.onNodeWithTag("emergency-load-failed").assertDoesNotExist()
        val saved = read { store.activeEmergency() }!!
        assertEquals(startedAt, saved.startedAt)
        assertEquals(id, saved.incidentId)
        assertEquals(listOf("home_search"), saved.checkedSteps)
        assertEquals(1, read { store.incidents() }.size)
        assertTrue(events("emergency_started").isEmpty())
        assertEquals(listOf<Long?>(now - 60_000L), countdownCommands())
    }

    @Test
    fun theLoadFailedScreenCanBeLeft() {
        breakSettings()
        showScreen(null)
        tap("emergency-back", scroll = false)
        assertTrue(left)
    }

    private fun notifications(enabled: Boolean) =
        shadowOf(compose.activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(enabled)

    @Test
    fun leavingWarnsThatAlertsWontComeWhenNotificationsAreOff() {
        notifications(false)
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-back", scroll = false)
        compose.onNodeWithTag("emergency-modal-leave-notifications-off").assertExists()
        tap("emergency-modal-leave-notifications-settings", scroll = false)
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(compose.activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        // The modal stays: the choice to stay, leave or end is still the user's.
        compose.onNodeWithTag("emergency-modal-leave-stay").assertExists()
    }

    @Test
    fun leavingSaysNothingOfNotificationsWhenTheyAreOn() {
        notifications(true)
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-back", scroll = false)
        compose.onNodeWithTag("emergency-modal-leave-stay").assertExists()
        compose.onNodeWithTag("emergency-modal-leave-notifications-off").assertDoesNotExist()
        compose.onNodeWithTag("emergency-modal-leave-notifications-settings").assertDoesNotExist()
        assertFalse(left)
    }
}
