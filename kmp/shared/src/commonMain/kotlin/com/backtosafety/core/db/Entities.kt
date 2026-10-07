package com.backtosafety.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The RN app's tables (spec/db-schema.json), in the shape Room manages: schema version 2.
// Column names are unchanged, so MIGRATION_1_2 copies RN rows straight across. Enum
// columns (role, category, risk_level, outcome, dominant_hand) are plain TEXT here; the
// RN schema's CHECK constraints are dropped because Room can't declare them.

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val nickname: String? = null,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
    val height: String? = null,
    val weight: String? = null,
    @ColumnInfo(name = "hair_color") val hairColor: String? = null,
    @ColumnInfo(name = "eye_color") val eyeColor: String? = null,
    @ColumnInfo(name = "identifying_marks") val identifyingMarks: String? = null,
    @ColumnInfo(name = "medical_conditions") val medicalConditions: String? = null,
    val medications: String? = null,
    val allergies: String? = null,
    @ColumnInfo(name = "cognitive_status") val cognitiveStatus: String? = null,
    @ColumnInfo(name = "dominant_hand") val dominantHand: String? = null,
    @ColumnInfo(name = "mobility_level") val mobilityLevel: String? = null,
    @ColumnInfo(name = "communication_preference") val communicationPreference: String? = null,
    @ColumnInfo(name = "escalation_signs") val escalationSigns: String? = null,
    @ColumnInfo(name = "deescalation_techniques") val deescalationTechniques: String? = null,
    @ColumnInfo(name = "approach_guidance") val approachGuidance: String? = null,
    val likes: String? = null,
    @ColumnInfo(name = "dislikes_triggers") val dislikesTriggers: String? = null,
    @ColumnInfo(name = "safe_word") val safeWord: String? = null,
    @ColumnInfo(name = "locative_device_info") val locativeDeviceInfo: String? = null,
    @ColumnInfo(name = "id_bracelets") val idBracelets: String? = null,
    @ColumnInfo(name = "medic_alert_id") val medicAlertId: String? = null,
    @ColumnInfo(name = "medic_alert_hotline") val medicAlertHotline: String? = null,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP") val updatedAt: String? = null,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val relationship: String? = null,
    val role: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "notify_on_emergency", defaultValue = "1") val notifyOnEmergency: Boolean = true,
    @ColumnInfo(name = "share_medical_info", defaultValue = "0") val shareMedicalInfo: Boolean = false,
    val notes: String? = null,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP") val updatedAt: String? = null,
)

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null,
    val reason: String? = null,
    @ColumnInfo(name = "distance_from_home") val distanceFromHome: String? = null,
    @ColumnInfo(name = "risk_level") val riskLevel: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP") val updatedAt: String? = null,
)

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "ended_at") val endedAt: String? = null,
    val outcome: String? = null,
    @ColumnInfo(name = "last_seen_lat") val lastSeenLat: Double? = null,
    @ColumnInfo(name = "last_seen_lon") val lastSeenLon: Double? = null,
    @ColumnInfo(name = "last_seen_accuracy") val lastSeenAccuracy: Double? = null,
    @ColumnInfo(name = "found_lat") val foundLat: Double? = null,
    @ColumnInfo(name = "found_lon") val foundLon: Double? = null,
    @ColumnInfo(name = "found_location_name") val foundLocationName: String? = null,
    val weather: String? = null,
    @ColumnInfo(name = "time_of_day") val timeOfDay: String? = null,
    @ColumnInfo(name = "trigger_identified") val triggerIdentified: String? = null,
    val wearing: String? = null,
    /** JSON array of checked step IDs. */
    @ColumnInfo(name = "areas_checked") val areasChecked: String? = null,
    @ColumnInfo(name = "people_contacted") val peopleContacted: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null,
)

@Entity(tableName = "safety_checks", indices = [Index(value = ["item_key"], unique = true)])
data class SafetyCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String? = null,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: String? = null,
    val notes: String? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP") val updatedAt: String? = null,
)

@Entity(tableName = "onboarding")
data class OnboardingStepEntity(
    @PrimaryKey val step: String,
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: String? = null,
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
)
