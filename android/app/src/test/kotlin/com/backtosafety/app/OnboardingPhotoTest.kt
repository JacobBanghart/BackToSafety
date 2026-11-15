package com.backtosafety.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.onboarding.PhotoScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.app.core.Analytics
import com.backtosafety.app.core.Translations
import com.backtosafety.app.core.Store
import com.backtosafety.core.ffi.AppDatabase
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/**
 * The onboarding photo step when a write fails: Continue and Skip report save_failed, show the
 * save-failed alert and stay on the step with the picked photo, so the user can try again, and
 * the step is recorded as completed or skipped only once its write succeeded (issue #8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class OnboardingPhotoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val translations = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                    .associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var db: AppDatabase
    private lateinit var store: Store
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var continued = 0

    /** What the screen reported, in order. */
    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()

    private fun events(name: String) = tracked.filter { it.first == name }.map { it.second }

    /** Stands in for the activity's registry: the library and the cropper answer at once. */
    private inner class Registry : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            when (contract) {
                is ActivityResultContracts.PickVisualMedia -> dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(jpeg("picked.jpg"))))
                is CropImageContract -> {
                    val output = Uri.fromFile(jpeg("crop_result.jpg"))
                    dispatchResult(
                        requestCode, Activity.RESULT_OK,
                        Intent().putExtra(
                            CropImage.CROP_IMAGE_EXTRA_RESULT,
                            CropImage.ActivityResult(output, output, null, floatArrayOf(), Rect(), 0, Rect(), 1),
                        ),
                    )
                }
                else -> error("The photo flow launched an activity this test doesn't stand in for: $contract")
            }
        }
    }

    private class Owner(override val activityResultRegistry: ActivityResultRegistry) : ActivityResultRegistryOwner

    private fun jpeg(name: String) = File(context.cacheDir, name).apply {
        outputStream().use { Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    @Before
    fun setUp() {
        // FileProvider caches its roots in a static map, and Robolectric gives each test a new
        // data directory: a root cached by an earlier test refuses this test's cache files.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
        Analytics.sink = { event, properties -> tracked += event to properties }
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking { store.seed() }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Owner(Registry())) {
                AppTheme(dark = false) {
                    PhotoScreen(translations.translator("en", "onboarding"), translations.translator("en", "common"), store, appScope, {}) { continued++ }
                }
            }
        }
    }

    @After
    fun tearDown() {
        Analytics.sink = { _, _ -> }
        appScope.cancel()
        db.close()
    }

    /** Renaming a table away makes a write to it fail; renaming it back restores it. */
    private fun rename(from: String, to: String) = runBlocking {
        db.useWriterConnection { it.usePrepared("ALTER TABLE $from RENAME TO $to") { statement -> statement.step() } }
    }

    private fun pickPhoto() {
        compose.onNodeWithTag("onboarding-photo-library").performClick()
        // The photo is saved off the main thread, so it arrives a moment after the crop.
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("onboarding-photo-continue") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun assertSaveFailedAlert() {
        val dialog = shadowOf(checkNotNull(ShadowAlertDialog.getLatestAlertDialog()) { "an alert showed" })
        assertEquals("Error", dialog.title)
        assertEquals("Failed to save. Please try again.", dialog.message)
    }

    /** Continue failed: the alert, no step forward, Continue usable again and the failure reported once. */
    private fun assertContinueFailed() {
        assertSaveFailedAlert()
        assertEquals(0, continued)
        compose.onNodeWithTag("onboarding-photo-continue").assertIsEnabled().assert(hasText("Continue"))
        assertEquals(listOf("onboarding_photo" to "save"), events("save_failed").map { it["screen"] to it["action"] })
        assertTrue(events("onboarding_step_completed").isEmpty())
    }

    @Test
    fun continueWhenTheProfileWriteFailsStaysOnTheStep() {
        pickPhoto()
        rename("profile", "profile_away")
        tap("onboarding-photo-continue")
        assertContinueFailed()
    }

    @Test
    fun continueWhenTheStepWriteFailsStaysOnTheStep() {
        pickPhoto()
        rename("onboarding", "onboarding_away")
        tap("onboarding-photo-continue")
        assertContinueFailed()
    }

    @Test
    fun continueRetriedWithTheStoreBackSavesThePickedPhoto() {
        pickPhoto()
        rename("profile", "profile_away")
        tap("onboarding-photo-continue")
        assertContinueFailed()

        // The photo picked before the failure is still there: Continue saves it without a new pick.
        rename("profile_away", "profile")
        assertNull(runBlocking { store.profile()?.photoUri })
        tap("onboarding-photo-continue")
        assertEquals(1, continued)
        assertNotNull(runBlocking { store.profile()?.photoUri })
        assertEquals(listOf(mapOf<String, Any?>("step" to "profile_photo")), events("onboarding_step_completed"))
    }

    @Test
    fun skipWhenTheStepWriteFailsStaysOnTheStep() {
        rename("onboarding", "onboarding_away")
        tap("onboarding-photo-skip")
        assertSaveFailedAlert()
        assertEquals(0, continued)
        assertEquals(listOf("onboarding_photo" to "skip"), events("save_failed").map { it["screen"] to it["action"] })
        assertTrue(events("onboarding_step_skipped").isEmpty())
    }
}
