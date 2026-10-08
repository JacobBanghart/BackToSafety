package com.backtosafety.app

import androidx.compose.foundation.isSystemInDarkTheme
import com.backtosafety.app.ui.trackStep
import com.backtosafety.core.AnalyticsEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.backtosafety.app.contacts.ContactsScreen
import com.backtosafety.app.destinations.DestinationsScreen
import com.backtosafety.app.emergency.EmergencyScreen
import com.backtosafety.app.home.HomeScreen
import com.backtosafety.app.onboarding.AppearanceScreen
import com.backtosafety.app.onboarding.CompleteScreen
import com.backtosafety.app.onboarding.ContactScreen
import com.backtosafety.app.onboarding.NameScreen
import com.backtosafety.app.onboarding.PhotoScreen
import com.backtosafety.app.onboarding.WelcomeScreen
import com.backtosafety.app.profile.ProfileScreen
import com.backtosafety.app.readout.ReadoutScreen
import com.backtosafety.app.settings.SettingsScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.Analytics
import com.backtosafety.core.Translations
import com.posthog.PostHog
import com.backtosafety.core.data.Store
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

/** Each route's path in the RN app (expo-router), which analytics reports screens as. */
private val RN_PATHS = mapOf(
    "welcome" to "/onboarding", "name" to "/onboarding/name", "photo" to "/onboarding/photo",
    "appearance" to "/onboarding/appearance", "contact" to "/onboarding/contact", "complete" to "/onboarding/complete",
    "home" to "/", "emergency" to "/emergency", "readout" to "/readout", "settings" to "/settings",
    "contacts" to "/contacts", "destinations" to "/destinations", "profile" to "/profile",
)

/** i18n/index.ts: languages released to users (Spanish awaits a native review). */
private val SHIPPED_LANGUAGES = listOf("en")

/** The app: theme from the saved preference, then onboarding or home (app/_layout.tsx). */
@Composable
fun App(store: Store, translations: Translations, modifier: Modifier) {
    var onboarded by remember { mutableStateOf<Boolean?>(null) }
    var themePreference by remember { mutableStateOf("system") }
    var language by remember { mutableStateOf("en") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.seed()
        // utils/analytics.ts initAnalytics: the device is the analytics identity.
        runCatching { store.deviceId() }.onSuccess { if (BuildConfig.POSTHOG_KEY.isNotEmpty()) PostHog.identify(it) }
        themePreference = store.setting(Store.THEME_PREFERENCE) ?: "system"
        // i18n/index.ts loadSavedLanguage: Spanish only in dev builds until it ships.
        language = when (store.setting(Store.LANGUAGE_PREFERENCE)) {
            "es" -> if (BuildConfig.DEBUG || "es" in SHIPPED_LANGUAGES) "es" else "en"
            else -> "en"
        }
        onboarded = store.isOnboarded()
    }
    val dark = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val start = onboarded ?: return
    val onboarding = translations.translator(language, "onboarding")
    val common = translations.translator(language, "common")
    val home = translations.translator(language, "home")
    val emergency = translations.translator(language, "emergency")
    val readout = translations.translator(language, "readout")
    val settings = translations.translator(language, "settings")
    fun setTheme(value: String) {
        themePreference = value
        scope.launch { store.putSetting(Store.THEME_PREFERENCE, value) }
    }

    AppTheme(dark = dark) {
        val nav = rememberNavController()
        // app/_layout.tsx reports each screen as its route path.
        DisposableEffect(nav) {
            val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                RN_PATHS[destination.route]?.let(Analytics::screen)
            }
            nav.addOnDestinationChangedListener(listener)
            onDispose { nav.removeOnDestinationChangedListener(listener) }
        }
        NavHost(nav, startDestination = if (start) "home" else "welcome", modifier = modifier) {
            composable("welcome") {
                WelcomeScreen(
                    t = onboarding,
                    themePreference = themePreference,
                    onThemeChange = {
                        Analytics.track(AnalyticsEvent.SETTINGS_THEME_CHANGED, mapOf("theme" to it, "source" to "onboarding_welcome"))
                        setTheme(it)
                    },
                    onGetStarted = {
                        trackStep(true, "welcome")
                        scope.launch { store.completeStep("welcome"); nav.navigate("name") }
                    },
                )
            }
            composable("name") { NameScreen(onboarding, common, store, onBack = { nav.popBackStack() }) { nav.navigate("photo") } }
            composable("photo") { PhotoScreen(onboarding, common, store, onBack = { nav.popBackStack() }) { nav.navigate("appearance") } }
            composable("appearance") { AppearanceScreen(onboarding, store, onBack = { nav.popBackStack() }) { nav.navigate("contact") } }
            composable("contact") { ContactScreen(onboarding, common, store, onBack = { nav.popBackStack() }) { nav.navigate("complete") } }
            composable("complete") {
                CompleteScreen(onboarding, store) { nav.navigate("home") { popUpTo(0) } }
            }
            composable("home") { HomeScreen(home, common("emergencyNumber"), store) { nav.navigate(it) } }
            composable("emergency") {
                EmergencyScreen(
                    emergency, common, store,
                    onLeave = { if (!nav.popBackStack()) nav.navigate("home") },
                    onViewReadout = { nav.navigate("readout") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings, common, store,
                    themePreference = themePreference,
                    onThemeChange = {
                        Analytics.track(AnalyticsEvent.SETTINGS_THEME_CHANGED, mapOf("theme" to it))
                        setTheme(it)
                    },
                    language = language,
                    onLanguageChange = {
                        Analytics.track(AnalyticsEvent.SETTINGS_LANGUAGE_CHANGED, mapOf("language" to it))
                        language = it
                        scope.launch { store.putSetting(Store.LANGUAGE_PREFERENCE, it) }
                    },
                    onBack = { nav.popBackStack() },
                    onDeleted = { nav.navigate("welcome") { popUpTo(0) } },
                )
            }
            composable("contacts") { ContactsScreen(translations.translator(language, "contacts"), common, store) { nav.popBackStack() } }
            composable("destinations") { DestinationsScreen(translations.translator(language, "destinations"), common, store) { nav.popBackStack() } }
            composable("readout") { ReadoutScreen(readout, common, store) { nav.popBackStack() } }
            composable("profile") {
                ProfileScreen(
                    translations.translator(language, "profile"), common, store,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.navigate("home") { popUpTo("home") { inclusive = true } } },
                )
            }
        }
    }
}
