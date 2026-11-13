package com.backtosafety.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/** The label column of a [DetailRow], wide enough for "Device ID" in en and es at 130%. */
private const val DETAIL_LABEL_WIDTH = 120

/** How strongly a [AppCard] with `danger` is washed with the error colour (its border is 0.5). */
const val DANGER_FILL_ALPHA = 0.08f

/**
 * A section card: Material 3's OutlinedCard in the theme's card colour and hairline, radius lg,
 * padding lg, margin below md. A danger card (Delete Account) is washed and edged in the error
 * colour, so it is marked as destructive by the whole card, not only by its icon.
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, surface: Boolean = false, danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    val fill = if (surface) colors.surface else colors.card
    OutlinedCard(
        modifier.padding(bottom = DesignTokens.Spacing.md.udp).fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.lg.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (danger) colors.error.copy(alpha = DANGER_FILL_ALPHA).compositeOver(fill) else fill,
            contentColor = colors.text,
        ),
        border = BorderStroke(1.dp, if (danger) colors.error.copy(alpha = 0.5f) else colors.border),
    ) {
        Column(Modifier.fillMaxWidth().padding(DesignTokens.Spacing.lg.udp), content = content)
    }
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
 * A support detail (platform, theme, device ID): label and value on one line at 14sp (caption
 * size was too small to read), with no rule, so it reads as secondary to the card rows above it. The labels are a fixed-width
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
    val type = DesignTokens.Typography.label
    Row(
        Modifier.fillMaxWidth()
            .then(if (testID != null) Modifier.testTag(testID) else Modifier)
            .heightIn(min = if (onPress != null) 48.udp else 40.udp)
            .then(if (onPress != null) Modifier.clickable(onClickLabel = action?.second, role = Role.Button, onClick = onPress) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.style(fontWeight = 500, letterSpacing = 0f), color = colors.textSecondary, modifier = Modifier.widthIn(min = DETAIL_LABEL_WIDTH.udp))
        Text(
            value, style = type.style(letterSpacing = 0f), color = colors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(if (testID != null) Modifier.testTag("$testID-value") else Modifier),
        )
        if (action != null) Icon(action.first, 18f, colors.primaryText)
    }
}
