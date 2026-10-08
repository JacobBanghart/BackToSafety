package com.backtosafety.core.data

import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.Profile
import com.backtosafety.core.db.IncidentEntity
import com.backtosafety.core.parseActiveEmergency
import com.backtosafety.core.serializeActiveEmergency
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import com.backtosafety.core.db.AppDatabase
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.db.DestinationEntity
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
        val now = sqliteNow()
        val current = db.profile().get() ?: ProfileEntity(name = "", createdAt = now)
        // updated_at as the RN update sets it; created_at kept (Room would otherwise write NULL).
        db.profile().save(change(current).copy(createdAt = current.createdAt ?: now, updatedAt = now))
    }

    /**
     * Adds a contact at [sortOrder], or after the last one (database/contacts.native.ts).
     * Room would write created_at as NULL over the column default, so it's stamped here.
     */
    suspend fun addContact(contact: ContactEntity, sortOrder: Int? = null): Long {
        val now = sqliteNow()
        return db.contacts().insert(
            contact.copy(
                sortOrder = sortOrder ?: ((db.contacts().maxSortOrder() ?: -1) + 1),
                createdAt = contact.createdAt ?: now, updatedAt = contact.updatedAt ?: now,
            ),
        )
    }

    /** Saves the edit form: every field, so a cleared one is cleared (F-35). */
    suspend fun updateContact(c: ContactEntity) = db.contacts().update(
        c.id, c.name, c.phone, c.relationship, c.role, c.address, c.notifyOnEmergency, c.shareMedicalInfo, c.notes,
    )

    suspend fun deleteContact(id: Long) = db.contacts().delete(id)

    /** After a drag: each contact's sort order becomes its index, written only where it changed. */
    suspend fun reorderContacts(ordered: List<ContactEntity>) = ordered.forEachIndexed { index, c ->
        if (c.sortOrder != index) db.contacts().setSortOrder(c.id, index)
    }

    suspend fun addDestination(d: DestinationEntity, sortOrder: Int? = null): Long {
        val now = sqliteNow()
        return db.destinations().insert(
            d.copy(
                sortOrder = sortOrder ?: ((db.destinations().maxSortOrder() ?: -1) + 1),
                createdAt = d.createdAt ?: now, updatedAt = d.updatedAt ?: now,
            ),
        )
    }

    suspend fun updateDestination(d: DestinationEntity) = db.destinations().update(
        d.id, d.name, d.address, d.category, d.reason, d.distanceFromHome, d.riskLevel, d.notes,
    )

    suspend fun deleteDestination(id: Long) = db.destinations().delete(id)

    suspend fun reorderDestinations(ordered: List<DestinationEntity>) = ordered.forEachIndexed { index, d ->
        if (d.sortOrder != index) db.destinations().setSortOrder(d.id, index)
    }

    suspend fun contacts() = db.contacts().all()

    /** Contacts the emergency alert texts (notify_on_emergency = 1). */
    suspend fun emergencyContacts() = db.contacts().emergency()

    suspend fun destinations() = db.destinations().all()

    suspend fun activeEmergency(): ActiveEmergency? = parseActiveEmergency(setting(ACTIVE_EMERGENCY))

    suspend fun saveActiveEmergency(e: ActiveEmergency) = putSetting(ACTIVE_EMERGENCY, serializeActiveEmergency(e))

    /** Ends the emergency: stored as an empty string, not deleted (spec/storage.md). */
    suspend fun clearActiveEmergency() = putSetting(ACTIVE_EMERGENCY, "")

    suspend fun incidents() = db.incidents().all()

    suspend fun createIncident(startedAt: String): Long =
        db.incidents().insert(IncidentEntity(startedAt = startedAt, outcome = "ongoing", createdAt = sqliteNow()))

    /**
     * Records what happened (spec/storage.md, incidents): [outcome] and [endedAt] when given,
     * and always the checked steps and clothing. A missing row is created first, for an
     * emergency started before incidents were recorded.
     */
    suspend fun recordIncident(
        incidentId: Long?,
        startedAt: String,
        checkedSteps: List<String>,
        wearing: String,
        outcome: String? = null,
        endedAt: String? = null,
    ): Long {
        val id = incidentId ?: createIncident(startedAt)
        val current = db.incidents().get(id) ?: IncidentEntity(id = id, startedAt = startedAt)
        db.incidents().save(
            current.copy(
                outcome = outcome ?: current.outcome,
                endedAt = endedAt ?: current.endedAt,
                areasChecked = JsonArray(checkedSteps.map(::JsonPrimitive)).toString(),
                wearing = wearing.ifEmpty { null },
            ),
        )
        return id
    }

    /**
     * Settings > Delete Account (database/storage.native.ts clearAllData): empties every
     * table, then re-creates the onboarding steps so the app starts onboarding again.
     */
    suspend fun clearAllData() {
        db.profile().deleteAll()
        db.contacts().deleteAll()
        db.destinations().deleteAll()
        db.incidents().deleteAll()
        db.safetyChecks().deleteAll()
        db.settings().deleteAll()
        db.onboarding().deleteAll()
        db.onboarding().insertMissing(ONBOARDING_STEPS.map { OnboardingStepEntity(step = it) })
    }

    private var cachedDeviceId: String? = null

    /** The anonymous per-install ID (utils/device-id.ts): a random UUID, stored on first use. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun deviceId(): String = cachedDeviceId ?: (
        setting(DEVICE_ID)?.takeIf { it.isNotEmpty() }
            ?: Uuid.random().toString().also { putSetting(DEVICE_ID, it) }
        ).also { cachedDeviceId = it }

    suspend fun setting(key: String): String? = db.settings().get(key)

    suspend fun putSetting(key: String, value: String) =
        db.settings().put(com.backtosafety.core.db.SettingEntity(key = key, value = value))

    companion object {
        const val ACTIVE_EMERGENCY = "active_emergency"
        const val DEVICE_ID = "device_id"
        const val THEME_PREFERENCE = "theme_preference"
        const val LANGUAGE_PREFERENCE = "language_preference"

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

/** SQLite's CURRENT_TIMESTAMP: UTC, "YYYY-MM-DD HH:MM:SS". */
internal fun sqliteNow(): String =
    kotlin.time.Clock.System.now().toString().substring(0, 19).replace('T', ' ')
