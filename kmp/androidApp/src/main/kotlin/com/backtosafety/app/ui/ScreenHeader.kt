package com.backtosafety.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/**
 * What a screen reader says for every back control (common.json "back"). Provided once at the
 * root so each header doesn't need its own translator; a back arrow with no label is read as
 * nothing useful.
 */
val LocalBackLabel = staticCompositionLocalOf { "" }

/**
 * The platform's back control: Material's 48dp icon button with the back arrow, labelled
 * [LocalBackLabel] for TalkBack.
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val label = LocalBackLabel.current
    IconButton(onClick = onBack, modifier = modifier.size(48.udp).semantics { contentDescription = label }) {
        Icon("arrow.left", 24f, LocalAppColors.current.text)
    }
}

/**
 * The screen's top app bar: Material 3's TopAppBar (start-aligned title, back arrow as the
 * navigation icon, actions at the end), so the app reads as an Android app. The screen pads
 * the status bar itself, so the bar takes no insets of its own. IDs: <testID>-back, -title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(
    title: String,
    testID: String,
    onBack: () -> Unit,
    titleIcon: Pair<String, Color>? = null,
    titleIconSize: Float = 18f,
    right: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    TopAppBar(
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalAlignment = Alignment.CenterVertically) {
                titleIcon?.let { (name, color) -> Icon(name, titleIconSize + 2f, color) }
                Text(
                    title,
                    style = DesignTokens.Typography.title.style(),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("$testID-title"),
                )
            }
        },
        navigationIcon = {
            Box(Modifier.padding(start = space.xs.udp)) { BackButton(onBack, Modifier.testTag("$testID-back")) }
        },
        actions = {
            // The action's container ends at the screen's 16dp margin (the bar's 4dp and these
            // 12dp), lined up with the cards below it.
            Row(
                Modifier.widthIn(min = 48.udp).padding(end = space.md.udp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = right,
            )
        },
        windowInsets = WindowInsets(0.dp),
        expandedHeight = 64.udp,
        // The theme's own app bar colours: AppTheme maps Material's surface to the page
        // background and onSurface to the text colour.
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
