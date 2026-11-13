package com.backtosafety.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.backtosafety.app.onboarding.WelcomeScreen
import com.backtosafety.app.ui.AppTheme
import com.backtosafety.app.ui.ButtonRowOrStack
import com.backtosafety.app.ui.ChipGrid
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.OptionChip
import com.backtosafety.core.Translations
import com.backtosafety.core.invoke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Layouts that must stay whole at 130% text: the welcome screen ends on complete cards, option
 * chips line up, and side-by-side buttons stay inside the screen's margins.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics, so text is measured with real fonts (the legacy mode measures it as empty).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class LargeTextLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val translations = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"), "i18n/locales").let { locales ->
        Translations.fromJson(
            locales.listFiles()!!.filter { it.isDirectory }.associate { locale ->
                locale.name to locale.listFiles()!!.filter { it.extension == "json" }
                    .associate { it.nameWithoutExtension to it.readText() }
            },
        )
    }

    private fun largeText(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
            AppTheme(dark = false) { content() }
        }
    }

    @Test
    fun theWelcomeScreenShowsOnlyWholeFeatureCardsAboveTheFooter() {
        val t = translations.translator("en", "onboarding")
        largeText { WelcomeScreen(t, "system", {}, {}) }
        val fold = compose.onNodeWithText(t("welcome.scrollHint").uppercase()).getUnclippedBoundsInRoot().top
        for (key in listOf("timer", "readout", "privacy")) {
            val title = compose.onNodeWithText(t("welcome.features.$key.title")).getUnclippedBoundsInRoot()
            val description = compose.onNodeWithText(t("welcome.features.$key.description")).getUnclippedBoundsInRoot()
            // A card is either wholly above the footer or starts below it; never cut across.
            val whole = description.bottom <= fold
            val hidden = title.top >= fold
            assertTrue("$key card is cut by the footer (title ${title.top}, text ends ${description.bottom}, fold $fold)", whole || hidden)
        }
    }

    @Test
    fun optionChipsAreAllTheSameHeightWhenALabelIsLong() {
        val labels = listOf("Primary Caregiver", "Caregiver", "Family", "Neighbor", "Friend", "Other")
        largeText {
            Box(Modifier.width(347.dp).testTag("grid")) {
                ChipGrid(labels) { label, modifier ->
                    OptionChip(label, "star.fill", "chip-$label", false, LocalAppColors.current.primary, modifier) {}
                }
            }
        }
        val heights = labels.map { compose.onNodeWithTag("chip-$it").getUnclippedBoundsInRoot().height }
        assertEquals("ragged chip heights $heights", 1, heights.distinct().size)
        val grid = compose.onNodeWithTag("grid").getUnclippedBoundsInRoot()
        val long = compose.onNodeWithTag("chip-Primary Caregiver").getUnclippedBoundsInRoot()
        // At 130% the long label doesn't fit half the width, so every chip takes a full row.
        assertEquals(grid.width, long.width)
    }

    @Test
    fun buttonsThatDontFitSideBySideStackInsideTheMargins() {
        largeText {
            Box(Modifier.width(300.dp).testTag("row")) {
                ButtonRowOrStack(12.dp) {
                    OptionChip("Take Photo", "camera.fill", "take", false, LocalAppColors.current.primary) {}
                    OptionChip("Choose Photo From Library", "photo.on.rectangle", "choose", false, LocalAppColors.current.primary) {}
                }
            }
        }
        val row = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val take = compose.onNodeWithTag("take").getUnclippedBoundsInRoot()
        val choose = compose.onNodeWithTag("choose").getUnclippedBoundsInRoot()
        assertTrue(take.right <= row.right && choose.right <= row.right)
        assertTrue("not stacked", choose.top >= take.bottom)
    }
}
