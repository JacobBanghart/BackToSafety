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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

// Pieces the contacts and places screens share (their RN styles are identical).

/** headerSaveButton: Add / Update / Saving... in the header's right slot. */
@Composable
fun HeaderSaveButton(label: String, testID: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.testTag(testID).widthIn(min = 72.udp).clip(RoundedCornerShape(DesignTokens.Radius.md.dp))
            .clickable(enabled = enabled, onClick = onClick).background(LocalAppColors.current.tint)
            .padding(horizontal = DesignTokens.Spacing.md.udp, vertical = DesignTokens.Spacing.xs.udp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DesignTokens.Typography.bodyBold.style(), color = Color(DesignTokens.Light.textOnPrimary), maxLines = 1)
    }
}

/** roleOption / optionButton: an icon and a caption (500) in a bordered pill. */
@Composable
fun OptionChip(label: String, icon: String, testID: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val white = Color(DesignTokens.Light.textOnPrimary)
    val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
    Row(
        Modifier.testTag(testID).clip(shape).clickable(onClick = onClick)
            .background(if (selected) selectedColor else colors.inputBackground)
            .rnBorder(1.dp, if (selected) selectedColor else colors.inputBorder, shape)
            .padding(horizontal = DesignTokens.Spacing.md.udp, vertical = DesignTokens.Spacing.sm.udp),
        horizontalArrangement = Arrangement.spacedBy(6.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 14f, if (selected) white else colors.icon)
        Text(label, style = DesignTokens.Typography.caption.style(fontWeight = 500), color = if (selected) white else colors.text)
    }
}

/** formDeleteButton: the outlined destructive button at the bottom of an edit form. */
@Composable
fun FormDeleteButton(label: String, testID: String, onClick: () -> Unit) {
    val error = Color(DesignTokens.Semantic.error)
    val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.md.udp).heightIn(min = 44.udp).testTag(testID).clip(shape)
            .clickable(onClick = onClick).rnBorder(1.dp, error, shape).padding(horizontal = DesignTokens.Spacing.md.udp),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs.udp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon("trash", 14f, error)
        Text(label, style = DesignTokens.Typography.bodyBold.style(), color = error)
    }
}

/** The list cards' look: card (or primaryLight while dragged) with a 1dp border, lifted while dragged. */
@Composable
fun Modifier.listCard(dragging: Boolean): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    return fillMaxWidth()
        .then(if (dragging) Modifier.scale(1.02f).alpha(0.98f).shadow(4.dp, shape) else Modifier)
        .clip(shape).background(if (dragging) colors.primaryLight else colors.card)
        .rnBorder(1.dp, if (dragging) colors.primary else colors.border, shape)
}
