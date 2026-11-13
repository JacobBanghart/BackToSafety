package com.backtosafety.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import com.backtosafety.app.ui.ListItem
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

    private fun largeText(scale: Float = 1.3f, content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
            AppTheme(dark = false) { content() }
        }
    }

    /** At rest, each feature card and the theme picker is wholly above the footer or starts below it. */
    private fun assertWelcomeEndsOnWholeBlocks(scale: Float) {
        val t = translations.translator("en", "onboarding")
        largeText(scale) { WelcomeScreen(t, "system", {}, {}) }
        // The footer's rule sits a 16dp padding and 1dp above Get Started.
        val fold = compose.onNodeWithTag("onboarding-get-started").getUnclippedBoundsInRoot().top - 17.dp
        val blocks = listOf("timer", "readout", "privacy").map { key ->
            key to (
                compose.onNodeWithText(t("welcome.features.$key.title")).getUnclippedBoundsInRoot().top to
                    compose.onNodeWithText(t("welcome.features.$key.description")).getUnclippedBoundsInRoot().bottom
                )
        } + ("theme" to (compose.onNodeWithText(t("welcome.themeLabel").uppercase()).getUnclippedBoundsInRoot().top to
            compose.onNodeWithTag("onboarding-welcome-theme-light").getUnclippedBoundsInRoot().bottom))
        for ((key, span) in blocks) {
            val (top, bottom) = span
            assertTrue("$key is cut by the footer at ${scale}x (top $top, bottom $bottom, fold $fold)", bottom <= fold || top >= fold)
        }
        // Moving a block below the fold moves only it and what follows: the title stays put.
        val title = compose.onNodeWithText(t("welcome.title")).getUnclippedBoundsInRoot()
        assertTrue("the hero moved below the fold at ${scale}x", title.bottom <= fold)
    }

    @Test
    fun theWelcomeScreenShowsOnlyWholeBlocksAboveTheFooter() = assertWelcomeEndsOnWholeBlocks(1.3f)

    @Test
    fun theWelcomeScreenShowsOnlyWholeBlocksAboveTheFooterAtTheLargestText() = assertWelcomeEndsOnWholeBlocks(2f)

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

    @Test
    fun aRowWithACopyActionKeepsALongValueToOneLineAndSaysWhatTappingDoes() {
        largeText {
            Box(Modifier.width(240.dp)) {
                ListItem(
                    "Device ID", "3f2a9c1b-7d4e-4b2a-9c1d-0a5e6f7b41ef", testID = "id",
                    action = "doc.on.clipboard.fill" to "Copy Device ID",
                ) {}
            }
        }
        // One body line at 130% is about 31dp; the raw UUID wrapped onto two or three.
        val value = compose.onNodeWithTag("id-value", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the value wrapped (${value.height})", value.height < 40.dp)
        compose.onNodeWithTag("id").assert(
            SemanticsMatcher("clicks to copy") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Copy Device ID" },
        )
        assertTrue("the row is under 48dp", compose.onNodeWithTag("id").getUnclippedBoundsInRoot().height >= 48.dp)
    }
}
