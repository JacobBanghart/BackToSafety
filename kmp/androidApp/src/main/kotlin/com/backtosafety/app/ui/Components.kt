package com.backtosafety.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

private val space = DesignTokens.Spacing
private val radius = DesignTokens.Radius

/** components/OnboardingStepHeader.tsx: back chevron, step dots, and a balancing spacer. */
@Composable
fun OnboardingStepHeader(activeStep: Int, totalSteps: Int, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = space.xxl.udp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.sizeIn(minWidth = 44.udp, minHeight = 44.udp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon("chevron.left", 20f, colors.tint)
        }
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
        Box(Modifier.sizeIn(minWidth = 44.udp, minHeight = 44.udp))
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
            .then(if (multiline) Modifier.heightIn(min = 80.udp) else Modifier.height(48.udp))
            .clip(RoundedCornerShape(radius.lg.dp))
            .background(colors.inputBackground)
            .border(1.udp, colors.inputBorder, RoundedCornerShape(radius.lg.dp)),
        decorationBox = { field ->
            Box(
                Modifier.padding(horizontal = space.lg.udp, vertical = if (multiline) space.sm.udp else 0.dp),
                contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.inputPlaceholder)
                field()
            }
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
            .alpha(if (dimmed) 0.5f else 1f)
            .clip(RoundedCornerShape(radius.lg.dp))
            .background(colors.primary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = verticalPadding.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = textStyle, color = colors.textOnPrimary)
    }
}

/** "Skip for now": a quiet text button. */
@Composable
fun SkipButton(label: String, testTag: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().testTag(testTag).clickable(onClick = onClick).padding(vertical = space.sm.udp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DesignTokens.Typography.body.style(), color = colors.textDisabled)
    }
}
