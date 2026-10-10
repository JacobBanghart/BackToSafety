package com.backtosafety.core.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * nijii.db, the same file the RN app wrote (spec/storage.md). Version 1 is the RN
 * schema; version 2 is the same tables in Room's shape (MIGRATION_1_2).
 */
@Database(
    entities = [
        ProfileEntity::class, ContactEntity::class, DestinationEntity::class, IncidentEntity::class,
        SafetyCheckEntity::class, SettingEntity::class, OnboardingStepEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun contacts(): ContactDao
    abstract fun destinations(): DestinationDao
    abstract fun incidents(): IncidentDao
    abstract fun settings(): SettingDao
    abstract fun onboarding(): OnboardingDao
    abstract fun safetyChecks(): SafetyCheckDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

const val DATABASE_NAME = "nijii.db"
const val DATABASE_VERSION = 2

/**
 * What the last [openAppDatabase] found on disk: the older schema version Room is about to
 * migrate from (1 = the RN app's database), or null when there was nothing to migrate. The
 * migration itself runs on the first query; the apps report it as data_migrated.
 */
object DatabaseOpen {
    var migratingFrom: Int? = null
        internal set
}

/**
 * Opens the database at [path], first adopting a database the RN app left there. Each
 * platform supplies a builder for the right file location.
 */
fun openAppDatabase(path: String, builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    val onDisk = adoptRnDatabase(path)
    DatabaseOpen.migratingFrom = onDisk?.takeIf { it in 1 until DATABASE_VERSION }
    return builder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .build()
}
