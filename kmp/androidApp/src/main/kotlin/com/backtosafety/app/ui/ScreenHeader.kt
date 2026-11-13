package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/**
 * The screen's top app bar (centre-aligned, as Material 3's CenterAlignedTopAppBar): a 48dp
 * back control on the left, the title centred against the full width (optionally with an
 * icon), and an optional action inset from the right edge. IDs: <testID>-back, -title.
 */
@Composable
fun ScreenHeader(
    title: String,
    testID: String,
    onBack: () -> Unit,
    titleIcon: Pair<String, Color>? = null,
    titleIconSize: Float = 18f,
    right: @Composable () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    Box(Modifier.fillMaxWidth().background(colors.background).heightIn(min = 56.udp).padding(horizontal = space.xs.udp)) {
        Row(
            Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(space.xs.udp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.padding(horizontal = (48 + space.sm).dp),
                horizontalArrangement = Arrangement.spacedBy(space.xs.udp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                titleIcon?.let { (name, color) -> Icon(name, titleIconSize, color) }
                Text(
                    title,
                    style = DesignTokens.Typography.title.style(),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("$testID-title"),
                )
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            // The platform's back control: a 48dp target with a 24dp chevron centred in it.
            Box(
                Modifier.size(48.udp).testTag("$testID-back").clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon("chevron.left", 24f, colors.tint) }
            Box(Modifier.weight(1f))
            Box(Modifier.widthIn(min = 48.udp).padding(end = space.sm.udp), contentAlignment = Alignment.CenterEnd) { right() }
        }
    }
}
