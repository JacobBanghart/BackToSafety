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
}
