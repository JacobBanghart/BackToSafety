package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/** The label column of a [DetailRow], wide enough for "Device ID" in en and es at 130%. */
private const val DETAIL_LABEL_WIDTH = 112

/** components/AppCard.tsx: card background, 1dp border, radius lg, padding lg, margin below md. */
@Composable
fun AppCard(modifier: Modifier = Modifier, surface: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(
        modifier.padding(bottom = DesignTokens.Spacing.md.udp).fillMaxWidth().clip(shape)
            .background(if (surface) colors.surface else colors.card)
            .rnBorder(1.udp, colors.border, shape).padding(DesignTokens.Spacing.lg.udp),
        content = content,
    )
}

/**
 * components/ListItem.tsx: a caption label over a body value, with a 1dp rule below that
 * takes space. Pressable rows carry testID; the value carries <testID>-value.
 *
 * A row with an [action] (an icon and what it does, such as copying) shows the icon at its end,
 * keeps the value to one line, and is a 48dp button announced by that action.
 */
@Composable
fun ListItem(
    label: String,
    value: String,
    testID: String? = null,
    ruleColor: Color? = null,
    action: Pair<String, String>? = null,
    onPress: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val rule = ruleColor ?: colors.border
    Row(
        Modifier.fillMaxWidth()
            .then(if (testID != null) Modifier.testTag(testID) else Modifier)
            .then(
                when {
                    onPress == null -> Modifier
                    action == null -> Modifier.clickable(onClick = onPress)
                    else -> Modifier.heightIn(min = 48.udp).clickable(onClickLabel = action.second, role = Role.Button, onClick = onPress)
                },
            )
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(rule, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            }
            .padding(bottom = 1.udp)
            .padding(vertical = DesignTokens.Spacing.sm.udp),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = DesignTokens.Typography.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = 2.udp))
            Text(
                value, style = DesignTokens.Typography.body.style(), color = colors.text,
                maxLines = if (action != null) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                modifier = if (testID != null) Modifier.testTag("$testID-value") else Modifier,
            )
        }
        if (action != null) Icon(action.first, 20f, colors.primaryText)
    }
}

/**
 * A support detail (platform, theme, device ID): label and value on one line in caption type,
 * with no rule, so it reads as secondary to the card rows above it. The labels are a fixed-width
 * column and each value starts right after it, so a value reads with its label (right-aligned
 * values sat far from them across the screen). Like [ListItem] it carries
 * testID on the row and <testID>-value on the value; with an [action] it is a 48dp button
 * announced by that action, its icon at the end.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    testID: String? = null,
    action: Pair<String, String>? = null,
    onPress: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val caption = DesignTokens.Typography.caption
    Row(
        Modifier.fillMaxWidth()
            .then(if (testID != null) Modifier.testTag(testID) else Modifier)
            .heightIn(min = if (onPress != null) 48.udp else 32.udp)
            .then(if (onPress != null) Modifier.clickable(onClickLabel = action?.second, role = Role.Button, onClick = onPress) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = caption.style(), color = colors.textSecondary, modifier = Modifier.widthIn(min = DETAIL_LABEL_WIDTH.udp))
        Text(
            value, style = caption.style(fontWeight = 600), color = colors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(if (testID != null) Modifier.testTag("$testID-value") else Modifier),
        )
        if (action != null) Icon(action.first, 18f, colors.primaryText)
    }
}
