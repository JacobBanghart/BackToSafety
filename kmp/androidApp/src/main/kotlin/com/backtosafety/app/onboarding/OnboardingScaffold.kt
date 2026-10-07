package com.backtosafety.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.core.DesignTokens

/**
 * An onboarding step: scrolling content above a footer that stays above the keyboard
 * (KeyboardAvoidingScroll with a footer; safeDrawing includes the IME inset).
 */
@Composable
fun OnboardingScaffold(
    footer: @Composable ColumnScope.() -> Unit,
    footerGap: Float = DesignTokens.Spacing.md,
    footerPadding: Float = DesignTokens.Spacing.lg,
    footerBottomPadding: Float = footerPadding,
    contentTop: Float = 20f,
    contentBottom: Float = 0f,
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = DesignTokens.Spacing.xl.dp, end = DesignTokens.Spacing.xl.dp, top = contentTop.dp, bottom = contentBottom.dp),
            content = content,
        )
        Column(
            Modifier.padding(start = footerPadding.dp, end = footerPadding.dp, top = footerPadding.dp, bottom = footerBottomPadding.dp),
            verticalArrangement = Arrangement.spacedBy(footerGap.dp),
            content = footer,
        )
    }
}
