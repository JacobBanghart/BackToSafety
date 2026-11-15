package com.backtosafety.app

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.backtosafety.app.core.Analytics
import com.backtosafety.app.core.Store
import com.backtosafety.core.ffi.AnalyticsEvent
import com.backtosafety.core.ffi.analyticsEventWireName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Starting on a database that won't migrate or seed: the app goes on rather than crashing on
 * every launch, and the failure is reported at once, not at PostHog's next batch (which a crash
 * would lose). The databases are copies of the RN app's (spec/fixtures/upgrade), which opening
 * migrates.
 */
@RunWith(RobolectricTestRunner::class)
// No BackToSafetyApp: it would start the core on its own thread.
@Config(sdk = [35], application = Application::class)
class StartupTest {
    @get:Rule val folder = TemporaryFolder()

    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var flushes = 0

    @Before
    fun setUp() {
        Analytics.sink = { name, properties -> events += name to properties }
        Analytics.flusher = { flushes++ }
        Store.dispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        Analytics.sink = null
        Analytics.flusher = {}
        Store.dispatcher = Dispatchers.IO
    }

    /** A copy of the RN app's database, as an update finds it. */
    private fun rnDatabase(): File {
        val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))
        return File(root, "spec/fixtures/upgrade/android/SQLite/nijii.db").copyTo(File(folder.newFolder("SQLite"), "nijii.db"))
    }

    @Test
    fun aFailedMigrationIsReportedAndFlushedWithoutThrowing() = runBlocking {
        val path = rnDatabase()
        // Every RN table is there (so it's adopted), but contacts lacks a column the migration copies.
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE contacts")
            it.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY, name TEXT)")
        }
        assertFalse(migrate(Store.open(path.path)))
        val (name, properties) = events.single()
        assertEquals(analyticsEventWireName(AnalyticsEvent.DATA_MIGRATED), name)
        assertEquals(false, properties["ok"])
        assertEquals(1L, (properties["from_version"] as Number).toLong())
        assertEquals(1, flushes)
    }

    @Test
    fun aSuccessfulMigrationIsReportedAndQueued() = runBlocking {
        assertTrue(migrate(Store.open(rnDatabase().path)))
        val (name, properties) = events.single()
        assertEquals(analyticsEventWireName(AnalyticsEvent.DATA_MIGRATED), name)
        assertEquals(true, properties["ok"])
        assertEquals(0, flushes)
    }

    @Test
    fun aFreshInstallReportsNothing() = runBlocking {
        val store = Store.inMemory()
        assertTrue(migrate(store))
        assertEquals(emptyList<Any>(), events)
        store.closeDatabase() // every query on it fails
        assertFalse(migrate(store))
        assertEquals(emptyList<Any>(), events)
        assertEquals(0, flushes)
    }
}
