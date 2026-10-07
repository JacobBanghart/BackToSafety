package com.backtosafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.backtosafety.app.onboarding.WelcomeScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.core.Translations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class MainActivity : ComponentActivity() {
    private val translations by lazy { loadTranslations() }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val t = translations.translator(locale = "en", namespace = "onboarding")
        setContent {
            AppTheme(dark = isSystemInDarkTheme()) {
                // testTag(...) values surface as Android resource IDs, matching the RN
                // app's testIDs, so the same Maestro flows drive both (spec/testids.json).
                WelcomeScreen(
                    t = t,
                    onGetStarted = {},
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                )
            }
        }
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
