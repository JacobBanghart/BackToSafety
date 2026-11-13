package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

private val space = DesignTokens.Spacing
private val radius = DesignTokens.Radius

/** components/OnboardingStepHeader.tsx: the platform back button, step dots, and a balancing spacer. */
@Composable
fun OnboardingStepHeader(activeStep: Int, totalSteps: Int, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = space.xxl.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(space.sm.udp, Alignment.CenterHorizontally)) {
            for (step in 1..totalSteps) {
                val on = step <= activeStep
                Box(
                    Modifier
                        .height(8.udp)
                        .width(if (step == activeStep) 24.udp else 8.udp)
                        .clip(RoundedCornerShape(radius.sm.dp))
                        .background(if (on) colors.primary else colors.border),
                )
            }
        }
        Box(Modifier.size(48.udp))
    }
}

/**
 * The onboarding text input: 48dp tall (or a growing text area), 1dp border, radius lg.
 * RN Android sets lineHeight 20 or 22 on these inputs; [textStyle] carries it.
 */
@Composable
fun AppInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val colors = LocalAppColors.current
    // Formatting on change (phone, height) rewrites the text; keep the cursor at the end
    // the way RN's controlled TextInput does, or the next keystroke lands mid-number.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        singleLine = !multiline,
        textStyle = textStyle.copy(color = colors.text),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .then(if (multiline) Modifier.heightIn(min = 80.udp) else Modifier.height(48.udp)),
        interactionSource = interaction,
        decorationBox = { field ->
            OutlinedFieldDecoration(
                value, field, interaction, multiline, { Text(placeholder, style = textStyle, color = colors.inputPlaceholder) },
                background = colors.inputBackground, border = colors.inputBorder,
                padding = PaddingValues(horizontal = space.lg.udp, vertical = if (multiline) space.sm.udp else 0.dp),
            )
        },
    )
}

/** The filled call-to-action button used across onboarding. */
@Composable
fun PrimaryButton(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    verticalPadding: Float = space.md,
    /** Looks disabled without being disabled (a tap can still explain what's missing). */
    dimmed: Boolean = !enabled,
) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(radius.lg.dp))
            // Not ready yet: a pale fill with the primary text on it, which still reads at
            // over 4.5:1 in both themes (half-transparent white on primary didn't).
            .background(if (dimmed) colors.primaryLight else colors.primary)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.udp)
            .padding(vertical = verticalPadding.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = textStyle, color = if (dimmed) colors.primaryText else colors.textOnPrimary)
    }
}

/** "Skip for now": a quiet text button, still legible (textSecondary) and 48dp tall. */
@Composable
fun SkipButton(label: String, testTag: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().testTag(testTag).heightIn(min = 48.udp).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = space.sm.udp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DesignTokens.Typography.body.style(), color = colors.textSecondary)
    }
}

/**
 * Buttons side by side at their natural width, centred, while they fit; otherwise stacked, each
 * the full width. At large text two buttons in a row ran to within a few pixels of the screen
 * edge and past the screen's margin. Mirrors iOS `ButtonRowOrStack`.
 */
@Composable
fun ButtonRowOrStack(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val fits = natural.sum() + gapPx * (measurables.size - 1) <= constraints.maxWidth
        if (fits) {
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(natural[i])) }
            val width = placeables.sumOf { it.width } + gapPx * (placeables.size - 1)
            val height = placeables.maxOf { it.height }
            layout(maxOf(width, constraints.minWidth), height) {
                var x = (maxOf(width, constraints.minWidth) - width) / 2
                for (p in placeables) {
                    p.place(x, (height - p.height) / 2)
                    x += p.width + gapPx
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(constraints.maxWidth)) }
            val height = placeables.sumOf { it.height } + gapPx * (placeables.size - 1)
            layout(constraints.maxWidth, height) {
                var y = 0
                for (p in placeables) {
                    p.place(0, y)
                    y += p.height + gapPx
                }
            }
        }
    }
}
