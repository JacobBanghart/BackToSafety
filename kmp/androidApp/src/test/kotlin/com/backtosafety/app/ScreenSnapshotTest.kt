package com.backtosafety.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.contacts.ContactsScreen
import com.backtosafety.app.destinations.DestinationsScreen
import com.backtosafety.app.emergency.EmergencyScreen
import com.backtosafety.app.home.HomeScreen
import com.backtosafety.app.onboarding.WelcomeScreen
import com.backtosafety.app.profile.ProfileScreen
import com.backtosafety.app.readout.ReadoutScreen
import com.backtosafety.app.settings.SettingsScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.AppClock
import com.backtosafety.core.Translate
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.db.DestinationEntity
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Every main screen rendered on the JVM (Robolectric) and compared with its reference image in
 * src/test/snapshots, in light, dark and large text. Seconds, no device: a visual change shows
 * up here first; the Maestro fidelity captures still hold the app to the goldens on devices.
 * `./gradlew :androidApp:testDebugUnitTest -Precord` re-records after a deliberate change.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenSnapshotTest(private val mode: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf("light"), arrayOf("dark"), arrayOf("large-text"))

        private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

        private val translations: Translations by lazy {
            val locales = File(root, "i18n/locales")
            Translations.fromJson(
                locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                    locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                        .associate { it.nameWithoutExtension to it.readText() }
                },
            )
        }

        /** 2026-10-06 16:00 UTC. */
        private const val NOW_MS = 1_791_302_400_000L
    }

    @get:Rule val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var store: Store

    private fun t(namespace: String): Translate = translations.translator("en", namespace)

    @Before
    fun setUp() {
        // The same on every machine: times render in UTC, US English.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        AppClock.testSeamsEnabled = true
        AppClock.freeze(NOW_MS)
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking {
            store.seed()
            store.updateProfile(
                mapOf(
                    "name" to "Margaret Smith", "nickname" to "Maggie", "dateOfBirth" to "03/15/1940",
                    "height" to "5'6\"", "medicalConditions" to "Moderate Alzheimer's", "dominantHand" to "left",
                ),
            )
            store.addContact(ContactEntity(name = "John Smith", phone = "(555) 123-4567", role = "primary_caregiver"))
            store.addDestination(DestinationEntity(name = "Riverside Park", category = "water", riskLevel = "high", address = "100 River Rd"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            AppTheme(dark = mode == "dark") {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, if (mode == "large-text") 1.3f else 1f),
                ) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/$mode/$name.png")
    }

    @Test fun welcome() = snapshot("welcome") { WelcomeScreen(t("onboarding"), "system", {}, {}) }

    @Test fun home() = snapshot("home") { HomeScreen(t("home"), "911", store) {} }

    @Test fun settings() = snapshot("settings") {
        SettingsScreen(t("settings"), t("common"), store, "system", {}, "en", {}, {}, {})
    }

    @Test fun readout() = snapshot("readout") { ReadoutScreen(t("readout"), t("common"), store) {} }

    @Test fun contacts() = snapshot("contacts") { ContactsScreen(t("contacts"), t("common"), store) {} }

    @Test fun places() = snapshot("places") { DestinationsScreen(t("destinations"), t("common"), store) {} }

    @Test fun profile() = snapshot("profile") { ProfileScreen(t("profile"), t("common"), store, {}, {}) }

    @Test fun emergency() = snapshot("emergency") { EmergencyScreen(t("emergency"), t("common"), store, {}, {}) }
}
