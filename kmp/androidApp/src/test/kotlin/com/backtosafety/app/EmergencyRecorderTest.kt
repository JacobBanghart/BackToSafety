package com.backtosafety.app

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.EmergencyRecorder
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the emergency screen's writes may and may not do to the saved emergency and the incident row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmergencyRecorderTest {
    private lateinit var db: AppDatabase
    private lateinit var store: Store
    private lateinit var recorder: EmergencyRecorder
    private val startedAt = "2026-10-06T16:00:00Z"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        recorder = EmergencyRecorder(store)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun anEndedEmergencyIsNeverSavedAgain() = runBlocking {
        val emergency = ActiveEmergency(startedAt, "", emptyList())
        recorder.begin(emergency)
        recorder.save(emergency)
        assertNotNull(store.activeEmergency())
        recorder.end()
        assertNull(store.activeEmergency())
        recorder.save(emergency.copy(wearing = "a red coat"))
        recorder.record(startedAt, emptyList(), "a red coat", "found", "2026-10-06T16:05:00Z")
        assertNull(store.activeEmergency())
    }

    @Test
    fun anEmergencyWithoutAnIncidentGetsOneAndKeepsItsId() = runBlocking {
        val emergency = ActiveEmergency(startedAt, "", emptyList())
        recorder.begin(emergency)
        recorder.save(emergency)
        recorder.record(startedAt, listOf("call_911"), "", outcome = "911_called")
        val row = store.incidents().single()
        assertEquals("911_called", row.outcome)
        // The saved emergency carries the new id, so a restart doesn't make a second row...
        assertEquals(row.id, store.activeEmergency()!!.incidentId)
        // ...and neither does the next record.
        recorder.record(startedAt, listOf("call_911"), "", endedAt = "2026-10-06T16:05:00Z")
        val ended = store.incidents().single()
        assertEquals("911_called", ended.outcome)
        assertEquals("2026-10-06T16:05:00Z", ended.endedAt)
    }

    @Test
    fun savesCarryTheIncidentIdTheRecorderHolds() = runBlocking {
        val id = store.createIncident(startedAt)
        val emergency = ActiveEmergency(startedAt, "", emptyList(), id)
        recorder.begin(emergency)
        recorder.save(emergency.copy(wearing = "blue", incidentId = null))
        assertEquals(id, store.activeEmergency()!!.incidentId)
        assertEquals("blue", store.activeEmergency()!!.wearing)
    }
}
