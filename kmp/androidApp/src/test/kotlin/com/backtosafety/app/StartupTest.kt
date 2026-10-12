package com.backtosafety.app

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starting on a database that won't migrate or seed: the app goes on rather than crashing on
 * every launch, and reports the failure at once, not at PostHog's next batch (which a crash
 * would lose).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartupTest {
    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var flushes = 0
    private lateinit var db: AppDatabase
    private lateinit var store: Store

    @Before
    fun setUp() {
        Analytics.sink = { name, properties -> events += name to properties }
        Analytics.flusher = { flushes++ }
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
    }

    @After
    fun tearDown() {
        Analytics.sink = { _, _ -> }
        Analytics.flusher = {}
    }

    @Test
    fun aFailedMigrationIsReportedAndFlushedWithoutThrowing() = runBlocking {
        db.close() // every query on it fails, as a migration that threw does
        assertFalse(migrate(store, migratingFrom = 1))
        val (name, properties) = events.single()
        assertEquals(AnalyticsEvent.DATA_MIGRATED.wireName, name)
        assertEquals(false, properties["ok"])
        assertEquals(1, properties["from_version"])
        assertEquals(1, flushes)
    }

    @Test
    fun aSuccessfulMigrationIsReportedAndQueued() = runBlocking {
        assertTrue(migrate(store, migratingFrom = 1))
        val (name, properties) = events.single()
        assertEquals(AnalyticsEvent.DATA_MIGRATED.wireName, name)
        assertEquals(true, properties["ok"])
        assertEquals(0, flushes)
    }

    @Test
    fun aFreshInstallReportsNothing() = runBlocking {
        assertTrue(migrate(store, migratingFrom = null))
        assertEquals(emptyList<Any>(), events)
        db.close()
        assertFalse(migrate(store, migratingFrom = null))
        assertEquals(emptyList<Any>(), events)
        assertEquals(0, flushes)
    }
}
