package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

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
 */
@Composable
fun ListItem(
    label: String,
    value: String,
    testID: String? = null,
    ruleColor: Color? = null,
    onPress: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val rule = ruleColor ?: colors.border
    Column(
        Modifier.fillMaxWidth()
            .then(if (testID != null) Modifier.testTag(testID) else Modifier)
            .then(if (onPress != null) Modifier.clickable(onClick = onPress) else Modifier)
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(rule, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            }
            .padding(bottom = 1.udp)
            .padding(vertical = DesignTokens.Spacing.sm.udp),
    ) {
        Text(label, style = DesignTokens.Typography.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = 2.udp))
        Text(
            value, style = DesignTokens.Typography.body.style(), color = colors.text,
            modifier = if (testID != null) Modifier.testTag("$testID-value") else Modifier,
        )
    }
}
