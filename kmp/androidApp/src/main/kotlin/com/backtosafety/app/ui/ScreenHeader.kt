package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/**
 * components/ScreenHeader.tsx: back chevron on the left, a title centered against the full
 * width (optionally with an icon), and an optional right element. IDs: <testID>-back, -title.
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
    Box(Modifier.fillMaxWidth().background(colors.background).heightIn(min = 52.dp).padding(space.md.dp)) {
        Row(
            Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(space.xs.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.padding(horizontal = (44 + space.md).dp),
                horizontalArrangement = Arrangement.spacedBy(space.xs.dp),
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
            Box(
                Modifier.widthIn(min = 44.dp).testTag("$testID-back").clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { Icon("chevron.left", 22f, colors.tint) }
            Box(Modifier.weight(1f))
            Box(Modifier.widthIn(min = 44.dp), contentAlignment = Alignment.CenterEnd) { right() }
        }
    }
}
