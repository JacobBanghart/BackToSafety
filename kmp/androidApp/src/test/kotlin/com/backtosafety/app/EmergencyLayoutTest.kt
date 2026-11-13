package com.backtosafety.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.backtosafety.app.emergency.EmergencyScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.AlertScheduler
import com.backtosafety.core.AppClock
import com.backtosafety.core.EmergencyAway
import com.backtosafety.core.ProfileField
import com.backtosafety.core.ScheduledAlert
import com.backtosafety.core.Translations
import com.backtosafety.core.buildInitialSteps
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The search checklist is the guided task, so at large text its first step is on screen when the
 * emergency opens, even with the direction hint showing: the countdown doesn't grow with the font
 * scale, and the clothing prompt comes after the first step rather than before the checklist.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics, so text is measured with real fonts (the legacy mode measures it as empty).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class EmergencyLayoutTest {
    @get:Rule val compose = createComposeRule()

    private object NoScheduler : AlertScheduler {
        override fun schedule(alerts: List<ScheduledAlert>) {}
        override fun cancelAll() {}
    }

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

    @Before
    fun setUp() {
        AppClock.testSeamsEnabled = true
        AppClock.freeze(1_791_302_400_000L)
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .allowMainThreadQueries()
            .build()
        store = Store(db)
        runBlocking {
            store.seed()
            store.updateProfile(mapOf(ProfileField.FULL_NAME to "Margaret Smith", ProfileField.DOMINANT_HAND to "left"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theFirstStepIsOnScreenAtLargeTextAndTheClothingPromptFollowsIt() {
        val t = translations.translator("en", "emergency")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                AppTheme(dark = false) {
                    EmergencyScreen(
                        t, translations.translator("en", "common"), store, EmergencyAway(store, NoScheduler),
                        CoroutineScope(Dispatchers.Unconfined), {}, {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag("emergency-step-${buildInitialSteps(t, "911").first().id}").getUnclippedBoundsInRoot()
        assertTrue("the first step starts at ${first.top}, below the screen (${screen.bottom})", first.top < screen.bottom)
        val wearing = compose.onNodeWithTag("emergency-wearing-input").getUnclippedBoundsInRoot()
        assertTrue("the clothing prompt (${wearing.top}) comes before the first step (${first.bottom})", wearing.top > first.bottom)
    }
}
