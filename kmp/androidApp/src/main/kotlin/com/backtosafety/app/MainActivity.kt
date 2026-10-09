package com.backtosafety.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.backtosafety.core.Analytics
import com.backtosafety.core.AppClock
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.db.databaseBuilder
import com.backtosafety.core.db.databasePath
import com.backtosafety.core.db.openAppDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppClock.testSeamsEnabled = BuildConfig.TEST_SEAMS
        setUpAnalytics()
        handleTestSeam(intent)
        val path = databasePath(this)
        val store = Store(openAppDatabase(path, databaseBuilder(this, path)))
        val translations = loadTranslations()
        setContent {
            // testTag(...) values surface as Android resource IDs, matching the RN
            // app's testIDs, so the same Maestro flows drive both (spec/testids.json).
            App(store, translations, Modifier.semantics { testTagsAsResourceId = true })
        }
    }

    /**
     * utils/posthog.ts: the same PostHog options as the RN app. Without a key (dev and test
     * builds) nothing is set up and every event goes nowhere.
     */
    private fun setUpAnalytics() {
        if (BuildConfig.POSTHOG_KEY.isEmpty()) return
        val config = PostHogAndroidConfig(BuildConfig.POSTHOG_KEY, BuildConfig.POSTHOG_HOST).apply {
            captureApplicationLifecycleEvents = true
            captureScreenViews = false // screens are reported by route, as the RN app does
            preloadFeatureFlags = true
            personProfiles = PersonProfiles.IDENTIFIED_ONLY
            flushAt = 20
            flushIntervalSeconds = 10
            maxBatchSize = 100
            maxQueueSize = 1000
            sessionReplay = true
            sessionReplayConfig.maskAllTextInputs = true
            sessionReplayConfig.maskAllImages = true
            errorTrackingConfig.autoCapture = true
        }
        PostHogAndroid.setup(applicationContext, config)
        Analytics.sink = { name, properties ->
            @Suppress("UNCHECKED_CAST")
            val props = properties.filterValues { it != null } as Map<String, Any>
            if (name == Analytics.SCREEN) {
                PostHog.screen(properties["\$screen_name"] as String, props - "\$screen_name")
            }
            else PostHog.capture(name, properties = props)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTestSeam(intent)
    }

    /**
     * app/debug/clock.tsx: `backtosafety://debug/clock?at=<ISO time>` freezes the clock,
     * `?advance=<seconds>` moves it forward. The current screen stays; it re-reads the clock.
     */
    private fun handleTestSeam(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.host != "debug" || uri.path != "/clock") return
        uri.getQueryParameter("at")?.let { at ->
            runCatching { java.time.OffsetDateTime.parse(at).toInstant().toEpochMilli() }.getOrNull()
                ?.let(AppClock::freeze)
        }
        uri.getQueryParameter("advance")?.toDoubleOrNull()?.let { AppClock.advance((it * 1000).toLong()) }
    }

    /** The RN app's i18n/locales JSON, synced into assets/locales at build time. */
    private fun loadTranslations(): Translations = Translations(
        assets.list("locales")!!.associateWith { locale ->
            assets.list("locales/$locale")!!.associate { file ->
                file.removeSuffix(".json") to Json.parseToJsonElement(
                    assets.open("locales/$locale/$file").bufferedReader().readText(),
                ).jsonObject
            }
        },
    )
}
