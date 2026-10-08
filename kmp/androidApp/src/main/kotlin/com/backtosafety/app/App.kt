package com.backtosafety.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.backtosafety.app.emergency.EmergencyScreen
import com.backtosafety.app.home.HomeScreen
import com.backtosafety.app.onboarding.AppearanceScreen
import com.backtosafety.app.onboarding.CompleteScreen
import com.backtosafety.app.onboarding.ContactScreen
import com.backtosafety.app.onboarding.NameScreen
import com.backtosafety.app.onboarding.PhotoScreen
import com.backtosafety.app.onboarding.WelcomeScreen
import com.backtosafety.app.readout.ReadoutScreen
import com.backtosafety.app.settings.SettingsScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.app.ui.NotPortedScreen
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import com.backtosafety.core.invoke
import kotlinx.coroutines.launch

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
        NavHost(nav, startDestination = if (start) "home" else "welcome", modifier = modifier) {
            composable("welcome") {
                WelcomeScreen(
                    t = onboarding,
                    themePreference = themePreference,
                    onThemeChange = ::setTheme,
                    onGetStarted = { scope.launch { store.completeStep("welcome"); nav.navigate("name") } },
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
                    themePreference = themePreference, onThemeChange = ::setTheme,
                    language = language,
                    onLanguageChange = {
                        language = it
                        scope.launch { store.putSetting(Store.LANGUAGE_PREFERENCE, it) }
                    },
                    onBack = { nav.popBackStack() },
                    onDeleted = { nav.navigate("welcome") { popUpTo(0) } },
                )
            }
            composable("readout") { ReadoutScreen(readout, common, store) { nav.popBackStack() } }
            // Not ported yet: each shows its title and a back button.
            for ((route, ns) in listOf(
                "profile" to "profile", "contacts" to "contacts", "destinations" to "destinations",

            )) {
                composable(route) { NotPortedScreen(translations.translator(language, ns)("screenTitle"), route) { nav.popBackStack() } }
            }
        }
    }
}
