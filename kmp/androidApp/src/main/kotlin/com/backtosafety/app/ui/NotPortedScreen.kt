package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder for a screen that isn't ported yet. It uses the RN screen's header testIDs. */
@Composable
fun NotPortedScreen(title: String, testID: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalAppColors.current.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(title, testID, onBack)
    }
}
