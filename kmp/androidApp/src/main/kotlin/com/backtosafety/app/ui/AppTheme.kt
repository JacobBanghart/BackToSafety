package com.backtosafety.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
)

/**
 * A text style sized like RN's. Android 14+ scales fonts nonlinearly, and RN converts
 * fontSize and lineHeight through that table separately (at 1.3x, 24sp -> ~26.7dp), while
 * Compose would scale lineHeight by the font size's ratio. So lineHeight is given as the
 * ratio of the two converted sizes.
 */
@Composable
fun rnTextStyle(fontSize: Float, lineHeight: Float, fontWeight: Int = 400, letterSpacing: Float = 0f): TextStyle {
    val ratio = with(LocalDensity.current) { lineHeight.sp.toDp().value / fontSize.sp.toDp().value }
    return RnTextBase.merge(
        TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight(fontWeight),
            lineHeight = ratio.em,
            letterSpacing = letterSpacing.sp,
        ),
    )
}

/** A design-system text style (constants/Typography.ts). */
@Composable
fun DesignTokens.Type.style(): TextStyle = rnTextStyle(fontSize, lineHeight, fontWeight, letterSpacing)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides AppColors(dark),
        // ThemedText's defaults (16/24), which unstyled text such as emoji icons inherits.
        LocalTextStyle provides rnTextStyle(16f, 24f),
        content = content,
    )
}
