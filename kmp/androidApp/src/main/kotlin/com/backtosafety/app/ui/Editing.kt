package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

// Pieces the contacts and places screens share (their RN styles are identical).

/**
 * The header's action (Add / Update / Save / Saving...): the screen's main action, so it is a
 * filled primary button in the same rounded rectangle as the body's primary buttons (a tonal
 * one read as too weak). The container is 40dp inside a 48dp target and ends at the 16dp
 * margin; while it can't be pressed (Saving...) it takes PrimaryButton's dimmed look.
 */
@Composable
fun HeaderSaveButton(label: String, testID: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Box(
        Modifier.testTag(testID).heightIn(min = 48.udp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 40.udp).widthIn(min = 72.udp).clip(shape)
                .background(if (enabled) colors.primary else colors.primaryLight)
                .padding(horizontal = DesignTokens.Spacing.lg.udp, vertical = DesignTokens.Spacing.sm.udp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = DesignTokens.Typography.bodyBold.style(), color = if (enabled) colors.textOnPrimary else colors.primaryText, maxLines = 1)
        }
    }
}

/**
 * roleOption / optionButton: an icon and a caption (500) in a bordered chip, 48dp tall to tap.
 * Selected is said three ways, not by fill alone: the fill, the ink on it ([selectedContent]),
 * and a check mark at the end. The check's space is kept when unselected, so choosing a chip
 * never changes how its label wraps.
 */
@Composable
fun OptionChip(
    label: String,
    icon: String,
    testID: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    selectedContent: Color = LocalAppColors.current.textOnPrimary,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Row(
        modifier.testTag(testID).heightIn(min = 48.udp).clip(shape).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) selectedColor else colors.inputBackground)
            .rnBorder(1.dp, if (selected) selectedColor else colors.inputBorder, shape)
            .padding(horizontal = DesignTokens.Spacing.md.udp, vertical = DesignTokens.Spacing.sm.udp),
        horizontalArrangement = Arrangement.spacedBy(6.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 16f, if (selected) selectedContent else colors.icon)
        Text(
            label, style = DesignTokens.Typography.caption.style(fontWeight = if (selected) 700 else 500),
            color = if (selected) selectedContent else colors.text, modifier = Modifier.weight(1f),
        )
        Icon("checkmark", 16f, selectedContent, Modifier.alpha(if (selected) 1f else 0f))
    }
}

/** formDeleteButton: the outlined destructive button at the bottom of an edit form. */
@Composable
fun FormDeleteButton(label: String, testID: String, onClick: () -> Unit) {
    val error = Color(DesignTokens.Semantic.error)
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.md.udp).heightIn(min = 48.udp).testTag(testID).clip(shape)
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

/**
 * Option chips in two columns at equal width, every chip as tall as the tallest, so a long label
 * ("Primary Caregiver" at large text) wraps inside its chip and the grid stays even. A single
 * column at large text pushed the rest of the form ("Notify in Emergency") off the screen.
 * Mirrors iOS `ChipGrid`.
 */
@Composable
fun <T> ChipGrid(items: List<T>, chip: @Composable (item: T, modifier: Modifier) -> Unit) {
    val gap = DesignTokens.Spacing.sm.udp
    Layout({ for (item in items) chip(item, Modifier) }) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val cell = (width - gapPx) / 2
        val rowHeight = measurables.maxOfOrNull { it.minIntrinsicHeight(cell) } ?: 0
        val placeables = measurables.map { it.measure(Constraints(minWidth = cell, maxWidth = cell, minHeight = rowHeight)) }
        val height = placeables.chunked(2).sumOf { row -> row.maxOf { it.height } } + gapPx * ((placeables.size + 1) / 2 - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            for (row in placeables.chunked(2)) {
                row.forEachIndexed { i, p -> p.place(i * (cell + gapPx), y) }
                y += row.maxOf { it.height } + gapPx
            }
        }
    }
}
