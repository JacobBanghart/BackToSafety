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
import com.backtosafety.app.home.HomeScreen
import com.backtosafety.app.onboarding.AppearanceScreen
import com.backtosafety.app.onboarding.CompleteScreen
import com.backtosafety.app.onboarding.ContactScreen
import com.backtosafety.app.onboarding.NameScreen
import com.backtosafety.app.onboarding.PhotoScreen
import com.backtosafety.app.onboarding.WelcomeScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.Translations
import com.backtosafety.core.data.Store
import kotlinx.coroutines.launch

/** The app: theme from the saved preference, then onboarding or home (app/_layout.tsx). */
@Composable
fun App(store: Store, translations: Translations, modifier: Modifier) {
    var onboarded by remember { mutableStateOf<Boolean?>(null) }
    var themePreference by remember { mutableStateOf("system") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.seed()
        themePreference = store.setting("theme_preference") ?: "system"
        onboarded = store.isOnboarded()
    }
    val dark = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val start = onboarded ?: return
    val onboarding = translations.translator("en", "onboarding")
    val common = translations.translator("en", "common")
    val home = translations.translator("en", "home")

    AppTheme(dark = dark) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = if (start) "home" else "welcome", modifier = modifier) {
            composable("welcome") {
                WelcomeScreen(
                    t = onboarding,
                    themePreference = themePreference,
                    onThemeChange = { value ->
                        themePreference = value
                        scope.launch { store.putSetting("theme_preference", value) }
                    },
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
            composable("home") { HomeScreen(home) }
        }
    }
}
