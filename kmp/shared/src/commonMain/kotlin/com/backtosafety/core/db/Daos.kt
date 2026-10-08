package com.backtosafety.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun save(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY sort_order, created_at")
    suspend fun all(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE notify_on_emergency = 1 ORDER BY sort_order, created_at")
    suspend fun emergency(): List<ContactEntity>

    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations ORDER BY sort_order, created_at")
    suspend fun all(): List<DestinationEntity>

    @Insert
    suspend fun insert(destination: DestinationEntity): Long

    @Query("DELETE FROM destinations")
    suspend fun deleteAll()
}

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY started_at DESC")
    suspend fun all(): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun get(id: Long): IncidentEntity?

    @Insert
    suspend fun insert(incident: IncidentEntity): Long

    @Upsert
    suspend fun save(incident: IncidentEntity)

    @Query("DELETE FROM incidents")
    suspend fun deleteAll()
}

@Dao
interface SafetyCheckDao {
    @Query("SELECT * FROM safety_checks ORDER BY id")
    suspend fun all(): List<SafetyCheckEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(checks: List<SafetyCheckEntity>)

    @Query("DELETE FROM safety_checks")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}

@Dao
interface OnboardingDao {
    @Query("SELECT * FROM onboarding")
    suspend fun all(): List<OnboardingStepEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(steps: List<OnboardingStepEntity>)

    /** Skipping a step marks it completed too, as the RN screens do. */
    @Query("UPDATE onboarding SET completed = 1 WHERE step = :step")
    suspend fun complete(step: String)

    /** Onboarded once no step is left neither completed nor skipped (spec/storage.md). */
    @Query("SELECT COUNT(*) = 0 FROM onboarding WHERE completed = 0 AND skipped = 0")
    suspend fun isComplete(): Boolean

    @Query("DELETE FROM onboarding")
    suspend fun deleteAll()
}
