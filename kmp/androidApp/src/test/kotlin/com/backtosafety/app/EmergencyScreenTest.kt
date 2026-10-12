package com.backtosafety.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.EmergencyScreen
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.AlertScheduler
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Before
    fun setUp() {
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
        appScope.cancel()
        db.close()
    }

    /** Shows the screen on the emergency that [saved] says is under way. */
    private fun showScreen(saved: ActiveEmergency) {
        runBlocking { store.saveActiveEmergency(saved) }
        gate.hold()
        compose.setContent {
            if (!left) EmergencyScreen(t("emergency"), t("common"), store, EmergencyAway(store, NoScheduler), appScope, { left = true }, {})
        }
        settle()
    }

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
    fun backOnTheFoundModalLeavesTheScreenLikeOkDoes() {
        showScreen(ActiveEmergency(startedAt, "", emptyList(), incidentId = runBlocking { store.createIncident(startedAt) }))
        tap("emergency-found")
        compose.onNodeWithTag("emergency-modal-found-ok").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertTrue("Back left the finished emergency's screen up", left)
        assertNull(read { store.activeEmergency() })
    }
}
