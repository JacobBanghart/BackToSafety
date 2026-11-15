package com.backtosafety.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.profile.ProfileScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.app.core.Analytics
import com.backtosafety.core.ffi.ProfileField
import com.backtosafety.app.core.Translations
import com.backtosafety.app.core.Store
import com.backtosafety.core.ffi.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/**
 * A failed read of the saved profile is not an empty profile: the screen says so with Retry and
 * keeps Save disabled, so an empty form can never be written over the stored one (issue #6).
 * Unsaved edits survive the screen being recreated (a font-size change, process death): they
 * still show, still count as unsaved, and the store isn't read again over them (issue #7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val translations = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                    .associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }

    private val name = "Margaret Smith"
    private val condition = "Alzheimer's, moderate"

    private lateinit var db: AppDatabase
    private lateinit var store: Store
    private var left = 0
    private var saved = 0

    /** What the screen reported, in order. */
    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()

    private fun events(name: String) = tracked.filter { it.first == name }.map { it.second }

    @Before
    fun setUp() {
        Analytics.sink = { event, properties -> tracked += event to properties }
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking { store.seed() }
    }

    @After
    fun tearDown() {
        Analytics.sink = { _, _ -> }
        db.close()
    }

    private fun seedProfile() = runBlocking {
        store.updateProfile(mapOf(ProfileField.FULL_NAME to name, ProfileField.MEDICAL_CONDITIONS to condition))
    }

    /** The profile table gone, so reading the profile fails; [fixProfile] brings it back. */
    private fun breakProfile() = renameProfile("profile", "profile_away")

    private fun fixProfile() = renameProfile("profile_away", "profile")

    private fun renameProfile(from: String, to: String) = runBlocking {
        db.useWriterConnection { it.usePrepared("ALTER TABLE $from RENAME TO $to") { statement -> statement.step() } }
    }

    private val screen = @Composable {
        AppTheme(dark = false) {
            ProfileScreen(translations.translator("en", "profile"), translations.translator("en", "common"), store, CoroutineScope(Dispatchers.Unconfined), { left++ }, { saved++ })
        }
    }

    private fun showScreen() {
        compose.setContent(screen)
        compose.waitForIdle()
    }

    /** The screen, shown so a test can recreate it with its saved state, as the activity is. */
    private fun showRestorableScreen() = StateRestorationTester(compose).apply {
        setContent(screen)
        compose.waitForIdle()
    }

    private fun savedProfile() = runBlocking { store.profile() }

    /** Saves, and waits for the screen to say it has. */
    private fun save() {
        compose.onNodeWithTag("profile-save").performClick()
        compose.waitUntil(5_000) { saved == 1 }
    }

    @Test
    fun aFailedReadShowsTheErrorAndSaveWritesNothing() {
        seedProfile()
        breakProfile()
        showScreen()
        compose.onNodeWithTag("profile-load-failed").assertExists()
        compose.onNodeWithTag("profile-field-name").assertDoesNotExist()
        assertEquals(listOf("profile" to "load"), events("save_failed").map { it["screen"] to it["action"] })

        compose.onNodeWithTag("profile-save").assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertTrue("Save wrote the empty form", events("profile_saved").isEmpty())
        assertTrue(events("save_failed").none { it["action"] == "save" })
        assertEquals(0, saved)

        fixProfile()
        val profile = runBlocking { store.profile() }!!
        assertEquals(name, profile.name)
        assertEquals(condition, profile.medicalConditions)
    }

    @Test
    fun retryWithTheStoreBackShowsTheSavedProfile() {
        seedProfile()
        breakProfile()
        showScreen()
        compose.onNodeWithTag("profile-load-failed").assertExists()

        // A retry that fails again keeps the error and reports it again.
        compose.onNodeWithTag("profile-load-retry").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("profile-load-failed").assertExists()
        assertEquals(2, events("save_failed").count { it["action"] == "load" })

        fixProfile()
        compose.onNodeWithTag("profile-load-retry").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("profile-load-failed").assertDoesNotExist()
        compose.onNodeWithTag("profile-field-name").assert(hasText(name))
        compose.onNodeWithTag("profile-save").assertIsEnabled()
        assertEquals(2, events("save_failed").size)
    }

    @Test
    fun theErrorCanBeLeftWithoutAPrompt() {
        seedProfile()
        breakProfile()
        showScreen()
        compose.onNodeWithTag("profile-load-failed").assertExists()
        compose.onNodeWithTag("profile-back").performClick()
        assertEquals(1, left)
        assertNull("Back asked about unsaved changes", ShadowAlertDialog.getLatestAlertDialog())

        // The system back leaves too: nothing on the screen holds it.
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun noProfileYetIsAnEmptyFormNotAnError() {
        showScreen()
        compose.onNodeWithTag("profile-field-name").assert(hasText(""))
        compose.onNodeWithTag("profile-load-failed").assertDoesNotExist()
        compose.onNodeWithTag("profile-save").assertIsEnabled()
        assertTrue(events("save_failed").isEmpty())
    }

    @Test
    fun anEditSurvivesRecreationAndStillAsksBeforeLeaving() {
        seedProfile()
        val tester = showRestorableScreen()
        compose.onNodeWithTag("profile-field-name").performTextReplacement("Maggie Smith")

        tester.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("profile-field-name").assert(hasText("Maggie Smith"))

        // The edit is still unsaved: the header's Back asks before discarding it.
        compose.onNodeWithTag("profile-back").performClick()
        assertEquals(0, left)
        val headerPrompt = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Back left without asking", headerPrompt)
        headerPrompt.dismiss()

        // And so does the system back, without finishing the activity.
        compose.activity.onBackPressedDispatcher.onBackPressed()
        val systemPrompt = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame("the system back didn't ask", headerPrompt, systemPrompt)
        assertTrue(systemPrompt.isShowing)
        assertFalse(compose.activity.isFinishing)
        systemPrompt.dismiss()

        save()
        assertEquals("Maggie Smith", savedProfile()?.name)
    }

    @Test
    fun theOpenSectionAndChosenOptionsSurviveRecreation() {
        seedProfile()
        val tester = showRestorableScreen()
        compose.onNodeWithTag("profile-section-medical").performScrollTo().performClick()
        compose.onNodeWithTag("profile-field-medications").performScrollTo().performTextInput("Donepezil 10mg")
        compose.onNodeWithTag("profile-hand-left").performScrollTo().performClick()
        compose.onNodeWithTag("profile-mobility-uses-cane").performScrollTo().performClick()
        compose.onNodeWithTag("profile-mobility-other").performScrollTo().performClick()
        compose.onNodeWithTag("profile-mobility-other-text").performScrollTo().performTextInput("Tires after a block")

        tester.emulateSavedInstanceStateRestore()

        // The medical section is still the open one (personal, the default, is closed), with
        // everything chosen in it. The Other text only shows while Other is chosen; the chosen
        // hand and options show only as colour, so Save below is what proves they were kept.
        compose.onNodeWithTag("profile-field-medications").performScrollTo().assert(hasText("Donepezil 10mg"))
        compose.onNodeWithTag("profile-field-name").assertDoesNotExist()
        compose.onNodeWithTag("profile-mobility-other-text").performScrollTo().assert(hasText("Tires after a block"))

        save()
        val profile = savedProfile()
        assertEquals("Donepezil 10mg", profile?.medications)
        assertEquals("left", profile?.dominantHand)
        assertEquals("Uses cane, Tires after a block", profile?.mobilityLevel)
    }

    @Test
    fun aRestoredEditIsNotReplacedByReadingTheStoreAgain() {
        seedProfile()
        val tester = showRestorableScreen()
        compose.onNodeWithTag("profile-field-name").performTextReplacement("Maggie Smith")
        breakProfile()

        tester.emulateSavedInstanceStateRestore()

        // A read would fail now, so the error showing would mean the store was read again.
        compose.onNodeWithTag("profile-field-name").assert(hasText("Maggie Smith"))
        compose.onNodeWithTag("profile-load-failed").assertDoesNotExist()
        assertTrue("the store was read again", events("save_failed").none { it["action"] == "load" })

        fixProfile()
        save()
        assertEquals("Maggie Smith", savedProfile()?.name)
    }

    @Test
    fun aRecreatedScreenWithNoEditsLeavesWithoutAPrompt() {
        seedProfile()
        val tester = showRestorableScreen()
        compose.onNodeWithTag("profile-field-name").assert(hasText(name))

        tester.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("profile-back").performClick()
        assertEquals(1, left)
        assertNull("Back asked about unsaved changes", ShadowAlertDialog.getLatestAlertDialog())
    }
}
