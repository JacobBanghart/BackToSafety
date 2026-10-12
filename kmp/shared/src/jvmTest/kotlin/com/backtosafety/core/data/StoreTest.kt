package com.backtosafety.core.data

import androidx.room.useWriterConnection
import com.backtosafety.core.db.AppDatabase
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.db.DATABASE_NAME
import com.backtosafety.core.db.DestinationEntity
import com.backtosafety.core.db.databaseBuilder
import com.backtosafety.core.db.openAppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

    private fun freshDb(): AppDatabase {
        val path = File(Files.createTempDirectory("nijii").toFile(), DATABASE_NAME).absolutePath
        return openAppDatabase(path, databaseBuilder(path))
    }

    private fun freshStore() = Store(freshDb())

    private suspend fun AppDatabase.exec(sql: String) = useWriterConnection { it.usePrepared(sql) { statement -> statement.step() } }

    /** Makes the database refuse [event] on [table] (when [condition] holds), as a full disk would. */
    private suspend fun AppDatabase.failOn(event: String, table: String, condition: String = "1") =
        exec("CREATE TRIGGER refuse BEFORE $event ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, 'refused'); END")

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
        store.addContact(ContactEntity(name = "John Smith", phone = "5551234567"))
        store.putSetting(Store.THEME_PREFERENCE, "dark")
        assertTrue(store.isOnboarded())

        store.clearAllData()
        assertEquals(null, store.profile())
        assertTrue(store.contacts().isEmpty())
        assertEquals(null, store.setting(Store.THEME_PREFERENCE))
        assertFalse(store.isOnboarded())
    }

    @Test
    fun readinessCountsWhatIsStored() = runBlocking {
        val store = freshStore()
        assertEquals(
            mapOf(
                "has_profile" to false, "has_photo" to false, "has_medical" to false, "profile_fields_filled" to 0,
                "contacts_count" to 0, "alert_contacts_count" to 0, "places_count" to 0,
            ),
            store.readinessProperties(),
        )
        store.saveProfile { it.copy(name = "Margaret Smith", medicalConditions = "Dementia", likes = "Gardening") }
        store.addContact(ContactEntity(name = "Ana", phone = "1"))
        store.addContact(ContactEntity(name = "Bo", phone = "2", notifyOnEmergency = false))
        store.addDestination(DestinationEntity(name = "Riverside Park"))
        assertEquals(
            mapOf(
                "has_profile" to true, "has_photo" to false, "has_medical" to true, "profile_fields_filled" to 2,
                "contacts_count" to 2, "alert_contacts_count" to 1, "places_count" to 1,
            ),
            store.readinessProperties(),
        )
    }

    @Test
    fun deviceIdIsCreatedOnceAndKept() = runBlocking {
        val path = File(Files.createTempDirectory("nijii").toFile(), DATABASE_NAME).absolutePath
        val first = Store(openAppDatabase(path, databaseBuilder(path))).deviceId()
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(first))
        assertEquals(first, Store(openAppDatabase(path, databaseBuilder(path))).deviceId())
    }

    @Test
    fun contactsAddEditReorderDelete() = runBlocking {
        val store = freshStore()
        val ana = store.addContact(ContactEntity(name = "Ana", phone = "1", relationship = "Neighbor", notes = "Has a key"))
        val bo = store.addContact(ContactEntity(name = "Bo", phone = "2"))
        assertEquals(listOf("Ana" to 0, "Bo" to 1), store.contacts().map { it.name to it.sortOrder })
        assertTrue(store.contacts().all { it.createdAt != null })

        // Clearing fields in the edit form clears them (F-35).
        val edited = store.contacts().first { it.id == ana }.copy(name = "Ana Lopez", relationship = null, notes = null)
        store.updateContact(edited)
        val saved = store.contacts().first { it.id == ana }
        assertEquals("Ana Lopez", saved.name)
        assertEquals(null, saved.relationship)
        assertEquals(null, saved.notes)

        store.reorderContacts(store.contacts().reversed())
        assertEquals(listOf("Bo", "Ana Lopez"), store.contacts().map { it.name })

        store.deleteContact(bo)
        assertEquals(listOf("Ana Lopez"), store.contacts().map { it.name })
    }

    @Test
    fun destinationsAddEditReorderDelete() = runBlocking {
        val store = freshStore()
        val park = store.addDestination(DestinationEntity(name = "Park", reason = "Walks", category = "walking_route"))
        store.addDestination(DestinationEntity(name = "Church"))
        store.updateDestination(store.destinations().first { it.id == park }.copy(reason = null))
        assertEquals(null, store.destinations().first { it.id == park }.reason)
        store.reorderDestinations(store.destinations().reversed())
        assertEquals(listOf("Church", "Park"), store.destinations().map { it.name })
        store.deleteDestination(park)
        assertEquals(listOf("Church"), store.destinations().map { it.name })
    }

    @Test
    fun updateProfileSetsGivenFieldsAndKeepsTheRest() = runBlocking {
        val store = freshStore()
        store.updateProfile(mapOf("name" to "Margaret Smith", "nickname" to "Maggie"))
        store.updateProfile(mapOf("height" to "5'6\"", "nickname" to null))
        val profile = store.profile()!!
        assertEquals("Margaret Smith", profile.name)
        assertEquals(null, profile.nickname)
        assertEquals("5'6\"", profile.height)
    }

    @Test
    fun deleteAccountFailingHalfWayChangesNothing() = runBlocking {
        val db = freshDb()
        val store = Store(db)
        store.seed()
        Store.ONBOARDING_STEPS.forEach { store.completeStep(it) }
        store.saveProfile { it.copy(name = "Margaret Smith") }
        store.addContact(ContactEntity(name = "John Smith", phone = "5551234567"))
        store.putSetting(Store.THEME_PREFERENCE, "dark")
        // The profile, contacts and the rest are deleted before the settings are.
        db.failOn("DELETE", "settings")

        assertFailsWith<Exception> { store.clearAllData() }
        assertEquals("Margaret Smith", store.profile()?.name)
        assertEquals(listOf("John Smith"), store.contacts().map { it.name })
        assertEquals("dark", store.setting(Store.THEME_PREFERENCE))
        assertTrue(store.isOnboarded())
    }

    @Test
    fun clearingTheEmergencyFailingHalfWayKeepsBothSettings() = runBlocking {
        val db = freshDb()
        val store = Store(db)
        store.saveActiveEmergency(com.backtosafety.core.ActiveEmergency("2026-10-06T16:00:00.000Z", "", emptyList()))
        store.putSetting(Store.EMERGENCY_AWAY_FROM, "1000")
        db.failOn("INSERT", "settings", "NEW.key = '${Store.EMERGENCY_AWAY_FROM}'")

        assertFailsWith<Exception> { store.clearActiveEmergency() }
        assertTrue(store.activeEmergency() != null)
        assertEquals("1000", store.setting(Store.EMERGENCY_AWAY_FROM))
    }

    @Test
    fun reorderFailingHalfWayKeepsTheOldOrder() = runBlocking {
        val db = freshDb()
        val store = Store(db)
        listOf("Ana", "Bo", "Cy").forEach { store.addContact(ContactEntity(name = it, phone = "1")) }
        listOf("Park", "Church", "Shop").forEach { store.addDestination(DestinationEntity(name = it)) }
        // Reversed, the first row moves to 0 and the last to 2: the move to 2 is refused.
        db.failOn("UPDATE", "contacts", "NEW.sort_order = 2")
        assertFailsWith<Exception> { store.reorderContacts(store.contacts().reversed()) }
        assertEquals(listOf("Ana" to 0, "Bo" to 1, "Cy" to 2), store.contacts().map { it.name to it.sortOrder })

        db.exec("DROP TRIGGER refuse")
        db.failOn("UPDATE", "destinations", "NEW.sort_order = 2")
        assertFailsWith<Exception> { store.reorderDestinations(store.destinations().reversed()) }
        assertEquals(listOf("Park" to 0, "Church" to 1, "Shop" to 2), store.destinations().map { it.name to it.sortOrder })
    }

    @Test
    fun concurrentAddsGetDistinctSortOrders() = runBlocking {
        val store = freshStore()
        (0 until 25).map { i ->
            async(Dispatchers.Default) {
                store.addContact(ContactEntity(name = "Contact $i", phone = "1"))
                store.addDestination(DestinationEntity(name = "Place $i"))
            }
        }.awaitAll()
        assertEquals((0 until 25).toList(), store.contacts().map { it.sortOrder }.sorted())
        assertEquals((0 until 25).toList(), store.destinations().map { it.sortOrder }.sorted())
    }

    @Test
    fun concurrentProfileSavesKeepEachOthersFields() = runBlocking {
        val store = freshStore()
        val fields = listOf("nickname", "height", "weight", "hairColor", "eyeColor", "likes", "allergies", "safeWord")
        fields.map { field -> async(Dispatchers.Default) { store.updateProfile(mapOf(field to "set")) } }.awaitAll()
        val profile = store.profile()!!
        assertEquals(
            listOf("set", "set", "set", "set", "set", "set", "set", "set"),
            listOf(
                profile.nickname, profile.height, profile.weight, profile.hairColor, profile.eyeColor,
                profile.likes, profile.allergies, profile.safeWord,
            ),
        )
    }
}
