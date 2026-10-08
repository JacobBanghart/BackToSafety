package com.backtosafety.app.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.backtosafety.core.DesignTokens

/** The RN app's theme colors (constants/Colors.ts via spec/design-tokens.json). */
class AppColors(val isDark: Boolean) {
    val text = Color(if (isDark) DesignTokens.Dark.text else DesignTokens.Light.text)
    val textSecondary = Color(if (isDark) DesignTokens.Dark.textSecondary else DesignTokens.Light.textSecondary)
    val textDisabled = Color(if (isDark) DesignTokens.Dark.textDisabled else DesignTokens.Light.textDisabled)
    val textOnPrimary = Color(if (isDark) DesignTokens.Dark.textOnPrimary else DesignTokens.Light.textOnPrimary)
    val background = Color(if (isDark) DesignTokens.Dark.background else DesignTokens.Light.background)
    val card = Color(if (isDark) DesignTokens.Dark.card else DesignTokens.Light.card)
    val surface = Color(if (isDark) DesignTokens.Dark.surface else DesignTokens.Light.surface)
    val border = Color(if (isDark) DesignTokens.Dark.border else DesignTokens.Light.border)
    val primary = Color(if (isDark) DesignTokens.Dark.primary else DesignTokens.Light.primary)
    val primaryLight = Color(if (isDark) DesignTokens.Dark.primaryLight else DesignTokens.Light.primaryLight)
    val tint = Color(if (isDark) DesignTokens.Dark.tint else DesignTokens.Light.tint)
    val inputBackground = Color(if (isDark) DesignTokens.Dark.inputBackground else DesignTokens.Light.inputBackground)
    val inputBorder = Color(if (isDark) DesignTokens.Dark.inputBorder else DesignTokens.Light.inputBorder)
    val inputPlaceholder = Color(if (isDark) DesignTokens.Dark.inputPlaceholder else DesignTokens.Light.inputPlaceholder)
    val error = Color(if (isDark) DesignTokens.Dark.error else DesignTokens.Light.error)
    val success = Color(if (isDark) DesignTokens.Dark.success else DesignTokens.Light.success)
}

val LocalAppColors = staticCompositionLocalOf { AppColors(isDark = false) }

/**
 * Text that lays out like RN's on Android: a block with a lineHeight is exactly
 * lines x lineHeight, with each line's text centered in it. In Compose that's
 * LineHeightStyle(Center, Trim.None), which only takes effect with includeFontPadding off.
 */
private val RnTextBase = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    lineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Default, LineBreak.WordBreak.Default),
    // RN's TextPaint lays glyphs out at their linear (unhinted) advances. Compose's default,
    // TextMotion.Static, hints them to whole pixels, which made body text ~0.6% narrower and
    // wrapped lines differently. Animated is linear: line widths then match RN to the pixel.
    textMotion = TextMotion.Animated,
)

/**
 * A text style sized the way RN's Android text is (TextAttributes): the font size and the
 * line height are converted to pixels and rounded *up* to whole pixels, and letter spacing
 * is em of that rounded font size. At 420dpi a 13sp caption renders at 35px, not 34.1, so
 * without this every caption is 2.6% narrower than RN's. The conversion goes through the
 * device's font scale, which Android 14+ applies nonlinearly. Lines break like RN's
 * textBreakStrategy="highQuality" (balanced), not greedily.
 */
@Composable
fun rnTextStyle(fontSize: Float, lineHeight: Float, fontWeight: Int = 400, letterSpacing: Float = 0f): TextStyle =
    with(LocalDensity.current) {
        val fontPx = ceilPx(fontSize.sp.toPx())
        val linePx = ceilPx(lineHeight.sp.toPx())
        RnTextBase.merge(
            TextStyle(
                fontSize = fontPx.toSp(),
                fontWeight = FontWeight(fontWeight),
                // Compose rounds the line height up again; keep float noise from adding a pixel.
                lineHeight = ((linePx - 0.01f) / fontPx).em,
                letterSpacing = (letterSpacing.sp.toPx() / fontPx).em,
            ),
        )
    }

/**
 * RN text with no lineHeight (e.g. ThemedText type="subtitle"): Android's natural line height,
 * with RN's default includeFontPadding = true.
 */
@Composable
fun rnTextStyleNatural(fontSize: Float, fontWeight: Int = 400): TextStyle = with(LocalDensity.current) {
    TextStyle(
        fontSize = ceilPx(fontSize.sp.toPx()).toSp(),
        fontWeight = FontWeight(fontWeight),
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineBreak = RnTextBase.lineBreak,
        textMotion = TextMotion.Animated,
    )
}

/**
 * Holds a line of text to RN's line height. RN's line-height span fixes each line's height
 * even when a glyph comes from a taller fallback font (emoji); Compose grows the line to the
 * fallback's metrics instead, so emoji-only text is clipped to the line here.
 */
@Composable
fun Modifier.rnLineHeight(lineHeight: Float): Modifier {
    val height = with(LocalDensity.current) { ceilPx(lineHeight.sp.toPx()).toDp() }
    return this.height(height).wrapContentHeight(unbounded = true)
}

/** Math.ceil on a pixel size, tolerant of float noise (42.0000001 is 42). */
fun ceilPx(px: Float): Float = kotlin.math.ceil(px - 0.001f)

/** A design-system text style (constants/Typography.ts), optionally with RN style overrides. */
@Composable
fun DesignTokens.Type.style(fontWeight: Int = this.fontWeight, letterSpacing: Float = this.letterSpacing): TextStyle =
    rnTextStyle(fontSize, lineHeight, fontWeight, letterSpacing)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides AppColors(dark),
        // ThemedText's defaults (16/24), which unstyled text such as emoji icons inherits.
        LocalTextStyle provides rnTextStyle(16f, 24f),
        content = content,
    )
}
