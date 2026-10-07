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
    version = 2,
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
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

const val DATABASE_NAME = "nijii.db"

/**
 * Opens the database at [path], first adopting a database the RN app left there. Each
 * platform supplies a builder for the right file location.
 */
fun openAppDatabase(path: String, builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    adoptRnDatabase(path)
    return builder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .build()
}
