package com.backtosafety.core

/** The person being cared for: the `profile` row (spec/storage.md). Blank fields are null. */
data class Profile(
    val name: String,
    val nickname: String? = null,
    val dateOfBirth: String? = null,
    val photoUri: String? = null,
    val height: String? = null,
    val weight: String? = null,
    val hairColor: String? = null,
    val eyeColor: String? = null,
    val identifyingMarks: String? = null,
    val medicalConditions: String? = null,
    val medications: String? = null,
    val allergies: String? = null,
    val cognitiveStatus: String? = null,
    val dominantHand: String? = null,
    val mobilityLevel: String? = null,
    val communicationPreference: String? = null,
    val escalationSigns: String? = null,
    val deescalationTechniques: String? = null,
    val approachGuidance: String? = null,
    val likes: String? = null,
    val dislikesTriggers: String? = null,
    val safeWord: String? = null,
    val locativeDeviceInfo: String? = null,
    val idBracelets: String? = null,
    val medicAlertId: String? = null,
    val medicAlertHotline: String? = null,
) {
    /** The value of [field], null when blank. */
    operator fun get(field: ProfileField): String? = when (field) {
        ProfileField.FULL_NAME -> name
        ProfileField.NICKNAME -> nickname
        ProfileField.DATE_OF_BIRTH -> dateOfBirth
        ProfileField.PHOTO_URI -> photoUri
        ProfileField.HEIGHT -> height
        ProfileField.WEIGHT -> weight
        ProfileField.HAIR_COLOR -> hairColor
        ProfileField.EYE_COLOR -> eyeColor
        ProfileField.IDENTIFYING_MARKS -> identifyingMarks
        ProfileField.MEDICAL_CONDITIONS -> medicalConditions
        ProfileField.MEDICATIONS -> medications
        ProfileField.ALLERGIES -> allergies
        ProfileField.COGNITIVE_STATUS -> cognitiveStatus
        ProfileField.DOMINANT_HAND -> dominantHand
        ProfileField.MOBILITY_LEVEL -> mobilityLevel
        ProfileField.COMMUNICATION_PREFERENCE -> communicationPreference
        ProfileField.ESCALATION_SIGNS -> escalationSigns
        ProfileField.DEESCALATION_TECHNIQUES -> deescalationTechniques
        ProfileField.APPROACH_GUIDANCE -> approachGuidance
        ProfileField.LIKES -> likes
        ProfileField.DISLIKES_TRIGGERS -> dislikesTriggers
        ProfileField.SAFE_WORD -> safeWord
        ProfileField.LOCATIVE_DEVICE_INFO -> locativeDeviceInfo
        ProfileField.ID_BRACELETS -> idBracelets
        ProfileField.MEDIC_ALERT_ID -> medicAlertId
        ProfileField.MEDIC_ALERT_HOTLINE -> medicAlertHotline
    }
}

/** A field counts as set when it isn't null or empty (the RN app's truthiness). */
internal fun String?.isSet(): Boolean = !this.isNullOrEmpty()
