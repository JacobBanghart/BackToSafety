package com.backtosafety.core.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL

/**
 * Version 1 (the RN app's schema, spec/db-schema.json) to version 2 (Room's shape):
 * every table is rebuilt from Room's exported CREATE statement and its rows copied
 * across. Columns that were nullable in RN but are NOT NULL now take the RN default
 * via COALESCE. Generated from shared/schemas/.../2.json and spec/db-schema.json;
 * the tests in DatabaseTest check it against the committed RN fixture.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // profile
        connection.execSQL("CREATE TABLE `new_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `nickname` TEXT, `date_of_birth` TEXT, `photo_uri` TEXT, `height` TEXT, `weight` TEXT, `hair_color` TEXT, `eye_color` TEXT, `identifying_marks` TEXT, `medical_conditions` TEXT, `medications` TEXT, `allergies` TEXT, `cognitive_status` TEXT, `dominant_hand` TEXT, `mobility_level` TEXT, `communication_preference` TEXT, `escalation_signs` TEXT, `deescalation_techniques` TEXT, `approach_guidance` TEXT, `likes` TEXT, `dislikes_triggers` TEXT, `safe_word` TEXT, `locative_device_info` TEXT, `id_bracelets` TEXT, `medic_alert_id` TEXT, `medic_alert_hotline` TEXT, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(`id`))")
        connection.execSQL("INSERT INTO `new_profile` (`id`, `name`, `nickname`, `date_of_birth`, `photo_uri`, `height`, `weight`, `hair_color`, `eye_color`, `identifying_marks`, `medical_conditions`, `medications`, `allergies`, `cognitive_status`, `dominant_hand`, `mobility_level`, `communication_preference`, `escalation_signs`, `deescalation_techniques`, `approach_guidance`, `likes`, `dislikes_triggers`, `safe_word`, `locative_device_info`, `id_bracelets`, `medic_alert_id`, `medic_alert_hotline`, `created_at`, `updated_at`) SELECT `id`, `name`, `nickname`, `date_of_birth`, `photo_uri`, `height`, `weight`, `hair_color`, `eye_color`, `identifying_marks`, `medical_conditions`, `medications`, `allergies`, `cognitive_status`, `dominant_hand`, `mobility_level`, `communication_preference`, `escalation_signs`, `deescalation_techniques`, `approach_guidance`, `likes`, `dislikes_triggers`, `safe_word`, `locative_device_info`, `id_bracelets`, `medic_alert_id`, `medic_alert_hotline`, `created_at`, `updated_at` FROM `profile`")
        connection.execSQL("DROP TABLE `profile`")
        connection.execSQL("ALTER TABLE `new_profile` RENAME TO `profile`")
        // contacts
        connection.execSQL("CREATE TABLE `new_contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `relationship` TEXT, `role` TEXT, `address` TEXT, `notify_on_emergency` INTEGER NOT NULL DEFAULT 1, `share_medical_info` INTEGER NOT NULL DEFAULT 0, `notes` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP)")
        connection.execSQL("INSERT INTO `new_contacts` (`id`, `name`, `phone`, `relationship`, `role`, `address`, `notify_on_emergency`, `share_medical_info`, `notes`, `sort_order`, `created_at`, `updated_at`) SELECT `id`, `name`, `phone`, `relationship`, `role`, `address`, COALESCE(`notify_on_emergency`, 1), COALESCE(`share_medical_info`, 0), `notes`, COALESCE(`sort_order`, 0), `created_at`, `updated_at` FROM `contacts`")
        connection.execSQL("DROP TABLE `contacts`")
        connection.execSQL("ALTER TABLE `new_contacts` RENAME TO `contacts`")
        // destinations
        connection.execSQL("CREATE TABLE `new_destinations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `latitude` REAL, `longitude` REAL, `category` TEXT, `reason` TEXT, `distance_from_home` TEXT, `risk_level` TEXT, `notes` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP)")
        connection.execSQL("INSERT INTO `new_destinations` (`id`, `name`, `address`, `latitude`, `longitude`, `category`, `reason`, `distance_from_home`, `risk_level`, `notes`, `sort_order`, `created_at`, `updated_at`) SELECT `id`, `name`, `address`, `latitude`, `longitude`, `category`, `reason`, `distance_from_home`, `risk_level`, `notes`, COALESCE(`sort_order`, 0), `created_at`, `updated_at` FROM `destinations`")
        connection.execSQL("DROP TABLE `destinations`")
        connection.execSQL("ALTER TABLE `new_destinations` RENAME TO `destinations`")
        // incidents
        connection.execSQL("CREATE TABLE `new_incidents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `started_at` TEXT NOT NULL, `ended_at` TEXT, `outcome` TEXT, `last_seen_lat` REAL, `last_seen_lon` REAL, `last_seen_accuracy` REAL, `found_lat` REAL, `found_lon` REAL, `found_location_name` TEXT, `weather` TEXT, `time_of_day` TEXT, `trigger_identified` TEXT, `wearing` TEXT, `areas_checked` TEXT, `people_contacted` TEXT, `notes` TEXT, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP)")
        connection.execSQL("INSERT INTO `new_incidents` (`id`, `started_at`, `ended_at`, `outcome`, `last_seen_lat`, `last_seen_lon`, `last_seen_accuracy`, `found_lat`, `found_lon`, `found_location_name`, `weather`, `time_of_day`, `trigger_identified`, `wearing`, `areas_checked`, `people_contacted`, `notes`, `created_at`) SELECT `id`, `started_at`, `ended_at`, `outcome`, `last_seen_lat`, `last_seen_lon`, `last_seen_accuracy`, `found_lat`, `found_lon`, `found_location_name`, `weather`, `time_of_day`, `trigger_identified`, `wearing`, `areas_checked`, `people_contacted`, `notes`, `created_at` FROM `incidents`")
        connection.execSQL("DROP TABLE `incidents`")
        connection.execSQL("ALTER TABLE `new_incidents` RENAME TO `incidents`")
        // safety_checks
        connection.execSQL("CREATE TABLE `new_safety_checks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT, `item_key` TEXT NOT NULL, `completed` INTEGER NOT NULL DEFAULT 0, `completed_at` TEXT, `notes` TEXT)")
        connection.execSQL("INSERT INTO `new_safety_checks` (`id`, `category`, `item_key`, `completed`, `completed_at`, `notes`) SELECT `id`, `category`, `item_key`, COALESCE(`completed`, 0), `completed_at`, `notes` FROM `safety_checks`")
        connection.execSQL("DROP TABLE `safety_checks`")
        connection.execSQL("ALTER TABLE `new_safety_checks` RENAME TO `safety_checks`")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_safety_checks_item_key` ON `safety_checks` (`item_key`)")
        // settings
        connection.execSQL("CREATE TABLE `new_settings` (`key` TEXT NOT NULL, `value` TEXT, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(`key`))")
        connection.execSQL("INSERT INTO `new_settings` (`key`, `value`, `updated_at`) SELECT `key`, `value`, `updated_at` FROM `settings`")
        connection.execSQL("DROP TABLE `settings`")
        connection.execSQL("ALTER TABLE `new_settings` RENAME TO `settings`")
        // onboarding
        connection.execSQL("CREATE TABLE `new_onboarding` (`step` TEXT NOT NULL, `completed` INTEGER NOT NULL DEFAULT 0, `completed_at` TEXT, `skipped` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`step`))")
        connection.execSQL("INSERT INTO `new_onboarding` (`step`, `completed`, `completed_at`, `skipped`) SELECT `step`, COALESCE(`completed`, 0), `completed_at`, COALESCE(`skipped`, 0) FROM `onboarding`")
        connection.execSQL("DROP TABLE `onboarding`")
        connection.execSQL("ALTER TABLE `new_onboarding` RENAME TO `onboarding`")
    }
}

/**
 * An RN-created nijii.db has user_version 0 (expo-sqlite tracks versions in its own
 * schema_version table). Room would treat 0 as a brand-new database, so mark it as
 * version 1 and let MIGRATION_1_2 bring it to Room's shape. No file, no-op.
 */
fun adoptRnDatabase(path: String) {
    val connection = runCatching { BundledSQLiteDriver().open(path, SQLITE_OPEN_READWRITE) }.getOrNull() ?: return
    connection.use { db ->
        val userVersion = db.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) }
        val isRnDatabase = db.prepare(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'",
        ).use { it.step(); it.getLong(0) > 0 }
        if (userVersion == 0L && isRnDatabase) db.execSQL("PRAGMA user_version = 1")
    }
}
