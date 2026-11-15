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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.onboarding.PhotoScreen
import com.backtosafety.app.profile.ProfileScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.app.core.Analytics
import com.backtosafety.app.core.Translations
import com.backtosafety.app.core.Store
import com.backtosafety.core.ffi.AppDatabase
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
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
 * Saving a picked photo into filesDir happens off the main thread, and a save that fails is
 * neither dropped nor silent: the screen shows "Failed to save photo", save_failed is reported
 * once with action `photo`, the photo on screen stays as it was, and no partial photo or camera
 * and cropper temp file is left behind (issue #9). The copy is made to fail with a cropper that
 * hands back a URI that won't open, which takes the same path as a full disk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class PhotoSaveTest {
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
    private var left = 0
    private var continued = 0

    /** What the screen reported, in order. */
    private val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()

    private fun events(name: String) = tracked.filter { it.first == name }.map { it.second }

    /**
     * Stands in for the activity's registry. The camera writes its shot and the cropper writes its
     * square where they were told to (camera_* and cropped_* in cacheDir), and both answer at
     * once. With [unreadable], the cropper hands back a URI to a file that isn't there.
     */
    private inner class Registry(private val unreadable: Boolean) : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            when (contract) {
                is ActivityResultContracts.TakePicture -> {
                    jpeg(checkNotNull((input as Uri).lastPathSegment) { "the camera was given a file to write" })
                    dispatchResult(requestCode, Activity.RESULT_OK, null)
                }
                is CropImageContract -> {
                    val target = (input as CropImageContractOptions).cropImageOptions.customOutputUri
                    val written = jpeg(checkNotNull(target?.lastPathSegment) { "the cropper was given a file to write" })
                    val output = Uri.fromFile(if (unreadable) File(context.cacheDir, "missing.jpg") else written)
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
    }

    @After
    fun tearDown() {
        Analytics.sink = { _, _ -> }
        appScope.cancel()
        db.close()
    }

    private fun show(unreadable: Boolean, screen: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Owner(Registry(unreadable))) {
                AppTheme(dark = false) { screen() }
            }
        }
        compose.waitForIdle()
    }

    private fun showOnboarding(unreadable: Boolean) = show(unreadable) {
        PhotoScreen(translations.translator("en", "onboarding"), translations.translator("en", "common"), store, appScope, {}) { continued++ }
    }

    private fun showProfile(unreadable: Boolean) = show(unreadable) {
        ProfileScreen(translations.translator("en", "profile"), translations.translator("en", "common"), store, appScope, { left++ }, {})
    }

    /** The save finishes on another thread; idling lets its result reach the main thread. */
    private fun waitForAlert() = compose.waitUntil(5_000) {
        compose.waitForIdle()
        ShadowAlertDialog.getLatestAlertDialog() != null
    }

    /** The failure was shown and reported once, and nothing it wrote is left behind. */
    private fun assertPhotoSaveFailed(screen: String) {
        val dialog = shadowOf(checkNotNull(ShadowAlertDialog.getLatestAlertDialog()) { "an alert showed" })
        assertEquals("Error", dialog.title)
        assertEquals("Failed to save photo. Please try again.", dialog.message)
        val reported = events("save_failed")
        assertEquals(listOf(screen to "photo"), reported.map { it["screen"] to it["action"] })
        assertTrue("the error's type is reported", (reported.single()["error"] as? String).orEmpty().isNotEmpty())
        assertEquals(emptyList<String>(), files(context.filesDir, "profile_photo_"))
        assertEquals(emptyList<String>(), files(context.cacheDir, "camera_", "cropped_"))
    }

    private fun files(dir: File, vararg prefixes: String) =
        dir.listFiles().orEmpty().map { it.name }.filter { name -> prefixes.any { name.startsWith(it) } }

    @Test
    fun onboardingShowsAFailedPhotoSaveAndKeepsNoPhoto() {
        showOnboarding(unreadable = true)
        compose.onNodeWithTag("onboarding-photo-take").performClick()
        waitForAlert()

        assertPhotoSaveFailed("onboarding_photo")
        compose.onNodeWithTag("onboarding-photo-continue").assertIsNotEnabled()
    }

    @Test
    fun profileShowsAFailedPhotoSaveAndKeepsTheFormAsItWas() {
        showProfile(unreadable = true)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("profile-photo-take").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-photo-take").performClick()
        waitForAlert()

        assertPhotoSaveFailed("profile")
        compose.onAllNodesWithTag("profile-photo").fetchSemanticsNodes().let { assertTrue("no photo shows", it.isEmpty()) }
        // Nothing changed, so Back leaves without asking about unsaved changes.
        compose.onNodeWithTag("profile-back").performClick()
        assertEquals(1, left)
    }

    @Test
    fun onboardingSavesAPickedPhotoIntoFilesDir() {
        showOnboarding(unreadable = false)
        compose.onNodeWithTag("onboarding-photo-take").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("onboarding-photo-continue") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(emptyList<String>(), files(context.cacheDir, "camera_", "cropped_"))

        compose.onNodeWithTag("onboarding-photo-continue").performClick()
        compose.waitUntil(5_000) { continued == 1 }
        val uri = runBlocking { store.profile()?.photoUri }
        assertNotNull("the photo was saved to the profile", uri)
        val file = File(checkNotNull(Uri.parse(uri).path))
        assertEquals(context.filesDir, file.parentFile)
        assertTrue(file.exists())
        assertTrue(events("save_failed").isEmpty())
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }
}
