package com.backtosafety.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.backtosafety.app.emergency.CountdownNotifications
import com.backtosafety.core.AppClock

class MainActivity : ComponentActivity() {
    /** Counts taps on countdown alert notifications; App opens the emergency for each. */
    private val openEmergencyRequests = mutableIntStateOf(0)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BackToSafetyApp
        handleTestSeam(intent)
        handleAlertTap(intent)
        // The database opens off the main thread, once per process; the splash screen holds
        // until it has (at once, for an activity recreated in a running process).
        app.start()
        splash.setKeepOnScreenCondition { app.services.value == null }
        setContent {
            val services = app.services.collectAsState().value ?: return@setContent
            // testTag(...) values surface as Android resource IDs, matching the RN
            // app's testIDs, so the same Maestro flows drive both (spec/testids.json).
            App(services, Modifier.semantics { testTagsAsResourceId = true }, openEmergencyRequests)
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
        val uri = intent?.data ?: return
        if (uri.host != "debug" || uri.path != "/clock") return
        uri.getQueryParameter("at")?.let { at ->
            // kotlin.time, not java.time: java.time needs API 26 and the app supports 24.
            runCatching { kotlin.time.Instant.parse(at).toEpochMilliseconds() }.getOrNull()
                ?.let(AppClock::freeze)
        }
        uri.getQueryParameter("advance")?.toDoubleOrNull()?.let { AppClock.advance((it * 1000).toLong()) }
    }
}
