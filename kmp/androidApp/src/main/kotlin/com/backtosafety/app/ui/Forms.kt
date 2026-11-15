package com.backtosafety.app.ui

import android.app.AlertDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/**
 * components/AppTextInput.tsx: a bold label (with " *" when required), an optional hint, and
 * the field: 48dp single-line (a full touch target) or an 80-120dp text area, 1dp border (focused: borderFocused),
 * radius md, body text on a 20 line height. Field margin below: md.
 */
@Composable
fun FormTextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testID: String,
    placeholder: String = "",
    hint: String? = null,
    required: Boolean = false,
    multiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = LocalAppColors.current
    val space = DesignTokens.Spacing
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = space.md.udp)) {
        Text(
            if (required) "$label *" else label, style = DesignTokens.Typography.bodyBold.style(), color = colors.text,
            modifier = Modifier.padding(bottom = space.xs.udp),
        )
        if (!hint.isNullOrEmpty()) {
            Text(hint, style = DesignTokens.Typography.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xs.udp))
        }
        RnTextField(
            value, onValueChange, testID, placeholder, multiline, keyboardType, capitalization,
            background = colors.inputBackground,
            border = if (focused) colors.borderFocused else colors.inputBorder,
            textAreaMaxHeight = 120f,
            textAreaPaddingV = space.sm,
            onFocusChanged = { focused = it },
        )
    }
}

/**
 * An RN TextInput as the forms style it on Android: body text on a 20 line height, 1dp border
 * (inside the box), radius md, padding md horizontally; 48dp single-line, or a top-aligned
 * text area from 80dp. Formatters rewrite the text on change; the cursor stays at the end the
 * way RN's controlled input keeps it.
 */
@Composable
fun RnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    testID: String,
    placeholder: String = "",
    multiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    background: Color,
    border: Color,
    textAreaMaxHeight: Float? = null,
    textAreaPaddingV: Float = 10f,
    maxLength: Int? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    /** Just the input: no border or horizontal padding (it sits inside a bordered row). */
    bare: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val textStyle = rnTextStyle(16f, 20f)
    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = field,
        onValueChange = {
            val text = maxLength?.let { max -> it.text.take(max) } ?: it.text
            field = it.copy(text = text)
            onValueChange(text)
        },
        singleLine = !multiline,
        textStyle = textStyle.copy(color = colors.text),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testID)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .then(
                if (multiline) Modifier.heightIn(min = 80.udp, max = textAreaMaxHeight?.udp ?: androidx.compose.ui.unit.Dp.Infinity)
                else Modifier.height(48.udp),
            ),
        interactionSource = interaction,
        decorationBox = { inner ->
            if (bare) {
                Box(contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.inputPlaceholder)
                    inner()
                }
            } else {
                OutlinedFieldDecoration(
                    value, inner, interaction, multiline, { Text(placeholder, style = textStyle, color = colors.inputPlaceholder) },
                    background = background, border = border,
                    padding = PaddingValues(
                        horizontal = (DesignTokens.Spacing.md + 1).udp,
                        vertical = if (multiline) (textAreaPaddingV + 1).udp else 0.dp,
                    ),
                )
            }
        },
    )
}

/**
 * Material 3's outlined text field container around a [BasicTextField]: the platform's outline
 * (1dp at rest, 2dp in borderFocused while focused), container colour and placeholder, so the
 * app's fields read as Android's own. The field itself (its testTag, formatting and cursor
 * handling) is unchanged; the label stays above the field, as both apps show it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutlinedFieldDecoration(
    value: String,
    inner: @Composable () -> Unit,
    interaction: MutableInteractionSource,
    multiline: Boolean,
    placeholder: @Composable () -> Unit,
    background: Color,
    border: Color,
    padding: PaddingValues,
) {
    val colors = LocalAppColors.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = background, unfocusedContainerColor = background,
        focusedBorderColor = colors.borderFocused, unfocusedBorderColor = border,
        cursorColor = colors.tint,
    )
    OutlinedTextFieldDefaults.DecorationBox(
        value = value,
        innerTextField = inner,
        enabled = true,
        singleLine = !multiline,
        visualTransformation = VisualTransformation.None,
        interactionSource = interaction,
        placeholder = placeholder,
        colors = fieldColors,
        contentPadding = padding,
        container = {
            OutlinedTextFieldDefaults.Container(
                enabled = true, isError = false, interactionSource = interaction, colors = fieldColors,
                shape = RoundedCornerShape(DesignTokens.Radius.lg.dp),
                focusedBorderThickness = 2.dp, unfocusedBorderThickness = 1.dp,
            )
        },
    )
}

/**
 * The contact form's on/off switch: Material 3's Switch, coloured by the theme (on is the brand
 * primary). It only draws: the row around it is the toggleable control, so a tap anywhere on the
 * row flips it and TalkBack reads the row as one switch.
 */
@Composable
fun Toggle(on: Boolean) {
    Switch(checked = on, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
}

/** RN's Alert.alert on Android: the platform dialog. Buttons are (label, action); an alert with nothing to confirm passes `tCommon("ok") to {}`. */
fun showAlert(
    context: Context,
    title: String,
    message: String? = null,
    cancel: String? = null,
    confirm: Pair<String, () -> Unit>,
) {
    AlertDialog.Builder(context).setTitle(title).apply { if (message != null) setMessage(message) }
        .apply { if (cancel != null) setNegativeButton(cancel, null) }
        .setPositiveButton(confirm.first) { _, _ -> confirm.second() }
        .show()
}

/**
 * hooks/useUnsavedChangesGuard.ts: leaving the screen (Android back) with unsaved changes asks
 * first; Discard runs [onDiscard] and then leaves via [onLeave].
 */
@Composable
fun UnsavedChangesGuard(
    enabled: Boolean,
    title: String,
    message: String,
    keepEditing: String,
    discard: String,
    onDiscard: () -> Unit,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(enabled) {
        showAlert(context, title, message, cancel = keepEditing, confirm = discard to {
            onDiscard()
            onLeave()
        })
    }
}
