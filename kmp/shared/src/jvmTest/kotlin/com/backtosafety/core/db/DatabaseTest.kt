package com.backtosafety.core.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.backtosafety.core.parseActiveEmergency
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The L5 contract at unit level: the Kotlin app must open the database the RN app left
 * behind (spec/storage.md) with every row intact. Uses the committed RN fixture,
 * spec/fixtures/upgrade/android/SQLite/nijii.db.
 */
class DatabaseTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))
    private val tables = listOf("profile", "contacts", "destinations", "incidents", "safety_checks", "settings", "onboarding")

    private fun rnFixtureCopy(): String {
        val dir = Files.createTempDirectory("nijii").toFile()
        val db = File(dir, DATABASE_NAME)
        File(root, "spec/fixtures/upgrade/android/SQLite/nijii.db").copyTo(db)
        return db.absolutePath
    }

    /** Every row of [table] as column -> value text, in rowid order. */
    private fun rows(path: String, table: String): List<Map<String, String?>> =
        BundledSQLiteDriver().open(path).use { db -> rows(db, table) }

    private fun rows(db: SQLiteConnection, table: String): List<Map<String, String?>> =
        db.prepare("SELECT * FROM `$table` ORDER BY rowid").use { st ->
            buildList {
                while (st.step()) add((0 until st.getColumnCount()).associate { st.getColumnName(it) to st.text(it) })
            }
        }

    private fun SQLiteStatement.text(i: Int): String? = if (isNull(i)) null else getText(i)

    @Test
    fun rnDatabaseMigratesWithEveryRowIntact() = runBlocking {
        val path = rnFixtureCopy()
        val before = tables.associateWith { rows(path, it) }
        assertTrue(before.getValue("profile").isNotEmpty(), "fixture has a profile")

        val db = openAppDatabase(path, databaseBuilder(path))
        db.profile().get() // first use runs MIGRATION_1_2 and Room's schema validation
        db.close()

        for (table in tables) {
            assertEquals(before.getValue(table), rows(path, table), "rows of $table after migration")
        }
        val userVersion = BundledSQLiteDriver().open(path).use { c ->
            c.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) }
        }
        assertEquals(2L, userVersion)
    }

    @Test
    fun appReadsTheRnData() = runBlocking {
        val path = rnFixtureCopy()
        val deviceIdBefore = rows(path, "settings").single { it["key"] == "device_id" }["value"]
        val db = openAppDatabase(path, databaseBuilder(path))

        val profile = assertNotNull(db.profile().get())
        assertEquals("Margaret Smith", profile.name)
        assertEquals("Maggie", profile.nickname)
        assertEquals("03/15/1940", profile.dateOfBirth)
        assertEquals("left", profile.dominantHand)
        assertEquals("Uses cane", profile.mobilityLevel)

        val contact = db.contacts().all().single()
        assertEquals("John Smith", contact.name)
        assertTrue(contact.notifyOnEmergency)
        assertEquals("Riverside Park", db.destinations().all().single().name)

        assertEquals(deviceIdBefore, db.settings().get("device_id"), "device_id survives")
        assertEquals("dark", db.settings().get("theme_preference"))
        val emergency = assertNotNull(parseActiveEmergency(db.settings().get("active_emergency")))
        assertEquals(listOf("home_search", "neighbors"), emergency.checkedSteps)
        assertEquals("Blue jacket", emergency.wearing)
        val incident = db.incidents().all().single()
        assertEquals("ongoing", incident.outcome)
        assertEquals(incident.id, emergency.incidentId)

        assertTrue(db.onboarding().isComplete(), "onboarding is not shown again")
        db.close()
    }

    @Test
    fun migratedDatabaseReopensWithoutMigrating() = runBlocking {
        val path = rnFixtureCopy()
        openAppDatabase(path, databaseBuilder(path)).also { it.profile().get() }.close()
        val again = openAppDatabase(path, databaseBuilder(path))
        assertEquals("Margaret Smith", again.profile().get()?.name)
        again.close()
    }

    @Test
    fun freshInstallCreatesVersionTwo() = runBlocking {
        val path = File(Files.createTempDirectory("nijii").toFile(), DATABASE_NAME).absolutePath
        val db = openAppDatabase(path, databaseBuilder(path))
        db.profile().save(ProfileEntity(name = "Ana"))
        assertEquals("Ana", db.profile().get()?.name)
        db.close()
        val userVersion = BundledSQLiteDriver().open(path).use { c ->
            c.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) }
        }
        assertEquals(2L, userVersion)
    }
}
