package com.backtosafety.app.profile

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.backtosafety.app.ui.Icon
import com.backtosafety.app.ui.LocalAppColors
import com.backtosafety.app.ui.RnTextField
import com.backtosafety.app.ui.ScreenHeader
import com.backtosafety.app.ui.UnsavedChangesGuard
import com.backtosafety.app.ui.rememberPhoto
import com.backtosafety.app.ui.rememberPhotoPicker
import com.backtosafety.app.ui.reportSaveFailed
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.runCatchingCancellable
import com.backtosafety.app.ui.showAlert
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.Analytics
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.AppClock
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.MOBILITY_OPTIONS
import com.backtosafety.core.MOBILITY_OPTION_KEYS
import com.backtosafety.core.PROFILE_TEXT_FIELDS
import com.backtosafety.core.ProfileField
import com.backtosafety.core.ProfileKeyboard
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.formatDob
import com.backtosafety.core.invoke
import com.backtosafety.core.parseDob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private val space = DesignTokens.Spacing
private val type = DesignTokens.Typography
private val white = Color(DesignTokens.Light.textOnPrimary)

private data class ProfileForm(
    val text: Map<ProfileField, String> = PROFILE_TEXT_FIELDS.associateWith { "" },
    val dominantHand: String = "unknown",
    val mobility: List<String> = emptyList(),
    val mobilityOther: String = "",
) {
    operator fun get(field: ProfileField) = text[field].orEmpty()
    fun with(field: ProfileField, value: String) = copy(text = text + (field to value))

    /** utils: the selected options, with "Other" replaced by its text. */
    val resolvedMobility: String
        get() = (mobility.filter { it != "Other" } + listOfNotNull(mobilityOther.trim().ifEmpty { null }.takeIf { "Other" in mobility }))
            .joinToString(", ")
}

/** Port of app/profile.tsx: the full profile editor, in four collapsible sections. */
@Composable
fun ProfileScreen(t: Translate, tCommon: Translate, store: Store, onBack: () -> Unit, onSaved: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<ProfileForm?>(null) }
    var initial by remember { mutableStateOf<ProfileForm?>(null) }
    var expanded by remember { mutableStateOf<String?>("personal") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val p = store.profile()
        val tokens = p?.mobilityLevel.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val known = tokens.filter { it in MOBILITY_OPTIONS }
        val custom = tokens.filter { it !in MOBILITY_OPTIONS }
        val values = PROFILE_TEXT_FIELDS.associateWith { field -> p?.get(field).orEmpty() }
        val loaded = ProfileForm(
            text = values,
            dominantHand = p?.dominantHand?.ifEmpty { null } ?: "unknown",
            mobility = if (custom.isNotEmpty()) known + "Other" else known,
            mobilityOther = custom.joinToString(", "),
        )
        form = loaded
        initial = loaded
    }

    val f = form

    // The snapshot compares what would be saved (RN compares the form plus resolved mobility).
    fun snapshot(x: ProfileForm?) = x?.let { Triple(it.text, it.dominantHand, it.resolvedMobility) }
    val unsaved = f != null && snapshot(f) != snapshot(initial)

    fun save() {
        val x = form ?: return
        // name is NOT NULL: an empty one is refused rather than failing silently (F-38).
        if (x[ProfileField.FULL_NAME].isBlank()) return showAlert(context, tCommon("required"), t("errors.nameRequired"))
        saving = true
        scope.launch {
            fun v(field: ProfileField) = x[field].trim().ifEmpty { null }
            runCatchingCancellable {
                // A null clears the field, so every field on the screen is in the map.
                val values = buildMap<ProfileField, String?> {
                    for (field in PROFILE_TEXT_FIELDS) put(field, v(field))
                    // The photo's path isn't trimmed.
                    put(ProfileField.PHOTO_URI, x[ProfileField.PHOTO_URI].ifEmpty { null })
                    put(ProfileField.DOMINANT_HAND, x.dominantHand)
                    put(ProfileField.MOBILITY_LEVEL, x.resolvedMobility.ifEmpty { null })
                }
                store.updateProfile(values)
            }.onSuccess {
                Analytics.track(AnalyticsEvent.PROFILE_SAVED,
                    mapOf(
                        "has_photo" to x[ProfileField.PHOTO_URI].isNotEmpty(), "has_medical" to x[ProfileField.MEDICAL_CONDITIONS].isNotEmpty(),
                        "has_medications" to x[ProfileField.MEDICATIONS].isNotEmpty(),
                    ),
                )
                initial = x
                onSaved()
            }.onFailure { e ->
                reportSaveFailed("profile", "save", e)
                showAlert(context, tCommon("error"), tCommon("saveFailed"))
            }
            saving = false
        }
    }

    fun back() {
        if (!unsaved || saving) return onBack()
        showAlert(
            context, t("unsavedChanges.title"), t("unsavedChanges.message"),
            cancel = t("unsavedChanges.cancelLabel", mapOf("defaultValue" to "Keep Editing")),
            confirm = t("unsavedChanges.confirmLabel") to onBack,
        )
    }

    UnsavedChangesGuard(
        enabled = unsaved && !saving,
        title = t("unsavedChanges.title"), message = t("unsavedChanges.message"),
        keepEditing = "Keep Editing", discard = t("unsavedChanges.confirmLabel"),
        onDiscard = {}, onLeave = onBack,
    )

    val picker = rememberPhotoPicker { uri -> form = form?.with(ProfileField.PHOTO_URI, uri) }

    // SafeAreaView with every edge, as RN's profile screen uses.
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(t("screenTitle"), "profile", ::back) {
            Box(
                Modifier.testTag("profile-save").widthIn(min = 72.udp).clip(RoundedCornerShape(DesignTokens.Radius.md.dp))
                    .clickable(enabled = !saving, onClick = ::save).background(colors.tint)
                    .padding(horizontal = space.lg.udp, vertical = space.sm.udp),
                contentAlignment = Alignment.Center,
            ) { Text(t(if (saving) "saving" else "save"), style = type.bodyBold.style(), color = white, maxLines = 1) }
        }
        if (f == null) return@Column
        // Reads the form at call time, never `f`: fields keep the callback from the composition
        // they were drawn in, so an edit built on that snapshot reverted every edit since.
        fun update(field: ProfileField, value: String) {
            form = form?.with(field, value)
        }
        val toggle: (String) -> Unit = { expanded = if (expanded == it) null else it }
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(space.lg.udp)) {
            PhotoSection(
                t, f[ProfileField.PHOTO_URI],
                onCamera = { Analytics.track(AnalyticsEvent.PROFILE_PHOTO_TAKEN); picker.fromCamera() },
                onLibrary = { Analytics.track(AnalyticsEvent.PROFILE_PHOTO_CHOSEN); picker.fromLibrary() },
            )

            Section("personal", t("sections.personal"), "person.fill", expanded, toggle) {
                Field(t, ProfileField.FULL_NAME, f, ::update)
                Field(t, ProfileField.NICKNAME, f, ::update)
                DateOfBirth(t("fields.dateOfBirth"), f[ProfileField.DATE_OF_BIRTH], { update(ProfileField.DATE_OF_BIRTH, it) })
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    Box(Modifier.weight(1f)) {
                        Field(t, ProfileField.HEIGHT, f, ::update)
                    }
                    Box(Modifier.weight(1f)) {
                        Field(t, ProfileField.WEIGHT, f, ::update)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    Box(Modifier.weight(1f)) { Field(t, ProfileField.HAIR_COLOR, f, ::update) }
                    Box(Modifier.weight(1f)) { Field(t, ProfileField.EYE_COLOR, f, ::update) }
                }
                Field(t, ProfileField.IDENTIFYING_MARKS, f, ::update)
            }

            Section("medical", t("sections.medical"), "cross.fill", expanded, toggle) {
                Field(t, ProfileField.MEDICAL_CONDITIONS, f, ::update)
                Field(t, ProfileField.MEDICATIONS, f, ::update)
                Field(t, ProfileField.ALLERGIES, f, ::update)
                Field(t, ProfileField.COGNITIVE_STATUS, f, ::update)
                Group(t("fields.dominantHand"), t("fields.dominantHandHint")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                        for (hand in listOf("left", "right", "unknown")) {
                            val selected = f.dominantHand == hand
                            val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
                            Box(
                                Modifier.weight(1f).testTag("profile-hand-$hand").clip(shape)
                                    .clickable { form = f.copy(dominantHand = hand) }
                                    .background(if (selected) colors.tint else Color.Transparent)
                                    .rnBorder(1.dp, if (selected) colors.tint else colors.border, shape)
                                    .padding(vertical = space.sm.udp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    t("fields.dominantHand${hand.replaceFirstChar { it.uppercase() }}"),
                                    style = type.body.style(fontWeight = 500), color = if (selected) white else colors.text,
                                )
                            }
                        }
                    }
                }
                Group(t("fields.mobilityLevel")) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm.udp), verticalArrangement = Arrangement.spacedBy(space.sm.udp)) {
                        for (option in MOBILITY_OPTIONS) {
                            val selected = option in f.mobility
                            val shape = RoundedCornerShape(DesignTokens.Radius.full.dp)
                            Box(
                                Modifier.testTag("profile-mobility-${option.lowercase().replace(' ', '-')}").clip(shape)
                                    .clickable {
                                        form = if (selected) {
                                            f.copy(mobility = f.mobility - option, mobilityOther = if (option == "Other") "" else f.mobilityOther)
                                        } else f.copy(mobility = f.mobility + option)
                                    }
                                    .background(if (selected) colors.tint else Color.Transparent)
                                    .rnBorder(1.dp, if (selected) colors.tint else colors.border, shape)
                                    .padding(horizontal = space.md.udp, vertical = space.xs.udp),
                            ) {
                                Text(
                                    t(MOBILITY_OPTION_KEYS.getValue(option)), style = type.body.style(fontWeight = 500),
                                    color = if (selected) white else colors.text,
                                )
                            }
                        }
                    }
                    if ("Other" in f.mobility) {
                        RnTextField(
                            f.mobilityOther, { form = f.copy(mobilityOther = it) }, "profile-mobility-other-text",
                            placeholder = t("fields.mobilityOtherPlaceholder"),
                            background = colors.card, border = colors.border, modifier = Modifier.padding(top = space.sm.udp),
                        )
                    }
                }
            }

            Section("communication", t("sections.communication"), "bubble.left.fill", expanded, toggle) {
                Field(t, ProfileField.COMMUNICATION_PREFERENCE, f, ::update)
                Field(t, ProfileField.ESCALATION_SIGNS, f, ::update)
                Field(t, ProfileField.DEESCALATION_TECHNIQUES, f, ::update)
                Field(t, ProfileField.APPROACH_GUIDANCE, f, ::update)
                Field(t, ProfileField.LIKES, f, ::update)
                Field(t, ProfileField.DISLIKES_TRIGGERS, f, ::update)
                Field(t, ProfileField.SAFE_WORD, f, ::update)
            }

            Section("devices", t("sections.devices"), "location.fill", expanded, toggle) {
                Field(t, ProfileField.LOCATIVE_DEVICE_INFO, f, ::update)
                Field(t, ProfileField.ID_BRACELETS, f, ::update)
                Field(t, ProfileField.MEDIC_ALERT_ID, f, ::update)
                Field(t, ProfileField.MEDIC_ALERT_HOTLINE, f, ::update)
            }
            Box(Modifier.height(40.udp))
        }
    }
}

@Composable
private fun PhotoSection(t: Translate, photoUri: String, onCamera: () -> Unit, onLibrary: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = space.xl.udp), horizontalAlignment = Alignment.CenterHorizontally) {
        val photo = rememberPhoto(photoUri.ifEmpty { null })
        Box(Modifier.padding(bottom = space.md.udp)) {
            if (photo != null) {
                Image(photo, null, Modifier.size(120.udp).clip(CircleShape).testTag("profile-photo"), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.size(120.udp).clip(CircleShape).background(colors.card).drawBehind {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            colors.border, radius = size.minDimension / 2 - stroke / 2,
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon("camera.fill", 38f, colors.textSecondary) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
            for ((label, tag, bg, action) in listOf(
                Quad(t("takePhoto"), "profile-photo-take", colors.tint, onCamera),
                Quad(t("choosePhoto"), "profile-photo-library", colors.primary, onLibrary),
            )) {
                Box(
                    Modifier.testTag(tag).clip(RoundedCornerShape(DesignTokens.Radius.md.dp)).clickable(onClick = action)
                        .background(bg).padding(horizontal = space.lg.udp, vertical = space.sm.udp),
                ) { Text(label, style = type.bodyBold.style(), color = white) }
            }
        }
    }
}

private data class Quad(val label: String, val tag: String, val bg: Color, val action: () -> Unit)

@Composable
private fun Section(
    key: String, title: String, icon: String, expanded: String?, onToggle: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(DesignTokens.Radius.lg.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = space.md.udp).clip(shape).background(colors.card).rnBorder(1.dp, colors.border, shape)) {
        Row(
            Modifier.fillMaxWidth().testTag("profile-section-$key").clickable { onToggle(key) }.padding(space.lg.udp),
            horizontalArrangement = Arrangement.spacedBy(space.md.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.udp).clip(CircleShape).background(colors.primaryLight), contentAlignment = Alignment.Center) {
                Icon(icon, 16f, colors.primary)
            }
            Text(title, style = type.bodyBold.style(), color = colors.text, modifier = Modifier.weight(1f))
            Icon(if (expanded == key) "chevron.up" else "chevron.down", 16f, colors.textSecondary)
        }
        if (expanded == key) {
            Column(Modifier.padding(start = space.lg.udp, end = space.lg.udp, bottom = space.lg.udp), content = content)
        }
    }
}

/** inputGroup: a bold label, an optional hint, then the content; margin below lg. */
@Composable
private fun Group(label: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = space.lg.udp)) {
        Text(label, style = type.bodyBold.style(), color = colors.text, modifier = Modifier.padding(bottom = space.xs.udp))
        if (!hint.isNullOrEmpty()) {
            Text(hint, style = type.caption.style(), color = colors.textSecondary, modifier = Modifier.padding(bottom = space.xs.udp))
        }
        content()
    }
}

@Composable
private fun Field(t: Translate, field: ProfileField, form: ProfileForm, onChange: (ProfileField, String) -> Unit) {
    val colors = LocalAppColors.current
    Group(t("fields.${field.key}"), if (field.hasHint) t("fields.${field.key}Hint") else null) {
        RnTextField(
            form[field], { onChange(field, field.formatInput(it)) }, "profile-field-${field.key}",
            placeholder = t("fields.${field.key}Placeholder"), multiline = field.multiline,
            keyboardType = when (field.keyboard) {
                ProfileKeyboard.TEXT -> KeyboardType.Text
                ProfileKeyboard.NUMBER -> KeyboardType.Number
                ProfileKeyboard.PHONE -> KeyboardType.Phone
            },
            capitalization = if (field.capitalizeWords) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            background = colors.card, border = colors.border,
        )
    }
}

/**
 * The date of birth: typed as MM/DD/YYYY, or picked in the platform date dialog. As in RN, a
 * bordered row (padding md) holds the 44dp input and the calendar button beside it.
 */
@Composable
private fun DateOfBirth(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(DesignTokens.Radius.md.dp)
    Group(label) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.udp).clip(shape).background(colors.card)
                .rnBorder(1.dp, colors.border, shape).padding(horizontal = space.md.udp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RnTextField(
                value, { onChange(ProfileField.DATE_OF_BIRTH.formatInput(it)) }, "profile-field-dateOfBirth",
                placeholder = "MM/DD/YYYY", keyboardType = KeyboardType.Number, maxLength = 10,
                background = Color.Transparent, border = Color.Transparent, bare = true,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.testTag("profile-dob-calendar").sizeIn(minWidth = 44.udp, minHeight = 44.udp)
                    .clickable {
                        val initial = parseDob(value) ?: LocalDate(1940, 1, 1)
                        DatePickerDialog(context, { _, y, m, d -> onChange(formatDob(LocalDate(y, m + 1, d))) }, initial.year, initial.month.ordinal, initial.day)
                            .apply { datePicker.maxDate = AppClock.nowMs() }
                            .show()
                    }
                    .padding(start = space.sm.udp),
                contentAlignment = Alignment.Center,
            ) { Icon("calendar", 18f, colors.textSecondary) }
        }
    }
}
