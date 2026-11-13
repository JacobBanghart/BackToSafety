package com.backtosafety.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.contacts.ContactsScreen
import com.backtosafety.app.destinations.DestinationsScreen
import com.backtosafety.app.home.HomeScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.Translate
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.db.DestinationEntity
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

/**
 * Icon-only controls are named for TalkBack: the glyph inside is hidden from accessibility
 * (a private-use character reads as noise), so the control's own label ("Editar a Ana") is the
 * only thing it speaks. Each is found here by that label, in Spanish, as a screen reader finds it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class IconButtonLabelsTest {
    @get:Rule val compose = createComposeRule()

    private val translations = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                    .associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }
    private fun t(namespace: String): Translate = translations.translator("es", namespace)

    private lateinit var db: AppDatabase
    private lateinit var store: Store

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking {
            store.seed()
            store.addContact(ContactEntity(name = "Ana", phone = "(555) 123-4567", role = "primary_caregiver"))
            store.addDestination(DestinationEntity(name = "Parque", category = "water", riskLevel = "high"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val isButton = SemanticsMatcher("role is Button") { SemanticsProperties.Role in it.config && it.config[SemanticsProperties.Role] == Role.Button }

    @Test
    fun theHomeAvatarIsAButtonNamedEditProfile() {
        val opened = mutableListOf<String>()
        compose.setContent { AppTheme(dark = false) { HomeScreen(t("home"), "911", store) { opened += it } } }
        compose.onNodeWithContentDescription("Editar perfil").assert(isButton).assertHasClickAction().performClick()
        assertEquals(listOf("profile"), opened)
    }

    @Test
    fun theContactCallAndEditButtonsAreNamedForTheContact() {
        compose.setContent { AppTheme(dark = false) { ContactsScreen(t("contacts"), t("common"), store) {} } }
        compose.onNodeWithContentDescription("Llamar a Ana").assert(isButton).assertHasClickAction()
        compose.onNodeWithContentDescription("Editar a Ana").assert(isButton).assertHasClickAction()
    }

    @Test
    fun thePlaceEditButtonIsNamedForThePlace() {
        compose.setContent { AppTheme(dark = false) { DestinationsScreen(t("destinations"), t("common"), store) {} } }
        compose.onNodeWithContentDescription("Editar Parque").assert(isButton).assertHasClickAction()
    }
}
