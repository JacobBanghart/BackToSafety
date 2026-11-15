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
import com.backtosafety.app.core.AppClock
import com.backtosafety.app.emergency.CountdownNotifications

class MainActivity : ComponentActivity() {
    /** Counts taps on countdown alert notifications; App opens the emergency for each. */
    private val openEmergencyRequests = mutableIntStateOf(0)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BackToSafetyApp
        handleAlertTap(intent)
        // The core and the database open off the main thread, once per process (begun in
        // BackToSafetyApp.onCreate); the splash screen holds until they have (at once, for an
        // activity recreated in a running process). A test-seam link is applied before the
        // first screen reads the clock.
        app.start(clockLink = intent?.data?.toString())
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
     * `?advance=<seconds>` moves it forward (the core parses it). The current screen stays; it
     * re-reads the clock.
     */
    private fun handleTestSeam(intent: Intent?) {
        AppClock.handleLink(intent?.data?.toString() ?: return)
    }
}
