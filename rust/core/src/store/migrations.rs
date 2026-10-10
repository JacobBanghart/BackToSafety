//! Schema versions: 1 is the RN app's database (spec/db-schema.json), 2 is the same tables in
//! Room's shape. Version 2's DDL is the exported schema (kmp/shared/schemas/.../2.json), so
//! the file is byte-compatible with what the Kotlin app's Room creates; the identity hash is
//! Room's, kept in `room_master_table`, so either app opens the other's file.

use rusqlite::Connection;

pub const DATABASE_VERSION: i32 = 2;

/// Room's identity hash for schema version 2.
const IDENTITY_HASH: &str = "48e3e4e8e60f20f27be65e459cfb0b71";

/// Room's CREATE statements for a fresh version 2 database, in its order.
const CREATE_V2: [&str; 8] = [
    r#"CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `nickname` TEXT, `date_of_birth` TEXT, `photo_uri` TEXT, `height` TEXT, `weight` TEXT, `hair_color` TEXT, `eye_color` TEXT, `identifying_marks` TEXT, `medical_conditions` TEXT, `medications` TEXT, `allergies` TEXT, `cognitive_status` TEXT, `dominant_hand` TEXT, `mobility_level` TEXT, `communication_preference` TEXT, `escalation_signs` TEXT, `deescalation_techniques` TEXT, `approach_guidance` TEXT, `likes` TEXT, `dislikes_triggers` TEXT, `safe_word` TEXT, `locative_device_info` TEXT, `id_bracelets` TEXT, `medic_alert_id` TEXT, `medic_alert_hotline` TEXT, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(`id`))"#,
    r#"CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `relationship` TEXT, `role` TEXT, `address` TEXT, `notify_on_emergency` INTEGER NOT NULL DEFAULT 1, `share_medical_info` INTEGER NOT NULL DEFAULT 0, `notes` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP)"#,
    r#"CREATE TABLE IF NOT EXISTS `destinations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `latitude` REAL, `longitude` REAL, `category` TEXT, `reason` TEXT, `distance_from_home` TEXT, `risk_level` TEXT, `notes` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP)"#,
    r#"CREATE TABLE IF NOT EXISTS `incidents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `started_at` TEXT NOT NULL, `ended_at` TEXT, `outcome` TEXT, `last_seen_lat` REAL, `last_seen_lon` REAL, `last_seen_accuracy` REAL, `found_lat` REAL, `found_lon` REAL, `found_location_name` TEXT, `weather` TEXT, `time_of_day` TEXT, `trigger_identified` TEXT, `wearing` TEXT, `areas_checked` TEXT, `people_contacted` TEXT, `notes` TEXT, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP)"#,
    r#"CREATE TABLE IF NOT EXISTS `safety_checks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT, `item_key` TEXT NOT NULL, `completed` INTEGER NOT NULL DEFAULT 0, `completed_at` TEXT, `notes` TEXT)"#,
    r#"CREATE UNIQUE INDEX IF NOT EXISTS `index_safety_checks_item_key` ON `safety_checks` (`item_key`)"#,
    r#"CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(`key`))"#,
    r#"CREATE TABLE IF NOT EXISTS `onboarding` (`step` TEXT NOT NULL, `completed` INTEGER NOT NULL DEFAULT 0, `completed_at` TEXT, `skipped` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`step`))"#,
];

fn record_identity(c: &Connection) -> rusqlite::Result<()> {
    c.execute_batch(
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    )?;
    c.execute(
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?1)",
        [IDENTITY_HASH],
    )?;
    Ok(())
}

/// Creates the version 2 schema in an empty database (a fresh install).
pub(crate) fn create_v2(c: &Connection) -> rusqlite::Result<()> {
    for sql in CREATE_V2 {
        c.execute_batch(sql)?;
    }
    record_identity(c)
}

/// Version 1 (the RN app's schema) to version 2 (Room's shape): every table is rebuilt from
/// Room's CREATE statement and its rows copied across. Columns that were nullable in RN but are
/// NOT NULL now take the RN default via COALESCE. Ported statement for statement from
/// MIGRATION_1_2 (kmp/shared db/Migrations.kt).
const MIGRATION_1_2: [&str; 29] = [
    r#"CREATE TABLE `new_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `nickname` TEXT, `date_of_birth` TEXT, `photo_uri` TEXT, `height` TEXT, `weight` TEXT, `hair_color` TEXT, `eye_color` TEXT, `identifying_marks` TEXT, `medical_conditions` TEXT, `medications` TEXT, `allergies` TEXT, `cognitive_status` TEXT, `dominant_hand` TEXT, `mobility_level` TEXT, `communication_preference` TEXT, `escalation_signs` TEXT, `deescalation_techniques` TEXT, `approach_guidance` TEXT, `likes` TEXT, `dislikes_triggers` TEXT, `safe_word` TEXT, `locative_device_info` TEXT, `id_bracelets` TEXT, `medic_alert_id` TEXT, `medic_alert_hotline` TEXT, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(`id`))"#,
    r#"INSERT INTO `new_profile` (`id`, `name`, `nickname`, `date_of_birth`, `photo_uri`, `height`, `weight`, `hair_color`, `eye_color`, `identifying_marks`, `medical_conditions`, `medications`, `allergies`, `cognitive_status`, `dominant_hand`, `mobility_level`, `communication_preference`, `escalation_signs`, `deescalation_techniques`, `approach_guidance`, `likes`, `dislikes_triggers`, `safe_word`, `locative_device_info`, `id_bracelets`, `medic_alert_id`, `medic_alert_hotline`, `created_at`, `updated_at`) SELECT `id`, `name`, `nickname`, `date_of_birth`, `photo_uri`, `height`, `weight`, `hair_color`, `eye_color`, `identifying_marks`, `medical_conditions`, `medications`, `allergies`, `cognitive_status`, `dominant_hand`, `mobility_level`, `communication_preference`, `escalation_signs`, `deescalation_techniques`, `approach_guidance`, `likes`, `dislikes_triggers`, `safe_word`, `locative_device_info`, `id_bracelets`, `medic_alert_id`, `medic_alert_hotline`, `created_at`, `updated_at` FROM `profile`"#,
    r#"DROP TABLE `profile`"#,
    r#"ALTER TABLE `new_profile` RENAME TO `profile`"#,
    r#"CREATE TABLE `new_contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `relationship` TEXT, `role` TEXT, `address` TEXT, `notify_on_emergency` INTEGER NOT NULL DEFAULT 1, `share_medical_info` INTEGER NOT NULL DEFAULT 0, `notes` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP)"#,
    r#"INSERT INTO `new_contacts` (`id`, `name`, `phone`, `relationship`, `role`, `address`, `notify_on_emergency`, `share_medical_info`, `notes`, `sort_order`, `created_at`, `updated_at`) SELECT `id`, `name`, `phone`, `relationship`, `role`, `address`, COALESCE(`notify_on_emergency`, 1), COALESCE(`share_medical_info`, 0), `notes`, COALESCE(`sort_order`, 0), `created_at`, `updated_at` FROM `contacts`"#,
    r#"DROP TABLE `contacts`"#,
    r#"ALTER TABLE `new_contacts` RENAME TO `contacts`"#,
    r#"CREATE TABLE `new_destinations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `latitude` REAL, `longitude` REAL, `category` TEXT, `reason` TEXT, `distance_from_home` TEXT, `risk_level` TEXT, `notes` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP)"#,
    r#"INSERT INTO `new_destinations` (`id`, `name`, `address`, `latitude`, `longitude`, `category`, `reason`, `distance_from_home`, `risk_level`, `notes`, `sort_order`, `created_at`, `updated_at`) SELECT `id`, `name`, `address`, `latitude`, `longitude`, `category`, `reason`, `distance_from_home`, `risk_level`, `notes`, COALESCE(`sort_order`, 0), `created_at`, `updated_at` FROM `destinations`"#,
    r#"DROP TABLE `destinations`"#,
    r#"ALTER TABLE `new_destinations` RENAME TO `destinations`"#,
    r#"CREATE TABLE `new_incidents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `started_at` TEXT NOT NULL, `ended_at` TEXT, `outcome` TEXT, `last_seen_lat` REAL, `last_seen_lon` REAL, `last_seen_accuracy` REAL, `found_lat` REAL, `found_lon` REAL, `found_location_name` TEXT, `weather` TEXT, `time_of_day` TEXT, `trigger_identified` TEXT, `wearing` TEXT, `areas_checked` TEXT, `people_contacted` TEXT, `notes` TEXT, `created_at` TEXT DEFAULT CURRENT_TIMESTAMP)"#,
    r#"INSERT INTO `new_incidents` (`id`, `started_at`, `ended_at`, `outcome`, `last_seen_lat`, `last_seen_lon`, `last_seen_accuracy`, `found_lat`, `found_lon`, `found_location_name`, `weather`, `time_of_day`, `trigger_identified`, `wearing`, `areas_checked`, `people_contacted`, `notes`, `created_at`) SELECT `id`, `started_at`, `ended_at`, `outcome`, `last_seen_lat`, `last_seen_lon`, `last_seen_accuracy`, `found_lat`, `found_lon`, `found_location_name`, `weather`, `time_of_day`, `trigger_identified`, `wearing`, `areas_checked`, `people_contacted`, `notes`, `created_at` FROM `incidents`"#,
    r#"DROP TABLE `incidents`"#,
    r#"ALTER TABLE `new_incidents` RENAME TO `incidents`"#,
    r#"CREATE TABLE `new_safety_checks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT, `item_key` TEXT NOT NULL, `completed` INTEGER NOT NULL DEFAULT 0, `completed_at` TEXT, `notes` TEXT)"#,
    r#"INSERT INTO `new_safety_checks` (`id`, `category`, `item_key`, `completed`, `completed_at`, `notes`) SELECT `id`, `category`, `item_key`, COALESCE(`completed`, 0), `completed_at`, `notes` FROM `safety_checks`"#,
    r#"DROP TABLE `safety_checks`"#,
    r#"ALTER TABLE `new_safety_checks` RENAME TO `safety_checks`"#,
    r#"CREATE UNIQUE INDEX IF NOT EXISTS `index_safety_checks_item_key` ON `safety_checks` (`item_key`)"#,
    r#"CREATE TABLE `new_settings` (`key` TEXT NOT NULL, `value` TEXT, `updated_at` TEXT DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(`key`))"#,
    r#"INSERT INTO `new_settings` (`key`, `value`, `updated_at`) SELECT `key`, `value`, `updated_at` FROM `settings`"#,
    r#"DROP TABLE `settings`"#,
    r#"ALTER TABLE `new_settings` RENAME TO `settings`"#,
    r#"CREATE TABLE `new_onboarding` (`step` TEXT NOT NULL, `completed` INTEGER NOT NULL DEFAULT 0, `completed_at` TEXT, `skipped` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`step`))"#,
    r#"INSERT INTO `new_onboarding` (`step`, `completed`, `completed_at`, `skipped`) SELECT `step`, COALESCE(`completed`, 0), `completed_at`, COALESCE(`skipped`, 0) FROM `onboarding`"#,
    r#"DROP TABLE `onboarding`"#,
    r#"ALTER TABLE `new_onboarding` RENAME TO `onboarding`"#,
];

pub(crate) fn migrate_1_2(c: &Connection) -> rusqlite::Result<()> {
    for sql in MIGRATION_1_2 {
        c.execute_batch(sql)?;
    }
    record_identity(c)
}

/// An RN-created nijii.db has user_version 0 (expo-sqlite tracks versions in its own
/// schema_version table). Treating 0 as a brand-new database would be wrong, so mark it as
/// version 1 and let the migration bring it to Room's shape. Returns the schema version now on
/// disk (1 for an RN database), or None when there's no file yet.
pub fn adopt_rn_database(path: &std::path::Path) -> Option<i32> {
    if !path.exists() {
        return None;
    }
    let c = Connection::open_with_flags(path, rusqlite::OpenFlags::SQLITE_OPEN_READ_WRITE).ok()?;
    let user_version: i64 = c.query_row("PRAGMA user_version", [], |r| r.get(0)).ok()?;
    let is_rn: i64 = c
        .query_row(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'",
            [],
            |r| r.get(0),
        )
        .ok()?;
    if user_version == 0 && is_rn > 0 {
        c.execute_batch("PRAGMA user_version = 1").ok()?;
        Some(1)
    } else {
        Some(user_version as i32)
    }
}
