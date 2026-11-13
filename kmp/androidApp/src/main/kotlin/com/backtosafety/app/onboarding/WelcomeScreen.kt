package com.backtosafety.app.onboarding

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.TrackStepViewed
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.Translate
import com.backtosafety.core.invoke

/** First onboarding step. Port of app/onboarding/index.tsx. */
@Composable
fun WelcomeScreen(
    t: Translate,
    themePreference: String,
    onThemeChange: (String) -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrackStepViewed("welcome")
    val colors = LocalAppColors.current
    val featureBg = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color(DesignTokens.Neutral.c100)
    val optionBg = if (colors.isDark) Color.White.copy(alpha = 0.1f) else Color(DesignTokens.Neutral.c100)
    val scroll = rememberScrollState()
    val type = DesignTokens.Typography
    val space = DesignTokens.Spacing

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // The viewport's height decides how the first screen ends: only whole feature cards show
        // above the footer (a card cut mid-sentence by the rule read as broken at large text).
        BoxWithConstraints(Modifier.weight(1f)) {
            WelcomeLayout(
                viewportHeight = constraints.maxHeight,
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(horizontal = space.xl.udp),
            ) {
                Logo(Modifier)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        t("welcome.title"),
                        style = rnTextStyle(36f, 44f, fontWeight = 700, letterSpacing = type.display.letterSpacing),
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = space.lg.udp),
                    )
                    Text(
                        t("welcome.description"),
                        style = type.bodyLarge.style(),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                for ((key, icon) in listOf("timer" to "timer", "readout" to "doc.text.fill", "privacy" to "lock.fill")) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                            .background(featureBg)
                            .padding(space.lg.udp),
                        horizontalArrangement = Arrangement.spacedBy(16.udp),
                    ) {
                        Box(
                            Modifier.size(44.udp).clip(CircleShape).background(colors.primaryLight),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, 22f, colors.primaryText) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs.udp)) {
                            Text(t("welcome.features.$key.title"), style = type.bodyBold.style(), color = colors.text)
                            Text(t("welcome.features.$key.description"), style = type.body.style(), color = colors.textSecondary)
                        }
                    }
                }
                Column {
                    Text(
                        t("welcome.themeLabel").uppercase(),
                        style = type.caption.style(fontWeight = 600, letterSpacing = 1f),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = space.md.udp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                        for ((value, label, icon) in listOf(
                            Triple("light", "welcome.themeOptions.light", "sun.max.fill"),
                            Triple("dark", "welcome.themeOptions.dark", "moon.fill"),
                            Triple("system", "welcome.themeOptions.auto", "iphone"),
                        )) {
                            val selected = themePreference == value
                            Row(
                                Modifier
                                    .weight(1f)
                                    .testTag("onboarding-welcome-theme-$value")
                                    .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                                    .background(if (selected) colors.primaryLight else optionBg)
                                    .border(
                                        2.udp,
                                        if (selected) colors.tint else Color.Transparent,
                                        RoundedCornerShape(DesignTokens.Radius.lg.dp),
                                    )
                                    .selectable(selected, role = Role.RadioButton) { onThemeChange(value) }
                                    .padding(space.md.udp),
                                horizontalArrangement = Arrangement.spacedBy(8.udp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(icon, 20f, if (selected) colors.primaryText else colors.textSecondary)
                                Text(
                                    t(label),
                                    style = type.bodyLarge.style().copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                    color = if (selected) colors.primaryText else colors.text,
                                )
                            }
                        }
                    }
                }
            }
        }

        // A rule above the footer: the content stops at a visible edge. No "scroll for more" cue:
        // pinned over the content it still made the cards above it look cut off, and the layout
        // already ends the first screen on whole blocks.
        Box(Modifier.fillMaxWidth().height(1.udp).background(colors.border))
        Column(Modifier.padding(space.lg.udp), verticalArrangement = Arrangement.spacedBy(space.md.udp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.udp)
                    .testTag("onboarding-get-started")
                    .clip(RoundedCornerShape(DesignTokens.Radius.lg.dp))
                    .background(colors.primary)
                    .clickable(role = Role.Button, onClick = onGetStarted)
                    .padding(vertical = space.md.udp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t("welcome.getStarted"),
                    style = type.bodyLarge.style().copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textOnPrimary,
                )
            }
            // The privacy promise at body size in ink: at caption size it read as fine print.
            Text(
                t("welcome.privacy"),
                style = type.body.style(fontWeight = 500),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember {
        context.assets.open("images/logo-full.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    // In dark mode the logo's cream disc is toned down a little, so it doesn't glare on black.
    val dim = if (LocalAppColors.current.isDark) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(LOGO_DARK, LOGO_DARK, LOGO_DARK, 1f) }) else null
    Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit, colorFilter = dim)
}

/**
 * The welcome content: logo, title, the three feature cards and the theme picker, stacked with
 * the screen's gaps. A block (card or theme picker) the viewport's bottom edge would cut never
 * shows at rest: the logo shrinks (to [LOGO_MIN]) until that block fits with a gap above the
 * footer, and if that isn't enough the block and what follows it move wholly below the fold,
 * so the first screen ends on complete blocks. Only what follows moves: everything above stays
 * where it was (shifting it all down cut the block above instead).
 * Children, in order: logo, hero text, the feature cards, the theme picker. Mirrors iOS
 * `WelcomeLayout`.
 */
@Composable
private fun WelcomeLayout(viewportHeight: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val space = DesignTokens.Spacing
    val topPad = space.xxxl.udp
    val afterLogo = space.xl.udp
    val afterHero = space.xxl.udp
    val betweenCards = space.lg.udp
    val afterCards = space.xxl.udp
    val bottomPad = (space.xl * 2).udp
    val foldGap = space.lg.udp
    val logoMax = LOGO_MAX.udp
    val logoMin = LOGO_MIN.udp
    Layout(content, modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val full = Constraints(minWidth = width, maxWidth = width)
        val hero = measurables[1].measure(full)
        val cards = measurables.subList(2, measurables.size - 1).map { it.measure(full) }
        val theme = measurables.last().measure(full)
        // The blocks below the hero, each with the gap above it.
        val blocks = cards + theme
        val gaps = cards.indices.map { if (it == 0) 0 else betweenCards.roundToPx() } + afterCards.roundToPx()
        val maxLogo = logoMax.roundToPx()
        val blocksTop = topPad.roundToPx() + maxLogo + afterLogo.roundToPx() + hero.height + afterHero.roundToPx()
        val fold = viewportHeight - foldGap.roundToPx()
        var logo = maxLogo
        var pushAt = -1
        var pushDown = 0
        if (constraints.hasBoundedWidth && viewportHeight != Constraints.Infinity) {
            var top = blocksTop
            for ((i, block) in blocks.withIndex()) {
                top += gaps[i]
                val bottom = top + block.height
                if (bottom > fold && top < viewportHeight) {
                    val overflow = bottom - fold
                    if (overflow <= maxLogo - logoMin.roundToPx()) {
                        logo = maxLogo - overflow
                    } else {
                        pushAt = i
                        pushDown = viewportHeight - top
                    }
                    break
                }
                top = bottom
            }
        }
        val logoPlaceable = measurables[0].measure(Constraints.fixed(logo, logo))
        var y = topPad.roundToPx()
        val placements = buildList {
            add(Triple(logoPlaceable, (width - logo) / 2, y)); y += logo + afterLogo.roundToPx()
            add(Triple(hero, 0, y)); y += hero.height + afterHero.roundToPx()
            blocks.forEachIndexed { i, block ->
                y += gaps[i] + if (i == pushAt) pushDown else 0
                add(Triple(block, 0, y)); y += block.height
            }
            y += bottomPad.roundToPx()
        }
        layout(width, y) { placements.forEach { (p, x, py) -> p.place(x, py) } }
    }
}

private const val LOGO_MAX = 180
private const val LOGO_MIN = 96

/** How much of its brightness the logo keeps in dark mode. */
private const val LOGO_DARK = 0.85f
