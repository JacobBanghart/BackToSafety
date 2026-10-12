package com.backtosafety.core

import com.backtosafety.core.db.ProfileEntity

/** The keyboard a profile text field asks for. */
enum class ProfileKeyboard { TEXT, NUMBER, PHONE }

/**
 * The profile's fields, defined once: [Profile], the `profile` row and both apps' screens read and
 * write them through this list. [key] is the [Profile] property's name, which is also the RN form
 * key (testID `profile-field-<key>`) and the key of the field's strings (`fields.<key>`,
 * `fields.<key>Placeholder`, `fields.<key>Hint`). Adding a field is an entry here plus the [Profile]
 * and [ProfileEntity] properties; the `when`s in [Profile.get] and [ProfileEntity.with] won't compile
 * without it, and ProfileFieldTest fails for a column with no entry.
 *
 * The rest describes how the screens input a field. The date of birth has its own input (a date
 * picker beside the text), and [DOMINANT_HAND] and [MOBILITY_LEVEL] are chosen, not typed
 * ([PROFILE_TEXT_FIELDS] is the rest).
 *
 * The person's name is [FULL_NAME], not NAME: Swift sees the entries as class properties, and
 * `ProfileField.name` would collide with the enum's own `name`.
 */
enum class ProfileField(
    val key: String,
    /** The input is a text area rather than a single line. */
    val multiline: Boolean = false,
    /** The screens show the `fields.<key>Hint` line under the label. */
    val hasHint: Boolean = false,
    val keyboard: ProfileKeyboard = ProfileKeyboard.TEXT,
    /** Typed text starts each word with a capital (names); otherwise each sentence. */
    val capitalizeWords: Boolean = false,
) {
    FULL_NAME("name", capitalizeWords = true),
    NICKNAME("nickname", capitalizeWords = true),
    DATE_OF_BIRTH("dateOfBirth", keyboard = ProfileKeyboard.NUMBER),
    PHOTO_URI("photoUri"),
    HEIGHT("height", keyboard = ProfileKeyboard.NUMBER),
    WEIGHT("weight", keyboard = ProfileKeyboard.NUMBER),
    HAIR_COLOR("hairColor"),
    EYE_COLOR("eyeColor"),
    IDENTIFYING_MARKS("identifyingMarks", multiline = true, hasHint = true),
    MEDICAL_CONDITIONS("medicalConditions", multiline = true),
    MEDICATIONS("medications", multiline = true),
    ALLERGIES("allergies", multiline = true),
    COGNITIVE_STATUS("cognitiveStatus", multiline = true, hasHint = true),
    DOMINANT_HAND("dominantHand"),
    MOBILITY_LEVEL("mobilityLevel"),
    COMMUNICATION_PREFERENCE("communicationPreference", multiline = true),
    ESCALATION_SIGNS("escalationSigns", multiline = true, hasHint = true),
    DEESCALATION_TECHNIQUES("deescalationTechniques", multiline = true, hasHint = true),
    APPROACH_GUIDANCE("approachGuidance", multiline = true),
    LIKES("likes", multiline = true, hasHint = true),
    DISLIKES_TRIGGERS("dislikesTriggers", multiline = true, hasHint = true),
    SAFE_WORD("safeWord", hasHint = true),
    LOCATIVE_DEVICE_INFO("locativeDeviceInfo", multiline = true, hasHint = true),
    ID_BRACELETS("idBracelets", multiline = true, hasHint = true),
    MEDIC_ALERT_ID("medicAlertId"),
    MEDIC_ALERT_HOTLINE("medicAlertHotline", hasHint = true, keyboard = ProfileKeyboard.PHONE),
    ;

    /** What the field's input does to typed text (profile.tsx formatFieldInput). */
    fun formatInput(value: String): String = when (this) {
        DATE_OF_BIRTH -> formatDobInput(value)
        HEIGHT -> formatHeightInput(value)
        WEIGHT -> formatWeightInput(value)
        MEDIC_ALERT_ID -> formatMedicAlertIdInput(value)
        MEDIC_ALERT_HOTLINE -> formatPhoneInput(value)
        else -> value
    }
}

/** The fields typed into a text input: all but the two that are chosen from options. */
val PROFILE_TEXT_FIELDS: List<ProfileField> =
    ProfileField.entries - listOf(ProfileField.DOMINANT_HAND, ProfileField.MOBILITY_LEVEL)

/** The row with [field] set to [value]; the name is NOT NULL, so a null name becomes empty. */
internal fun ProfileEntity.with(field: ProfileField, value: String?): ProfileEntity = when (field) {
    ProfileField.FULL_NAME -> copy(name = value ?: "")
    ProfileField.NICKNAME -> copy(nickname = value)
    ProfileField.DATE_OF_BIRTH -> copy(dateOfBirth = value)
    ProfileField.PHOTO_URI -> copy(photoUri = value)
    ProfileField.HEIGHT -> copy(height = value)
    ProfileField.WEIGHT -> copy(weight = value)
    ProfileField.HAIR_COLOR -> copy(hairColor = value)
    ProfileField.EYE_COLOR -> copy(eyeColor = value)
    ProfileField.IDENTIFYING_MARKS -> copy(identifyingMarks = value)
    ProfileField.MEDICAL_CONDITIONS -> copy(medicalConditions = value)
    ProfileField.MEDICATIONS -> copy(medications = value)
    ProfileField.ALLERGIES -> copy(allergies = value)
    ProfileField.COGNITIVE_STATUS -> copy(cognitiveStatus = value)
    ProfileField.DOMINANT_HAND -> copy(dominantHand = value)
    ProfileField.MOBILITY_LEVEL -> copy(mobilityLevel = value)
    ProfileField.COMMUNICATION_PREFERENCE -> copy(communicationPreference = value)
    ProfileField.ESCALATION_SIGNS -> copy(escalationSigns = value)
    ProfileField.DEESCALATION_TECHNIQUES -> copy(deescalationTechniques = value)
    ProfileField.APPROACH_GUIDANCE -> copy(approachGuidance = value)
    ProfileField.LIKES -> copy(likes = value)
    ProfileField.DISLIKES_TRIGGERS -> copy(dislikesTriggers = value)
    ProfileField.SAFE_WORD -> copy(safeWord = value)
    ProfileField.LOCATIVE_DEVICE_INFO -> copy(locativeDeviceInfo = value)
    ProfileField.ID_BRACELETS -> copy(idBracelets = value)
    ProfileField.MEDIC_ALERT_ID -> copy(medicAlertId = value)
    ProfileField.MEDIC_ALERT_HOTLINE -> copy(medicAlertHotline = value)
}
