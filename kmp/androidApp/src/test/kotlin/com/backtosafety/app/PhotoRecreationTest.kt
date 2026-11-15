package com.backtosafety.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.onboarding.PhotoScreen
import com.backtosafety.app.profile.ProfileScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.ProfileField
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A photo survives the app being recreated mid-flow. The system can reclaim the app while the
 * cropper has the screen, and the cropped photo then arrives as Profile is recreated, into the
 * form restored with it (alongside any unsaved typing). It can also reclaim the app while the camera has the screen, so the picker must
 * remember across process death where the camera writes, or the shot never reaches the crop
 * screen. Onboarding keeps a picked photo when its screen is recreated (process death, or a
 * font-size change; rotation doesn't recreate MainActivity). Found reproducing issue #3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class PhotoRecreationTest {
    @get:Rule val compose = createComposeRule()

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

    /**
     * Stands in for the activity's registry. The library, the camera and the cropper answer at
     * once. With [holdCamera] or [holdCrop], that request is kept unanswered instead, as when the
     * app is reclaimed while the camera or the crop screen is open. What the camera and the
     * cropper were launched with is recorded, so a test can check which file went where.
     */
    private inner class Registry(private val holdCamera: Boolean = false, private val holdCrop: Boolean = false) : ActivityResultRegistry() {
        var cameraRequest: Int? = null
        var cameraInput: Uri? = null
        var cropRequest: Int? = null
        var cropInput: Uri? = null

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            when (contract) {
                is ActivityResultContracts.TakePicture -> {
                    cameraInput = input as Uri
                    if (holdCamera) cameraRequest = requestCode else dispatchResult(requestCode, Activity.RESULT_OK, null)
                }
                is ActivityResultContracts.PickVisualMedia -> dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(jpeg("picked.jpg"))))
                is CropImageContract -> {
                    cropInput = (input as CropImageContractOptions).uri
                    if (holdCrop) cropRequest = requestCode else dispatchResult(requestCode, Activity.RESULT_OK, cropped())
                }
                else -> error("The photo flow launched an activity this test doesn't stand in for: $contract")
            }
        }
    }

    private class Owner(override val activityResultRegistry: ActivityResultRegistry) : ActivityResultRegistryOwner

    private fun jpeg(name: String) = File(context.cacheDir, name).apply {
        outputStream().use { Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    /** What the crop screen returns: the square it wrote. */
    private fun cropped(): Intent {
        val output = Uri.fromFile(jpeg("crop_result.jpg"))
        return Intent().putExtra(
            CropImage.CROP_IMAGE_EXTRA_RESULT,
            CropImage.ActivityResult(output, output, null, floatArrayOf(), Rect(), 0, Rect(), 1),
        )
    }

    @Before
    fun setUp() {
        // FileProvider caches its roots in a static map, and Robolectric gives each test a new
        // data directory: a root cached by an earlier test refuses this test's cache files.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking {
            store.seed()
            store.updateProfile(mapOf(ProfileField.FULL_NAME to "Margaret Smith"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun savedPhoto(): String? = runBlocking { store.profile()?.photoUri }

    private fun assertIsSavedPhoto(uri: String?) {
        assertNotNull("the photo was saved to the profile", uri)
        val file = File(Uri.parse(uri).path!!)
        assertEquals(context.filesDir, file.parentFile)
        assertTrue(file.exists())
    }

    @Test
    fun aPhotoCroppedAfterTheAppWasReclaimedReachesTheProfile() {
        var registry = Registry(holdCrop = true)
        var left = 0
        val tester = StateRestorationTester(compose)
        tester.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Owner(registry)) {
                AppTheme(dark = false) {
                    ProfileScreen(translations.translator("en", "profile"), translations.translator("en", "common"), store, { left++ }, {})
                }
            }
        }
        compose.onNodeWithTag("profile-photo-take").performClick()
        val cropRequest = checkNotNull(registry.cropRequest) { "the crop screen opened" }

        // The app is reclaimed with the crop screen open. When it comes back, the cropper's result
        // is waiting and is handed over as soon as the screen asks for it, into Profile's
        // restored form.
        val saved = Bundle().also(registry::onSaveInstanceState)
        registry = Registry().apply {
            onRestoreInstanceState(saved)
            dispatchResult(cropRequest, Activity.RESULT_OK, cropped())
        }
        tester.emulateSavedInstanceStateRestore()

        // The photo is an unsaved edit: Back asks before discarding it rather than leaving.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("profile-photo-take").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-back").performClick()
        assertEquals(0, left)

        compose.onNodeWithTag("profile-save").performClick()
        compose.waitUntil(5_000) { savedPhoto() != null }
        assertIsSavedPhoto(savedPhoto())
    }

    @Test
    fun typingAndAPhotoCroppedAfterTheAppWasReclaimedBothReachTheProfile() {
        var registry = Registry(holdCrop = true)
        var left = 0
        val tester = StateRestorationTester(compose)
        tester.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Owner(registry)) {
                AppTheme(dark = false) {
                    ProfileScreen(translations.translator("en", "profile"), translations.translator("en", "common"), store, { left++ }, {})
                }
            }
        }
        compose.onNodeWithTag("profile-field-name").performTextReplacement("Maggie Smith")
        compose.onNodeWithTag("profile-photo-take").performClick()
        val cropRequest = checkNotNull(registry.cropRequest) { "the crop screen opened" }

        // The app is reclaimed with the crop screen open, and the crop is done when it comes back.
        val saved = Bundle().also(registry::onSaveInstanceState)
        registry = Registry().apply {
            onRestoreInstanceState(saved)
            dispatchResult(cropRequest, Activity.RESULT_OK, cropped())
        }
        tester.emulateSavedInstanceStateRestore()

        // Neither the typing nor the photo is lost, and both are unsaved: Back asks first.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("profile-photo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-field-name").assert(hasText("Maggie Smith"))
        compose.onNodeWithTag("profile-back").performClick()
        assertEquals(0, left)

        compose.onNodeWithTag("profile-save").performClick()
        compose.waitUntil(5_000) { savedPhoto() != null }
        assertIsSavedPhoto(savedPhoto())
        assertEquals("Maggie Smith", runBlocking { store.profile()?.name })
    }

    @Test
    fun aPhotoTakenAfterTheAppWasReclaimedWithTheCameraOpenReachesTheProfile() {
        var registry = Registry(holdCamera = true)
        val tester = StateRestorationTester(compose)
        tester.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Owner(registry)) {
                AppTheme(dark = false) {
                    ProfileScreen(translations.translator("en", "profile"), translations.translator("en", "common"), store, {}, {})
                }
            }
        }
        compose.onNodeWithTag("profile-photo-take").performClick()
        val cameraRequest = checkNotNull(registry.cameraRequest) { "the camera opened" }
        val shot = checkNotNull(registry.cameraInput?.lastPathSegment) { "the camera was given a file to write" }
        jpeg(shot)

        // The app is reclaimed with the camera open. When it comes back, the camera's result is
        // waiting, and only the path the picker saved says which file the shot is in.
        val saved = Bundle().also(registry::onSaveInstanceState)
        registry = Registry().apply {
            onRestoreInstanceState(saved)
            dispatchResult(cameraRequest, Activity.RESULT_OK, null)
        }
        tester.emulateSavedInstanceStateRestore()

        compose.waitUntil(5_000) { registry.cropInput != null }
        val source = checkNotNull(registry.cropInput)
        assertEquals("file", source.scheme)
        assertEquals(File(context.cacheDir, shot), File(source.path!!))

        // Save does nothing until the form has loaded, and the header's Save button is drawn
        // before it, so wait for a control that only the loaded form has. The photo then shows.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("profile-photo-take").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("profile-photo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile-save").performClick()
        compose.waitUntil(5_000) { savedPhoto() != null }
        assertIsSavedPhoto(savedPhoto())
    }

    @Test
    fun aPhotoPickedDuringOnboardingSurvivesItsScreenBeingRecreated() {
        val tester = StateRestorationTester(compose)
        var continued = 0
        tester.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Owner(Registry())) {
                AppTheme(dark = false) {
                    PhotoScreen(translations.translator("en", "onboarding"), translations.translator("en", "common"), store, {}) { continued++ }
                }
            }
        }
        compose.onNodeWithTag("onboarding-photo-library").performClick()
        compose.onNodeWithTag("onboarding-photo-continue").assertIsEnabled()

        tester.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("onboarding-photo-continue").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { continued == 1 }
        assertIsSavedPhoto(savedPhoto())
    }
}
