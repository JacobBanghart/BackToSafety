package com.backtosafety.core.data

import com.backtosafety.core.Profile
import com.backtosafety.core.db.AppDatabase
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.db.OnboardingStepEntity
import com.backtosafety.core.db.ProfileEntity
import com.backtosafety.core.db.SafetyCheckEntity

/**
 * The app's data, with the RN app's semantics (spec/storage.md): what's seeded on every
 * launch, how a partial profile save merges, and what counts as onboarded.
 */
class Store(private val db: AppDatabase) {

    /** Rows the RN app inserts on every launch (spec/seed.json), if missing. */
    suspend fun seed() {
        db.onboarding().insertMissing(ONBOARDING_STEPS.map { OnboardingStepEntity(step = it) })
        db.safetyChecks().insertMissing(SAFETY_CHECKS.map { (category, key) -> SafetyCheckEntity(category = category, itemKey = key) })
    }

    suspend fun isOnboarded(): Boolean = db.onboarding().isComplete()

    suspend fun completeStep(step: String) = db.onboarding().complete(step)

    suspend fun profile(): Profile? = db.profile().get()?.toProfile()

    /**
     * Saves a profile change. Like the RN app, the first save creates the single profile
     * row and later saves update it.
     */
    suspend fun saveProfile(change: (ProfileEntity) -> ProfileEntity) {
        val current = db.profile().get() ?: ProfileEntity(name = "")
        db.profile().save(change(current))
    }

    suspend fun addContact(contact: ContactEntity): Long = db.contacts().insert(contact)

    suspend fun setting(key: String): String? = db.settings().get(key)

    suspend fun putSetting(key: String, value: String) =
        db.settings().put(com.backtosafety.core.db.SettingEntity(key = key, value = value))

    companion object {
        val ONBOARDING_STEPS = listOf(
            "welcome", "profile_name", "profile_photo", "profile_appearance", "emergency_contact", "complete",
        )
        val SAFETY_CHECKS = listOf(
            "at_home" to "visual_supports_doors",
            "at_home" to "locks_high_location",
            "at_home" to "door_chimes",
            "at_home" to "geofence_setup",
            "at_home" to "physical_boundaries",
            "away_from_home" to "alert_caregivers_staff",
            "away_from_home" to "safety_plan_locations",
            "away_from_home" to "introduce_first_responders",
            "away_from_home" to "evaluate_locative_tech",
            "foundation" to "social_stories",
            "foundation" to "water_safety_classes",
            "foundation" to "safety_responsibility",
        )
    }
}

internal fun ProfileEntity.toProfile() = Profile(
    name = name, nickname = nickname, dateOfBirth = dateOfBirth, photoUri = photoUri, height = height,
    weight = weight, hairColor = hairColor, eyeColor = eyeColor, identifyingMarks = identifyingMarks,
    medicalConditions = medicalConditions, medications = medications, allergies = allergies,
    cognitiveStatus = cognitiveStatus, dominantHand = dominantHand, mobilityLevel = mobilityLevel,
    communicationPreference = communicationPreference, escalationSigns = escalationSigns,
    deescalationTechniques = deescalationTechniques, approachGuidance = approachGuidance, likes = likes,
    dislikesTriggers = dislikesTriggers, safeWord = safeWord, locativeDeviceInfo = locativeDeviceInfo,
    idBracelets = idBracelets, medicAlertId = medicAlertId, medicAlertHotline = medicAlertHotline,
)
