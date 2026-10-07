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
)

/** A field counts as set when it isn't null or empty (the RN app's truthiness). */
internal fun String?.isSet(): Boolean = !this.isNullOrEmpty()
