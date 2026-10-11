package com.backtosafety.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.backtosafety.app.core.Analytics
import com.backtosafety.app.core.AppClock
import com.backtosafety.app.core.CoreStartup
import com.backtosafety.app.emergency.CountdownNotifications
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class MainActivity : ComponentActivity() {
    /** Counts taps on countdown alert notifications; App opens the emergency for each. */
    private val openEmergencyRequests = mutableIntStateOf(0)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sink = setUpAnalytics()
        val link = intent?.data?.toString()
        handleAlertTap(intent)
        setContent {
            // The core comes up on its own thread (CoreStartup); nothing shows until it's there,
            // as before, when the first screen waited for the database.
            val core by produceState<CoreStartup.Ready?>(null) {
                val ready = CoreStartup.await()
                AppClock.testSeamsEnabled = BuildConfig.TEST_SEAMS
                sink?.let { Analytics.sink = it }
                link?.let(AppClock::handleLink)
                value = ready
            }
            core?.let {
                // testTag(...) values surface as Android resource IDs, matching the RN
                // app's testIDs, so the same Maestro flows drive both (spec/testids.json).
                App(it.store, it.translations, Modifier.semantics { testTagsAsResourceId = true }, openEmergencyRequests)
            }
        }
    }

    /**
     * utils/posthog.ts: the same PostHog options as the RN app. Without a key (dev and test
     * builds) nothing is set up and every event goes nowhere. Returns the sink the core reports
     * to, which is installed once the core is up.
     */
    private fun setUpAnalytics(): ((String, Map<String, Any?>) -> Unit)? {
        if (BuildConfig.POSTHOG_KEY.isEmpty()) return null
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
        return { name, properties ->
            @Suppress("UNCHECKED_CAST")
            val props = properties.filterValues { it != null } as Map<String, Any>
            if (name == Analytics.SCREEN) {
                PostHog.screen(properties["\$screen_name"] as String, props - "\$screen_name")
            } else PostHog.capture(name, properties = props)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTestSeam(intent)
        handleAlertTap(intent)
    }

    private fun handleAlertTap(intent: Intent?) {
        if (intent?.getBooleanExtra(CountdownNotifications.EXTRA_OPEN_EMERGENCY, false) == true) openEmergencyRequests.intValue++
    }

    /**
     * app/debug/clock.tsx: `backtosafety://debug/clock?at=<ISO time>` freezes the clock,
     * `?advance=<seconds>` moves it forward. The current screen stays; it re-reads the clock.
     */
    private fun handleTestSeam(intent: Intent?) {
        AppClock.handleLink(intent?.data?.toString() ?: return)
    }
}
