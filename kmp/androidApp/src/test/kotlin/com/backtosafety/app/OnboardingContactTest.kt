package com.backtosafety.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.onboarding.ContactScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The onboarding contact step: a phone that can't be texted is refused, and Continue never adds a second contact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class OnboardingContactTest {
    @get:Rule val compose = createComposeRule()

    private val translations = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                    .associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }

    private lateinit var db: AppDatabase
    private lateinit var store: Store
    private var continued = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking { store.seed() }
        compose.setContent {
            AppTheme(dark = false) {
                ContactScreen(translations.translator("en", "onboarding"), translations.translator("en", "common"), store, {}) { continued++ }
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun type(tag: String, text: String) = compose.onNodeWithTag(tag).performTextInput(text)

    private fun tapContinue() {
        compose.onNodeWithTag("onboarding-contact-continue").performClick()
        compose.waitForIdle()
    }

    @Test
    fun aPhoneWithNoDigitsIsNotSaved() = runBlocking<Unit> {
        type("onboarding-contact-name", "Rosa Diaz")
        type("onboarding-contact-phone", "+")
        tapContinue()
        assertEquals(emptyList<Any>(), store.contacts())
        assertEquals(0, continued)
    }

    @Test
    fun continuingAgainEditsTheSavedContactInsteadOfAddingAnother() = runBlocking<Unit> {
        type("onboarding-contact-name", "Rosa Diaz")
        type("onboarding-contact-phone", "5559876543")
        tapContinue()
        tapContinue() // as after Back from the next screen
        assertEquals(2, continued)
        val saved = store.contacts().single()
        assertEquals("Rosa Diaz", saved.name)
        assertEquals("primary_caregiver", saved.role)

        type("onboarding-contact-name", " Jr")
        tapContinue()
        assertEquals("Rosa Diaz Jr", store.contacts().single().name)
    }
}
