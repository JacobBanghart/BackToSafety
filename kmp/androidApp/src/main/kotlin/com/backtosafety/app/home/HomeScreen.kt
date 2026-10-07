package com.backtosafety.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.PrimaryButton
import com.backtosafety.app.ui.rnTextStyle
import com.backtosafety.core.Translate
import com.backtosafety.core.invoke

/** Placeholder until home is ported: just the entry point flows look for. */
@Composable
fun HomeScreen(t: Translate) {
    Box(
        Modifier.fillMaxSize().background(LocalAppColors.current.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        PrimaryButton(t("emergencyButton.startTitle"), "home-start-emergency", onClick = {}, textStyle = rnTextStyle(18f, 24f, fontWeight = 600))
    }
}
