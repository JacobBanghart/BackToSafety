package com.backtosafety.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.backtosafety.core.ActiveEmergency
import com.backtosafety.core.Profile
import com.backtosafety.core.ProfileField
import com.backtosafety.core.db.AppDatabase
import com.backtosafety.core.db.ContactEntity
import com.backtosafety.core.db.DestinationEntity
import com.backtosafety.core.db.IncidentEntity
import com.backtosafety.core.db.OnboardingStepEntity
import com.backtosafety.core.db.ProfileEntity
import com.backtosafety.core.db.SafetyCheckEntity
import com.backtosafety.core.parseActiveEmergency
import com.backtosafety.core.serializeActiveEmergency
import com.backtosafety.core.with
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The app's data, with the RN app's semantics (spec/storage.md): what's seeded on every
 * launch, how a partial profile save merges, and what counts as onboarded.
 *
 * Every public suspend function is @Throws, so a failure reaches Swift as an error it can catch
 * instead of ending the app (ThrowsGuardTest). Writes of several steps run in one transaction
 * ([inTransaction]): they all happen or none does, and two of them never interleave.
 */
class Store(private val db: AppDatabase) {

    /**
     * Removes the photo files the profile no longer points at: every saved profile photo except
     * the one at the URI given (none, if null), and the camera and cropper temp files. The
     * platform supplies it (the files are its own); it runs after the write that made them
     * stale, so a failed write never costs the photo the profile still shows, and a failure of
     * its own never fails the write.
     */
    var photoCleaner: (keeping: String?) -> Unit = {}

    private fun cleanPhotos(keeping: String?) {
        runCatching { photoCleaner(keeping) }
    }

    /**
     * Runs [block] in one immediate transaction on the writer connection: the DAO calls inside
     * share it, a failure rolls them all back, and the write lock taken at the start makes a
     * read-modify-write inside it exclusive.
     */
    private suspend fun <R> inTransaction(block: suspend () -> R): R =
        db.useWriterConnection { it.immediateTransaction { block() } }

    /** Rows the RN app inserts on every launch (spec/seed.json), if missing. */
    @Throws(Exception::class)
    suspend fun seed() {
        db.onboarding().insertMissing(ONBOARDING_STEPS.map { OnboardingStepEntity(step = it) })
        db.safetyChecks().insertMissing(SAFETY_CHECKS.map { (category, key) -> SafetyCheckEntity(category = category, itemKey = key) })
    }

    @Throws(Exception::class)
    suspend fun isOnboarded(): Boolean = db.onboarding().isComplete()

    @Throws(Exception::class)
    suspend fun completeStep(step: String) = db.onboarding().complete(step)

    @Throws(Exception::class)
    suspend fun profile(): Profile? = db.profile().get()?.toProfile()

    /**
     * Saves a profile change. Like the RN app, the first save creates the single profile
     * row and later saves update it.
     */
    @Throws(Exception::class)
    suspend fun saveProfile(change: (ProfileEntity) -> ProfileEntity) {
        val now = sqliteNow()
        val (before, after) = inTransaction {
            val current = db.profile().get() ?: ProfileEntity(name = "", createdAt = now)
            // updated_at as the RN update sets it; created_at kept (Room would otherwise write NULL).
            val saved = change(current).copy(createdAt = current.createdAt ?: now, updatedAt = now)
            db.profile().save(saved)
            current.photoUri to saved.photoUri
        }
        // The photo changed: the previous file (and any pick that was never saved) is unused now.
        if (before != after) cleanPhotos(after)
    }

    /**
     * A partial profile save, as the RN screens save: given fields are set (null clears), the
     * rest kept. Both apps save this way.
     */
    @Throws(Exception::class)
    suspend fun updateProfile(values: Map<ProfileField, String?>) = saveProfile { profile ->
        values.entries.fold(profile) { p, (field, value) -> p.with(field, value) }
    }

    /**
     * Adds a contact at [sortOrder], or after the last one (database/contacts.native.ts).
     * Room would write created_at as NULL over the column default, so it's stamped here.
     */
    @Throws(Exception::class)
    suspend fun addContact(contact: ContactEntity, sortOrder: Int? = null): Long {
        val now = sqliteNow()
        return inTransaction {
            db.contacts().insert(
                contact.copy(
                    sortOrder = sortOrder ?: ((db.contacts().maxSortOrder() ?: -1) + 1),
                    createdAt = contact.createdAt ?: now, updatedAt = contact.updatedAt ?: now,
                ),
            )
        }
    }

    /** Saves the edit form: every field, so a cleared one is cleared (F-35). */
    @Throws(Exception::class)
    suspend fun updateContact(c: ContactEntity) = db.contacts().update(
        c.id, c.name, c.phone, c.relationship, c.role, c.address, c.notifyOnEmergency, c.shareMedicalInfo, c.notes,
    )

    @Throws(Exception::class)
    suspend fun deleteContact(id: Long) = db.contacts().delete(id)

    /** After a drag: each contact's sort order becomes its index, written only where it changed. */
    @Throws(Exception::class)
    suspend fun reorderContacts(ordered: List<ContactEntity>) = inTransaction {
        ordered.forEachIndexed { index, c ->
            if (c.sortOrder != index) db.contacts().setSortOrder(c.id, index)
        }
    }

    @Throws(Exception::class)
    suspend fun addDestination(d: DestinationEntity, sortOrder: Int? = null): Long {
        val now = sqliteNow()
        return inTransaction {
            db.destinations().insert(
                d.copy(
                    sortOrder = sortOrder ?: ((db.destinations().maxSortOrder() ?: -1) + 1),
                    createdAt = d.createdAt ?: now, updatedAt = d.updatedAt ?: now,
                ),
            )
        }
    }

    @Throws(Exception::class)
    suspend fun updateDestination(d: DestinationEntity) = db.destinations().update(
        d.id, d.name, d.address, d.category, d.reason, d.distanceFromHome, d.riskLevel, d.notes,
    )

    @Throws(Exception::class)
    suspend fun deleteDestination(id: Long) = db.destinations().delete(id)

    @Throws(Exception::class)
    suspend fun reorderDestinations(ordered: List<DestinationEntity>) = inTransaction {
        ordered.forEachIndexed { index, d ->
            if (d.sortOrder != index) db.destinations().setSortOrder(d.id, index)
        }
    }

    @Throws(Exception::class)
    suspend fun contacts() = db.contacts().all()

    /** Contacts the emergency alert texts (notify_on_emergency = 1). */
    @Throws(Exception::class)
    suspend fun emergencyContacts() = db.contacts().emergency()

    @Throws(Exception::class)
    suspend fun destinations() = db.destinations().all()

    @Throws(Exception::class)
    suspend fun activeEmergency(): ActiveEmergency? = parseActiveEmergency(setting(ACTIVE_EMERGENCY))

    @Throws(Exception::class)
    suspend fun saveActiveEmergency(e: ActiveEmergency) = putSetting(ACTIVE_EMERGENCY, serializeActiveEmergency(e))

    /** Ends the emergency: stored as an empty string, not deleted (spec/storage.md). */
    @Throws(Exception::class)
    suspend fun clearActiveEmergency() = inTransaction {
        putSetting(ACTIVE_EMERGENCY, "")
        putSetting(EMERGENCY_AWAY_FROM, "")
    }

    @Throws(Exception::class)
    suspend fun incidents() = db.incidents().all()

    @Throws(Exception::class)
    suspend fun createIncident(startedAt: String): Long =
        db.incidents().insert(IncidentEntity(startedAt = startedAt, outcome = "ongoing", createdAt = sqliteNow()))

    /**
     * Records what happened (spec/storage.md, incidents): [outcome] and [endedAt] when given,
     * and always the checked steps and clothing. A missing row is created first, for an
     * emergency started before incidents were recorded.
     */
    @Throws(Exception::class)
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
     * table, then re-creates the onboarding steps so the app starts onboarding again. All of it
     * or none: half-way, the profile could be gone while onboarding still counted as complete.
     * Then the photo files go ([photoCleaner]) and the cached device ID is dropped.
     */
    @Throws(Exception::class)
    suspend fun clearAllData() {
        inTransaction {
            db.profile().deleteAll()
            db.contacts().deleteAll()
            db.destinations().deleteAll()
            db.incidents().deleteAll()
            db.safetyChecks().deleteAll()
            db.settings().deleteAll()
            db.onboarding().deleteAll()
            db.onboarding().insertMissing(ONBOARDING_STEPS.map { OnboardingStepEntity(step = it) })
        }
        // The device ID row is gone with the settings: forget the cached copy too, or this
        // process would keep handing out the deleted identity.
        cachedDeviceId = null
        // And the person's photos, which are files outside the database.
        cleanPhotos(null)
    }

    private var cachedDeviceId: String? = null

    /**
     * data_migrated's properties, after the first query migrated the database from
     * [fromVersion] in [durationMs]: what came through, so a migration that lost data shows.
     */
    @Throws(Exception::class)
    suspend fun dataMigratedProperties(fromVersion: Int, durationMs: Long): Map<String, Any?> {
        val contacts = contacts()
        return mapOf(
            "from_version" to fromVersion,
            "to_version" to com.backtosafety.core.db.DATABASE_VERSION,
            "ok" to true,
            "duration_ms" to durationMs,
            "has_profile" to (profile() != null),
            "contacts_count" to contacts.size,
            "places_count" to destinations().size,
            "incidents_count" to incidents().size,
        )
    }

    /** app_ready's readiness properties (readinessProperties) from what's stored. */
    @Throws(Exception::class)
    suspend fun readinessProperties(): Map<String, Any?> {
        val contacts = contacts()
        return com.backtosafety.core.readinessProperties(
            profile(), contacts.size, contacts.count { it.notifyOnEmergency }, destinations().size,
        )
    }

    /** The anonymous per-install ID (utils/device-id.ts): a random UUID, stored on first use. */
    @OptIn(ExperimentalUuidApi::class)
    @Throws(Exception::class)
    suspend fun deviceId(): String = cachedDeviceId ?: (
        setting(DEVICE_ID)?.takeIf { it.isNotEmpty() }
            ?: Uuid.random().toString().also { putSetting(DEVICE_ID, it) }
        ).also { cachedDeviceId = it }

    @Throws(Exception::class)
    suspend fun setting(key: String): String? = db.settings().get(key)

    @Throws(Exception::class)
    suspend fun putSetting(key: String, value: String) =
        db.settings().put(com.backtosafety.core.db.SettingEntity(key = key, value = value))

    companion object {
        const val ACTIVE_EMERGENCY = "active_emergency"

        /** When the time away from the emergency screen began (epoch ms), or empty (EmergencyAway). */
        const val EMERGENCY_AWAY_FROM = "emergency_away_from"

        /** Set once the notification permission prompt has been shown. */
        const val NOTIFICATIONS_PROMPTED = "notifications_prompted"
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
