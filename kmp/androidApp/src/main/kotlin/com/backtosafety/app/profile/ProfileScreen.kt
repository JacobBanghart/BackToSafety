package com.backtosafety.app.profile

import android.app.DatePickerDialog
import com.backtosafety.core.AnalyticsEvent
import com.backtosafety.core.Analytics
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
import com.backtosafety.app.ui.rnBorder
import com.backtosafety.app.ui.showAlert
import com.backtosafety.app.ui.style
import com.backtosafety.app.ui.udp
import com.backtosafety.core.AppClock
import com.backtosafety.core.DesignTokens
import com.backtosafety.core.MOBILITY_OPTIONS
import com.backtosafety.core.MOBILITY_OPTION_KEYS
import com.backtosafety.core.Translate
import com.backtosafety.core.data.Store
import com.backtosafety.core.formatDob
import com.backtosafety.core.formatDobInput
import com.backtosafety.core.formatHeightInput
import com.backtosafety.core.formatMedicAlertIdInput
import com.backtosafety.core.formatPhoneInput
import com.backtosafety.core.formatWeightInput
import com.backtosafety.core.invoke
import com.backtosafety.core.parseDob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private val space = DesignTokens.Spacing
private val type = DesignTokens.Typography
private val white = Color(DesignTokens.Light.textOnPrimary)

/** Every text field on the screen, by its RN form key (testID profile-field-<key>). */
private val TEXT_FIELDS = listOf(
    "name", "nickname", "dateOfBirth", "photoUri", "height", "weight", "hairColor", "eyeColor", "identifyingMarks",
    "medicalConditions", "medications", "allergies", "cognitiveStatus",
    "communicationPreference", "escalationSigns", "deescalationTechniques", "approachGuidance", "likes",
    "dislikesTriggers", "safeWord", "locativeDeviceInfo", "idBracelets", "medicAlertId", "medicAlertHotline",
)

private data class ProfileForm(
    val text: Map<String, String> = TEXT_FIELDS.associateWith { "" },
    val dominantHand: String = "unknown",
    val mobility: List<String> = emptyList(),
    val mobilityOther: String = "",
) {
    operator fun get(field: String) = text[field].orEmpty()
    fun with(field: String, value: String) = copy(text = text + (field to value))

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
        val values = mapOf(
            "name" to p?.name, "nickname" to p?.nickname, "dateOfBirth" to p?.dateOfBirth, "photoUri" to p?.photoUri,
            "height" to p?.height, "weight" to p?.weight, "hairColor" to p?.hairColor, "eyeColor" to p?.eyeColor,
            "identifyingMarks" to p?.identifyingMarks, "medicalConditions" to p?.medicalConditions,
            "medications" to p?.medications, "allergies" to p?.allergies, "cognitiveStatus" to p?.cognitiveStatus,
            "communicationPreference" to p?.communicationPreference, "escalationSigns" to p?.escalationSigns,
            "deescalationTechniques" to p?.deescalationTechniques, "approachGuidance" to p?.approachGuidance,
            "likes" to p?.likes, "dislikesTriggers" to p?.dislikesTriggers, "safeWord" to p?.safeWord,
            "locativeDeviceInfo" to p?.locativeDeviceInfo, "idBracelets" to p?.idBracelets,
            "medicAlertId" to p?.medicAlertId, "medicAlertHotline" to p?.medicAlertHotline,
        ).mapValues { it.value.orEmpty() }
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
        if (x["name"].isBlank()) return showAlert(context, tCommon("required"), t("errors.nameRequired"))
        saving = true
        scope.launch {
            fun v(field: String) = x[field].trim().ifEmpty { null }
            runCatching {
                store.saveProfile {
                    it.copy(
                        name = v("name")!!, nickname = v("nickname"), dateOfBirth = v("dateOfBirth"),
                        photoUri = x["photoUri"].ifEmpty { null }, height = v("height"), weight = v("weight"),
                        hairColor = v("hairColor"), eyeColor = v("eyeColor"), identifyingMarks = v("identifyingMarks"),
                        medicalConditions = v("medicalConditions"), medications = v("medications"), allergies = v("allergies"),
                        cognitiveStatus = v("cognitiveStatus"), dominantHand = x.dominantHand,
                        mobilityLevel = x.resolvedMobility.ifEmpty { null },
                        communicationPreference = v("communicationPreference"), escalationSigns = v("escalationSigns"),
                        deescalationTechniques = v("deescalationTechniques"), approachGuidance = v("approachGuidance"),
                        likes = v("likes"), dislikesTriggers = v("dislikesTriggers"), safeWord = v("safeWord"),
                        locativeDeviceInfo = v("locativeDeviceInfo"), idBracelets = v("idBracelets"),
                        medicAlertId = v("medicAlertId"), medicAlertHotline = v("medicAlertHotline"),
                    )
                }
            }.onSuccess {
                Analytics.track(AnalyticsEvent.PROFILE_SAVED,
                    mapOf("has_photo" to x["photoUri"].isNotEmpty(), "has_medical" to x["medicalConditions"].isNotEmpty(), "has_medications" to x["medications"].isNotEmpty()),
                )
                initial = x
                onSaved()
            }.onFailure { showAlert(context, tCommon("error"), tCommon("saveFailed")) }
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

    val picker = rememberPhotoPicker { uri -> form = form?.with("photoUri", uri) }

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
        fun update(field: String, value: String) {
            form = f.with(field, value)
        }
        val toggle: (String) -> Unit = { expanded = if (expanded == it) null else it }
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(space.lg.udp)) {
            PhotoSection(
                t, f["photoUri"],
                onCamera = { Analytics.track(AnalyticsEvent.PROFILE_PHOTO_TAKEN); picker.fromCamera() },
                onLibrary = { Analytics.track(AnalyticsEvent.PROFILE_PHOTO_CHOSEN); picker.fromLibrary() },
            )

            Section("personal", t("sections.personal"), "person.fill", expanded, toggle) {
                Field(t("fields.name"), "name", f, ::update, t("fields.namePlaceholder"), capitalization = KeyboardCapitalization.Words)
                Field(t("fields.nickname"), "nickname", f, ::update, t("fields.nicknamePlaceholder"), capitalization = KeyboardCapitalization.Words)
                DateOfBirth(t("fields.dateOfBirth"), f["dateOfBirth"], { update("dateOfBirth", it) })
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    Box(Modifier.weight(1f)) {
                        Field(t("fields.height"), "height", f, ::update, t("fields.heightPlaceholder"), keyboardType = KeyboardType.Number)
                    }
                    Box(Modifier.weight(1f)) {
                        Field(t("fields.weight"), "weight", f, ::update, t("fields.weightPlaceholder"), keyboardType = KeyboardType.Number)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(space.md.udp)) {
                    Box(Modifier.weight(1f)) { Field(t("fields.hairColor"), "hairColor", f, ::update, t("fields.hairColorPlaceholder")) }
                    Box(Modifier.weight(1f)) { Field(t("fields.eyeColor"), "eyeColor", f, ::update, t("fields.eyeColorPlaceholder")) }
                }
                Field(
                    t("fields.identifyingMarks"), "identifyingMarks", f, ::update, t("fields.identifyingMarksPlaceholder"),
                    multiline = true, hint = t("fields.identifyingMarksHint"),
                )
            }

            Section("medical", t("sections.medical"), "cross.fill", expanded, toggle) {
                Field(t("fields.medicalConditions"), "medicalConditions", f, ::update, t("fields.medicalConditionsPlaceholder"), multiline = true)
                Field(t("fields.medications"), "medications", f, ::update, t("fields.medicationsPlaceholder"), multiline = true)
                Field(t("fields.allergies"), "allergies", f, ::update, t("fields.allergiesPlaceholder"), multiline = true)
                Field(
                    t("fields.cognitiveStatus"), "cognitiveStatus", f, ::update, t("fields.cognitiveStatusPlaceholder"),
                    multiline = true, hint = t("fields.cognitiveStatusHint"),
                )
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
                Field(t("fields.communicationPreference"), "communicationPreference", f, ::update, t("fields.communicationPreferencePlaceholder"), multiline = true)
                Field(t("fields.escalationSigns"), "escalationSigns", f, ::update, t("fields.escalationSignsPlaceholder"), multiline = true, hint = t("fields.escalationSignsHint"))
                Field(t("fields.deescalationTechniques"), "deescalationTechniques", f, ::update, t("fields.deescalationTechniquesPlaceholder"), multiline = true, hint = t("fields.deescalationTechniquesHint"))
                Field(t("fields.approachGuidance"), "approachGuidance", f, ::update, t("fields.approachGuidancePlaceholder"), multiline = true)
                Field(t("fields.likes"), "likes", f, ::update, t("fields.likesPlaceholder"), multiline = true, hint = t("fields.likesHint"))
                Field(t("fields.dislikesTriggers"), "dislikesTriggers", f, ::update, t("fields.dislikesTriggersPlaceholder"), multiline = true, hint = t("fields.dislikesTriggersHint"))
                Field(t("fields.safeWord"), "safeWord", f, ::update, t("fields.safeWordPlaceholder"), hint = t("fields.safeWordHint"))
            }

            Section("devices", t("sections.devices"), "location.fill", expanded, toggle) {
                Field(t("fields.locativeDeviceInfo"), "locativeDeviceInfo", f, ::update, t("fields.locativeDeviceInfoPlaceholder"), multiline = true, hint = t("fields.locativeDeviceInfoHint"))
                Field(t("fields.idBracelets"), "idBracelets", f, ::update, t("fields.idBraceletsPlaceholder"), multiline = true, hint = t("fields.idBraceletsHint"))
                Field(t("fields.medicAlertId"), "medicAlertId", f, ::update, t("fields.medicAlertIdPlaceholder"))
                Field(
                    t("fields.medicAlertHotline"), "medicAlertHotline", f, ::update, t("fields.medicAlertHotlinePlaceholder"),
                    keyboardType = KeyboardType.Phone, hint = t("fields.medicAlertHotlineHint"),
                )
            }
            Box(Modifier.height(40.udp))
        }
    }
}

/** What each field's input does to typed text (profile.tsx formatFieldInput). */
private fun formatField(field: String, value: String) = when (field) {
    "height" -> formatHeightInput(value)
    "weight" -> formatWeightInput(value)
    "medicAlertId" -> formatMedicAlertIdInput(value)
    "medicAlertHotline" -> formatPhoneInput(value)
    else -> value
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
private fun Field(
    label: String, field: String, form: ProfileForm, onChange: (String, String) -> Unit, placeholder: String,
    multiline: Boolean = false, hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = LocalAppColors.current
    Group(label, hint) {
        RnTextField(
            form[field], { onChange(field, formatField(field, it)) }, "profile-field-$field",
            placeholder = placeholder, multiline = multiline, keyboardType = keyboardType, capitalization = capitalization,
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
                value, { onChange(formatDobInput(it)) }, "profile-field-dateOfBirth",
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
