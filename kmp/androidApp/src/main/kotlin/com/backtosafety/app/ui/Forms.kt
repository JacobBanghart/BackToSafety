package com.backtosafety.app.ui

import android.app.AlertDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.backtosafety.core.DesignTokens

/**
 * components/AppTextInput.tsx: a bold label (with " *" when required), an optional hint, and
 * the field: 44dp single-line or an 80-120dp text area, 1dp border (focused: borderFocused),
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
 * (inside the box), radius md, padding md horizontally; 44dp single-line, or a top-aligned
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
    val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
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
                else Modifier.height(44.udp),
            )
            .then(
                if (bare) Modifier
                // RN's border is inside the box, so it's drawn over the padding, not around it.
                else Modifier.clip(shape).background(background).border(1.dp, border, shape),
            ),
        decorationBox = { inner ->
            Row(
                Modifier.padding(
                    start = if (bare) 0.dp else (DesignTokens.Spacing.md + 1).udp,
                    end = if (bare) 0.dp else (DesignTokens.Spacing.md + 1).udp,
                )
                    .padding(vertical = if (multiline) (textAreaPaddingV + 1).udp else 0.dp),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.inputPlaceholder)
                    inner()
                }
            }
        },
    )
}

/** The custom on/off switch on the contact form: 50x30, a 24dp knob that slides 20dp. */
@Composable
fun Toggle(on: Boolean) {
    val colors = LocalAppColors.current
    Box(
        Modifier.size(50.udp, 30.udp).clip(RoundedCornerShape(15.udp))
            .background(if (on) Color(DesignTokens.Semantic.success) else colors.border).padding(3.udp),
    ) {
        Box(
            Modifier.offset(x = if (on) 20.udp else 0.dp).size(24.udp).clip(CircleShape)
                .background(Color(DesignTokens.Light.background)),
        )
    }
}

/** RN's Alert.alert on Android: the platform dialog. Buttons are (label, action). */
fun showAlert(
    context: Context,
    title: String,
    message: String? = null,
    cancel: String? = null,
    confirm: Pair<String, () -> Unit> = "OK" to {},
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
