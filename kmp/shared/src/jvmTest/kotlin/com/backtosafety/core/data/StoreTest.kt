package com.backtosafety.core.data

import com.backtosafety.core.db.DATABASE_NAME
import com.backtosafety.core.db.databaseBuilder
import com.backtosafety.core.db.openAppDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StoreTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

    private fun freshStore(): Store {
        val path = File(Files.createTempDirectory("nijii").toFile(), DATABASE_NAME).absolutePath
        return Store(openAppDatabase(path, databaseBuilder(path)))
    }

    @Test
    fun seedsTheSameRowsAsTheRnApp() {
        val seed = Json.parseToJsonElement(File(root, "spec/seed.json").readText()).jsonObject
        assertEquals(seed["onboardingSteps"]!!.jsonArray.map { it.jsonPrimitive.content }, Store.ONBOARDING_STEPS)
        assertEquals(
            seed["safetyChecks"]!!.jsonArray.map {
                it.jsonObject["category"]!!.jsonPrimitive.content to it.jsonObject["item_key"]!!.jsonPrimitive.content
            },
            Store.SAFETY_CHECKS,
        )
    }

    @Test
    fun onboardingCompletesStepByStep() = runBlocking {
        val store = freshStore()
        store.seed()
        store.seed() // every launch seeds; the second time inserts nothing
        assertFalse(store.isOnboarded())
        Store.ONBOARDING_STEPS.dropLast(1).forEach { store.completeStep(it) }
        assertFalse(store.isOnboarded())
        store.completeStep("complete")
        assertTrue(store.isOnboarded())
    }

    @Test
    fun profileSavesMerge() = runBlocking {
        val store = freshStore()
        store.saveProfile { it.copy(name = "Margaret Smith", nickname = "Maggie") }
        store.saveProfile { it.copy(height = "5'6\"") }
        val profile = store.profile()!!
        assertEquals("Margaret Smith", profile.name)
        assertEquals("Maggie", profile.nickname)
        assertEquals("5'6\"", profile.height)
    }

    @Test
    fun activeEmergencyRoundTripsInTheRnFormat() = runBlocking {
        val store = freshStore()
        val e = com.backtosafety.core.ActiveEmergency("2026-10-06T16:00:00.000Z", "Blue jacket", listOf("neighbors"), incidentId = 3)
        store.saveActiveEmergency(e)
        assertEquals(
            """{"startedAt":"2026-10-06T16:00:00.000Z","wearing":"Blue jacket","checkedSteps":["neighbors"],"isActive":true,"incidentId":3}""",
            store.setting(Store.ACTIVE_EMERGENCY),
        )
        assertEquals(e, store.activeEmergency())
        store.clearActiveEmergency()
        assertEquals("", store.setting(Store.ACTIVE_EMERGENCY))
        assertEquals(null, store.activeEmergency())
    }

    @Test
    fun incidentsRecordOutcomes() = runBlocking {
        val store = freshStore()
        val id = store.createIncident("2026-10-06T16:00:00.000Z")
        store.recordIncident(id, "2026-10-06T16:00:00.000Z", listOf("call_911"), "", outcome = "911_called")
        store.recordIncident(id, "2026-10-06T16:00:00.000Z", listOf("call_911", "neighbors"), "Blue jacket", outcome = "found", endedAt = "2026-10-06T16:10:00.000Z")
        val incident = store.incidents().single()
        assertEquals(id, incident.id)
        assertEquals("found", incident.outcome)
        assertEquals("2026-10-06T16:10:00.000Z", incident.endedAt)
        assertEquals("""["call_911","neighbors"]""", incident.areasChecked)
        assertEquals("Blue jacket", incident.wearing)

        // An emergency from before incidents were recorded gets its row on first update.
        val created = store.recordIncident(null, "2026-10-07T09:00:00.000Z", emptyList(), "", outcome = "911_called")
        assertEquals("911_called", store.incidents().first { it.id == created }.outcome)
    }

    @Test
    fun deleteAccountEmptiesEverythingAndRestartsOnboarding() = runBlocking {
        val store = freshStore()
        store.seed()
        Store.ONBOARDING_STEPS.forEach { store.completeStep(it) }
        store.saveProfile { it.copy(name = "Margaret Smith") }
        store.addContact(com.backtosafety.core.db.ContactEntity(name = "John Smith", phone = "5551234567"))
        store.putSetting(Store.THEME_PREFERENCE, "dark")
        assertTrue(store.isOnboarded())

        store.clearAllData()
        assertEquals(null, store.profile())
        assertTrue(store.contacts().isEmpty())
        assertEquals(null, store.setting(Store.THEME_PREFERENCE))
        assertFalse(store.isOnboarded())
    }

    @Test
    fun deviceIdIsCreatedOnceAndKept() = runBlocking {
        val path = File(Files.createTempDirectory("nijii").toFile(), DATABASE_NAME).absolutePath
        val first = Store(openAppDatabase(path, databaseBuilder(path))).deviceId()
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(first))
        assertEquals(first, Store(openAppDatabase(path, databaseBuilder(path))).deviceId())
    }
}
