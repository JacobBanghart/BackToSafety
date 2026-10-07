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
 * Text that lays out like RN's on Android: RN keeps the platform's includeFontPadding on,
 * and applies lineHeight to every line, first and last included, with the text centered
 * in it. Compose defaults to the opposite on both, which makes every block shorter.
 */
val RnTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides AppColors(dark),
        LocalTextStyle provides RnTextStyle,
        content = content,
    )
}

/** A design-system text style (constants/Typography.ts). RN font sizes scale like sp. */
fun DesignTokens.Type.style(): TextStyle = RnTextStyle.merge(
    TextStyle(
        fontSize = fontSize.sp,
        fontWeight = FontWeight(fontWeight),
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    ),
)

