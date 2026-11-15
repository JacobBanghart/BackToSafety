package com.backtosafety.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.app.ui.FormTextInput
import com.backtosafety.app.ui.LocalBackLabel
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.core.Translations
import com.backtosafety.core.invoke
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The shared controls every screen uses: a back control that a screen reader can name, and a
 * single-line field tall enough to hit (48dp).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class AccessibleControlsTest {
    @get:Rule val compose = createComposeRule()

    private val common = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                    .associate { it.nameWithoutExtension to it.readText() }
            },
        ).translator("es", "common")
    }

    @Test
    fun theBackControlIsReadInTheAppsLanguageAndGoesBack() {
        var backs = 0
        compose.setContent {
            AppTheme(dark = false) {
                CompositionLocalProvider(LocalBackLabel provides common("back")) {
                    ScreenHeader("Ajustes", "settings", onBack = { backs++ })
                }
            }
        }
        compose.onNodeWithTag("settings-back").assertContentDescriptionEquals("Atrás").assertHasClickAction().performClick()
        assertEquals(1, backs)
    }

    @Test
    fun aSingleLineFieldIsAFullTouchTarget() {
        compose.setContent {
            AppTheme(dark = false) { FormTextInput("Name", "", {}, "form-name") }
        }
        compose.onNodeWithTag("form-name").assertHeightIsAtLeast(48.dp)
    }
}
