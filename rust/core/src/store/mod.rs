//! The app's data, with the RN app's semantics (spec/storage.md): what's seeded on every
//! launch, how a partial profile save merges, and what counts as onboarded. A port of kmp/shared's
//! `data/Store.kt` over `db/` (Room's DAOs, here plain SQL on rusqlite). The API is
//! synchronous: one connection behind a mutex, so a `Store` is shared as `Arc<Store>`.

mod entities;
mod migrations;

pub use entities::{
    ContactEntity, DestinationEntity, IncidentEntity, OnboardingStepEntity, ProfileEntity,
    SafetyCheckEntity, SettingEntity,
};
pub use migrations::{DATABASE_VERSION, adopt_rn_database};

use crate::alerts::{Properties, readiness_properties};
use crate::emergency::{ActiveEmergency, parse_active_emergency, serialize_active_emergency};
use crate::profile::Profile;
use rusqlite::{Connection, OptionalExtension, params};
use serde_json::json;
use std::path::Path;
use std::sync::{Mutex, MutexGuard};
use std::time::Instant;

/// nijii.db, the same file the RN app wrote (spec/storage.md).
pub const DATABASE_NAME: &str = "nijii.db";

#[derive(Debug)]
pub enum StoreError {
    Sqlite(rusqlite::Error),
    Io(std::io::Error),
    /// `update_profile` was given a field that isn't a Profile property.
    UnknownProfileField(String),
    /// The file was written by a newer schema than this build knows.
    NewerDatabase(i32),
}

impl StoreError {
    /// The error's type name, for save_failed (never its message, which could hold what was typed).
    pub fn type_name(&self) -> &'static str {
        match self {
            StoreError::Sqlite(_) => "SqliteError",
            StoreError::Io(_) => "IoError",
            StoreError::UnknownProfileField(_) => "UnknownProfileField",
            StoreError::NewerDatabase(_) => "NewerDatabase",
        }
    }
}

impl std::fmt::Display for StoreError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            StoreError::Sqlite(e) => write!(f, "database error: {e}"),
            StoreError::Io(e) => write!(f, "storage error: {e}"),
            StoreError::UnknownProfileField(field) => write!(f, "unknown profile field: {field}"),
            StoreError::NewerDatabase(v) => write!(f, "database is from a newer version ({v})"),
        }
    }
}

impl std::error::Error for StoreError {}

impl From<rusqlite::Error> for StoreError {
    fn from(e: rusqlite::Error) -> Self {
        StoreError::Sqlite(e)
    }
}

impl From<std::io::Error> for StoreError {
    fn from(e: std::io::Error) -> Self {
        StoreError::Io(e)
    }
}

pub type Result<T> = std::result::Result<T, StoreError>;

/// Rows the RN app inserts on every launch (spec/seed.json), if missing.
pub const ONBOARDING_STEPS: [&str; 6] = [
    "welcome",
    "profile_name",
    "profile_photo",
    "profile_appearance",
    "emergency_contact",
    "complete",
];

/// (category, item_key) of the seeded safety checks.
pub const SAFETY_CHECKS: [(&str, &str); 12] = [
    ("at_home", "visual_supports_doors"),
    ("at_home", "locks_high_location"),
    ("at_home", "door_chimes"),
    ("at_home", "geofence_setup"),
    ("at_home", "physical_boundaries"),
    ("away_from_home", "alert_caregivers_staff"),
    ("away_from_home", "safety_plan_locations"),
    ("away_from_home", "introduce_first_responders"),
    ("away_from_home", "evaluate_locative_tech"),
    ("foundation", "social_stories"),
    ("foundation", "water_safety_classes"),
    ("foundation", "safety_responsibility"),
];

pub struct Store {
    conn: Mutex<Connection>,
    cached_device_id: Mutex<Option<String>>,
    migrated_from: Option<i32>,
    migration_ms: Option<u64>,
}

/// SQLite's CURRENT_TIMESTAMP: UTC, "YYYY-MM-DD HH:MM:SS".
pub fn sqlite_now() -> String {
    chrono::Utc::now().format("%Y-%m-%d %H:%M:%S").to_string()
}

impl Store {
    pub const ACTIVE_EMERGENCY: &'static str = "active_emergency";
    /// When the time away from the emergency screen began (epoch ms), or empty (EmergencyAway).
    pub const EMERGENCY_AWAY_FROM: &'static str = "emergency_away_from";
    /// Set once the notification permission prompt has been shown.
    pub const NOTIFICATIONS_PROMPTED: &'static str = "notifications_prompted";
    pub const DEVICE_ID: &'static str = "device_id";
    pub const THEME_PREFERENCE: &'static str = "theme_preference";
    pub const LANGUAGE_PREFERENCE: &'static str = "language_preference";
    pub const ONBOARDING_STEPS: [&'static str; 6] = ONBOARDING_STEPS;
    pub const SAFETY_CHECKS: [(&'static str, &'static str); 12] = SAFETY_CHECKS;

    /// Opens the database at `path` (creating it, and its folder, on a fresh install), first
    /// adopting a database the RN app left there and migrating older schemas to the current one.
    pub fn open(path: impl AsRef<Path>) -> Result<Store> {
        let path = path.as_ref();
        if let Some(dir) = path.parent().filter(|d| !d.as_os_str().is_empty()) {
            std::fs::create_dir_all(dir)?;
        }
        let on_disk = adopt_rn_database(path);
        let migrating_from = on_disk.filter(|v| (1..DATABASE_VERSION).contains(v));
        let started = Instant::now();
        let mut conn = Connection::open(path)?;
        Self::set_up(&mut conn)?;
        let migration_ms = migrating_from.map(|_| started.elapsed().as_millis() as u64);
        Ok(Self::with_connection(conn, migrating_from, migration_ms))
    }

    /// A fresh, private database that lives as long as the Store (tests).
    pub fn open_in_memory() -> Result<Store> {
        let mut conn = Connection::open_in_memory()?;
        Self::set_up(&mut conn)?;
        Ok(Self::with_connection(conn, None, None))
    }

    fn with_connection(
        conn: Connection,
        migrated_from: Option<i32>,
        migration_ms: Option<u64>,
    ) -> Store {
        Store {
            conn: Mutex::new(conn),
            cached_device_id: Mutex::new(None),
            migrated_from,
            migration_ms,
        }
    }

    /// Creates the schema on a fresh file, or migrates an older one; user_version is the
    /// schema version, as Room keeps it.
    fn set_up(conn: &mut Connection) -> Result<()> {
        let tx = conn.transaction()?;
        let version: i32 = tx.query_row("PRAGMA user_version", [], |r| r.get(0))?;
        match version {
            0 => migrations::create_v2(&tx)?,
            1 => migrations::migrate_1_2(&tx)?,
            v if v == DATABASE_VERSION => return Ok(()),
            v => return Err(StoreError::NewerDatabase(v)),
        }
        tx.execute_batch(&format!("PRAGMA user_version = {DATABASE_VERSION}"))?;
        tx.commit()?;
        Ok(())
    }

    /// The older schema version this open migrated from (1 = the RN app's database), or None
    /// when there was nothing to migrate. The apps report it as data_migrated.
    pub fn migrated_from(&self) -> Option<i32> {
        self.migrated_from
    }

    /// How long that migration took, for data_migrated's duration_ms.
    pub fn migration_duration_ms(&self) -> Option<u64> {
        self.migration_ms
    }

    fn conn(&self) -> MutexGuard<'_, Connection> {
        self.conn.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// Rows the RN app inserts on every launch (spec/seed.json), if missing.
    pub fn seed(&self) -> Result<()> {
        let mut c = self.conn();
        let tx = c.transaction()?;
        insert_onboarding_steps(&tx)?;
        for (category, key) in SAFETY_CHECKS {
            tx.execute(
                "INSERT OR IGNORE INTO safety_checks (id, category, item_key, completed, completed_at, notes) \
                 VALUES (NULL, ?1, ?2, 0, NULL, NULL)",
                params![category, key],
            )?;
        }
        tx.commit()?;
        Ok(())
    }

    /// Onboarded once no step is left neither completed nor skipped (spec/storage.md).
    pub fn is_onboarded(&self) -> Result<bool> {
        Ok(self.conn().query_row(
            "SELECT COUNT(*) = 0 FROM onboarding WHERE completed = 0 AND skipped = 0",
            [],
            |r| r.get(0),
        )?)
    }

    /// Skipping a step marks it completed too, as the RN screens do.
    pub fn complete_step(&self, step: &str) -> Result<()> {
        self.conn().execute(
            "UPDATE onboarding SET completed = 1 WHERE step = ?1",
            [step],
        )?;
        Ok(())
    }

    /// Every onboarding step row.
    pub fn onboarding_steps(&self) -> Result<Vec<OnboardingStepEntity>> {
        let c = self.conn();
        let mut st = c.prepare("SELECT * FROM onboarding")?;
        let rows = st.query_map([], OnboardingStepEntity::from_row)?;
        Ok(rows.collect::<rusqlite::Result<_>>()?)
    }

    /// Every seeded safety check row, by id.
    pub fn safety_checks(&self) -> Result<Vec<SafetyCheckEntity>> {
        let c = self.conn();
        let mut st = c.prepare("SELECT * FROM safety_checks ORDER BY id")?;
        let rows = st.query_map([], SafetyCheckEntity::from_row)?;
        Ok(rows.collect::<rusqlite::Result<_>>()?)
    }

    pub fn profile(&self) -> Result<Option<Profile>> {
        Ok(self.profile_entity()?.map(|e| e.to_profile()))
    }

    /// The stored profile row itself (with its timestamps), for `save_profile`'s callers.
    pub fn profile_entity(&self) -> Result<Option<ProfileEntity>> {
        get_profile(&self.conn())
    }

    /// Saves a profile change. Like the RN app, the first save creates the single profile row
    /// and later saves update it.
    pub fn save_profile(&self, change: impl FnOnce(ProfileEntity) -> ProfileEntity) -> Result<()> {
        let now = sqlite_now();
        let current = self.profile_entity()?.unwrap_or_else(|| ProfileEntity {
            created_at: Some(now.clone()),
            ..ProfileEntity::default()
        });
        let created_at = current.created_at.clone().or(Some(now.clone()));
        let mut changed = change(current);
        // updated_at as the RN update sets it; created_at kept.
        changed.created_at = changed.created_at.or(created_at);
        changed.updated_at = Some(now);
        changed.save(&self.conn())?;
        Ok(())
    }

    /// A partial profile save by field name (the Profile property names, camelCase), as the RN
    /// screens save: given fields are set (None clears), the rest kept.
    pub fn update_profile(&self, values: &[(&str, Option<&str>)]) -> Result<()> {
        // An unknown field fails the whole save before anything is written.
        let mut probe = ProfileEntity::default();
        for (field, value) in values {
            probe
                .set_field(field, *value)
                .map_err(StoreError::UnknownProfileField)?;
        }
        self.save_profile(|mut profile| {
            for (field, value) in values {
                let _ = profile.set_field(field, *value);
            }
            profile
        })
    }

    /// Adds a contact at `sort_order`, or after the last one (database/contacts.native.ts).
    pub fn add_contact(&self, contact: ContactEntity, sort_order: Option<i32>) -> Result<i64> {
        let now = sqlite_now();
        let c = self.conn();
        let sort_order = match sort_order {
            Some(s) => s,
            None => max_sort_order(&c, "contacts")?.map_or(0, |m| m + 1),
        };
        c.execute(
            "INSERT INTO contacts (id, name, phone, relationship, role, address, notify_on_emergency, \
             share_medical_info, notes, sort_order, created_at, updated_at) \
             VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12)",
            params![
                (contact.id != 0).then_some(contact.id),
                contact.name,
                contact.phone,
                contact.relationship,
                contact.role,
                contact.address,
                contact.notify_on_emergency,
                contact.share_medical_info,
                contact.notes,
                sort_order,
                contact.created_at.unwrap_or_else(|| now.clone()),
                contact.updated_at.unwrap_or(now),
            ],
        )?;
        Ok(c.last_insert_rowid())
    }

    /// Saves the edit form: every field, so a cleared one is cleared (F-35).
    pub fn update_contact(&self, c: &ContactEntity) -> Result<()> {
        self.conn().execute(
            "UPDATE contacts SET name = ?2, phone = ?3, relationship = ?4, role = ?5, address = ?6, \
             notify_on_emergency = ?7, share_medical_info = ?8, notes = ?9, updated_at = CURRENT_TIMESTAMP \
             WHERE id = ?1",
            params![
                c.id,
                c.name,
                c.phone,
                c.relationship,
                c.role,
                c.address,
                c.notify_on_emergency,
                c.share_medical_info,
                c.notes,
            ],
        )?;
        Ok(())
    }

    pub fn delete_contact(&self, id: i64) -> Result<()> {
        self.conn()
            .execute("DELETE FROM contacts WHERE id = ?1", [id])?;
        Ok(())
    }

    /// After a drag: each contact's sort order becomes its index, written only where it changed.
    pub fn reorder_contacts(&self, ordered: &[ContactEntity]) -> Result<()> {
        let mut c = self.conn();
        let tx = c.transaction()?;
        for (index, contact) in ordered.iter().enumerate() {
            if contact.sort_order != index as i32 {
                set_sort_order(&tx, "contacts", contact.id, index as i32)?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    pub fn add_destination(&self, d: DestinationEntity, sort_order: Option<i32>) -> Result<i64> {
        let now = sqlite_now();
        let c = self.conn();
        let sort_order = match sort_order {
            Some(s) => s,
            None => max_sort_order(&c, "destinations")?.map_or(0, |m| m + 1),
        };
        c.execute(
            "INSERT INTO destinations (id, name, address, latitude, longitude, category, reason, \
             distance_from_home, risk_level, notes, sort_order, created_at, updated_at) \
             VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13)",
            params![
                (d.id != 0).then_some(d.id),
                d.name,
                d.address,
                d.latitude,
                d.longitude,
                d.category,
                d.reason,
                d.distance_from_home,
                d.risk_level,
                d.notes,
                sort_order,
                d.created_at.unwrap_or_else(|| now.clone()),
                d.updated_at.unwrap_or(now),
            ],
        )?;
        Ok(c.last_insert_rowid())
    }

    pub fn update_destination(&self, d: &DestinationEntity) -> Result<()> {
        self.conn().execute(
            "UPDATE destinations SET name = ?2, address = ?3, category = ?4, reason = ?5, \
             distance_from_home = ?6, risk_level = ?7, notes = ?8, updated_at = CURRENT_TIMESTAMP \
             WHERE id = ?1",
            params![
                d.id,
                d.name,
                d.address,
                d.category,
                d.reason,
                d.distance_from_home,
                d.risk_level,
                d.notes,
            ],
        )?;
        Ok(())
    }

    pub fn delete_destination(&self, id: i64) -> Result<()> {
        self.conn()
            .execute("DELETE FROM destinations WHERE id = ?1", [id])?;
        Ok(())
    }

    pub fn reorder_destinations(&self, ordered: &[DestinationEntity]) -> Result<()> {
        let mut c = self.conn();
        let tx = c.transaction()?;
        for (index, d) in ordered.iter().enumerate() {
            if d.sort_order != index as i32 {
                set_sort_order(&tx, "destinations", d.id, index as i32)?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    /// Every contact, in display order (sort_order, then created_at).
    pub fn contacts(&self) -> Result<Vec<ContactEntity>> {
        self.query_contacts("SELECT * FROM contacts ORDER BY sort_order, created_at")
    }

    /// Contacts the emergency alert texts (notify_on_emergency = 1).
    pub fn emergency_contacts(&self) -> Result<Vec<ContactEntity>> {
        self.query_contacts(
            "SELECT * FROM contacts WHERE notify_on_emergency = 1 ORDER BY sort_order, created_at",
        )
    }

    fn query_contacts(&self, sql: &str) -> Result<Vec<ContactEntity>> {
        let c = self.conn();
        let mut st = c.prepare(sql)?;
        let rows = st.query_map([], ContactEntity::from_row)?;
        Ok(rows.collect::<rusqlite::Result<_>>()?)
    }

    /// Every destination, in display order (sort_order, then created_at).
    pub fn destinations(&self) -> Result<Vec<DestinationEntity>> {
        let c = self.conn();
        let mut st = c.prepare("SELECT * FROM destinations ORDER BY sort_order, created_at")?;
        let rows = st.query_map([], DestinationEntity::from_row)?;
        Ok(rows.collect::<rusqlite::Result<_>>()?)
    }

    pub fn active_emergency(&self) -> Result<Option<ActiveEmergency>> {
        Ok(parse_active_emergency(
            self.setting(Self::ACTIVE_EMERGENCY)?.as_deref(),
        ))
    }

    pub fn save_active_emergency(&self, e: &ActiveEmergency) -> Result<()> {
        self.put_setting(Self::ACTIVE_EMERGENCY, &serialize_active_emergency(e))
    }

    /// Ends the emergency: stored as an empty string, not deleted (spec/storage.md).
    pub fn clear_active_emergency(&self) -> Result<()> {
        self.put_setting(Self::ACTIVE_EMERGENCY, "")?;
        self.put_setting(Self::EMERGENCY_AWAY_FROM, "")
    }

    /// Every incident, newest first.
    pub fn incidents(&self) -> Result<Vec<IncidentEntity>> {
        let c = self.conn();
        let mut st = c.prepare("SELECT * FROM incidents ORDER BY started_at DESC")?;
        let rows = st.query_map([], IncidentEntity::from_row)?;
        Ok(rows.collect::<rusqlite::Result<_>>()?)
    }

    pub fn create_incident(&self, started_at: &str) -> Result<i64> {
        Ok(insert_incident(&self.conn(), started_at)?)
    }

    /// Records what happened (spec/storage.md, incidents): `outcome` and `ended_at` when given,
    /// and always the checked steps and clothing. A missing row is created first, for an
    /// emergency started before incidents were recorded.
    pub fn record_incident(
        &self,
        incident_id: Option<i64>,
        started_at: &str,
        checked_steps: &[String],
        wearing: &str,
        outcome: Option<&str>,
        ended_at: Option<&str>,
    ) -> Result<i64> {
        let mut c = self.conn();
        let tx = c.transaction()?;
        let id = match incident_id {
            Some(id) => id,
            None => insert_incident(&tx, started_at)?,
        };
        let mut current = tx
            .query_row(
                "SELECT * FROM incidents WHERE id = ?1",
                [id],
                IncidentEntity::from_row,
            )
            .optional()?
            .unwrap_or_else(|| IncidentEntity {
                id,
                ..IncidentEntity::new(started_at)
            });
        if let Some(o) = outcome {
            current.outcome = Some(o.to_string());
        }
        if let Some(e) = ended_at {
            current.ended_at = Some(e.to_string());
        }
        current.areas_checked =
            Some(serde_json::to_string(checked_steps).expect("strings serialize"));
        current.wearing = (!wearing.is_empty()).then(|| wearing.to_string());
        save_incident(&tx, &current)?;
        tx.commit()?;
        Ok(id)
    }

    /// Settings > Delete Account (database/storage.native.ts clearAllData): empties every
    /// table, then re-creates the onboarding steps so the app starts onboarding again.
    pub fn clear_all_data(&self) -> Result<()> {
        let mut c = self.conn();
        let tx = c.transaction()?;
        for table in [
            "profile",
            "contacts",
            "destinations",
            "incidents",
            "safety_checks",
            "settings",
            "onboarding",
        ] {
            tx.execute(&format!("DELETE FROM {table}"), [])?;
        }
        insert_onboarding_steps(&tx)?;
        tx.commit()?;
        *self
            .cached_device_id
            .lock()
            .unwrap_or_else(|e| e.into_inner()) = None;
        Ok(())
    }

    /// data_migrated's properties, after migrating the database from `from_version` in
    /// `duration_ms`: what came through, so a migration that lost data shows.
    pub fn data_migrated_properties(
        &self,
        from_version: i32,
        duration_ms: u64,
    ) -> Result<Properties> {
        let mut p = Properties::new();
        p.insert("from_version".into(), json!(from_version));
        p.insert("to_version".into(), json!(DATABASE_VERSION));
        p.insert("ok".into(), json!(true));
        p.insert("duration_ms".into(), json!(duration_ms));
        p.insert("has_profile".into(), json!(self.profile()?.is_some()));
        p.insert("contacts_count".into(), json!(self.contacts()?.len()));
        p.insert("places_count".into(), json!(self.destinations()?.len()));
        p.insert("incidents_count".into(), json!(self.incidents()?.len()));
        Ok(p)
    }

    /// app_ready's readiness properties (readiness_properties) from what's stored.
    pub fn readiness_properties(&self) -> Result<Properties> {
        let contacts = self.contacts()?;
        Ok(readiness_properties(
            self.profile()?.as_ref(),
            contacts.len(),
            contacts.iter().filter(|c| c.notify_on_emergency).count(),
            self.destinations()?.len(),
        ))
    }

    /// The anonymous per-install ID (utils/device-id.ts): a random UUID, stored on first use.
    pub fn device_id(&self) -> Result<String> {
        let mut cached = self
            .cached_device_id
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        if let Some(id) = cached.as_ref() {
            return Ok(id.clone());
        }
        let id = match self.setting(Self::DEVICE_ID)?.filter(|v| !v.is_empty()) {
            Some(id) => id,
            None => {
                let id = uuid::Uuid::new_v4().to_string();
                self.put_setting(Self::DEVICE_ID, &id)?;
                id
            }
        };
        *cached = Some(id.clone());
        Ok(id)
    }

    pub fn setting(&self, key: &str) -> Result<Option<String>> {
        Ok(self
            .conn()
            .query_row("SELECT value FROM settings WHERE `key` = ?1", [key], |r| {
                r.get::<_, Option<String>>(0)
            })
            .optional()?
            .flatten())
    }

    pub fn put_setting(&self, key: &str, value: &str) -> Result<()> {
        self.conn().execute(
            "INSERT OR REPLACE INTO settings (`key`, value, updated_at) VALUES (?1, ?2, NULL)",
            params![key, value],
        )?;
        Ok(())
    }
}

fn get_profile(c: &Connection) -> Result<Option<ProfileEntity>> {
    Ok(c.query_row(
        "SELECT * FROM profile WHERE id = 1",
        [],
        ProfileEntity::from_row,
    )
    .optional()?)
}

fn insert_onboarding_steps(c: &Connection) -> rusqlite::Result<()> {
    for step in ONBOARDING_STEPS {
        c.execute(
            "INSERT OR IGNORE INTO onboarding (step, completed, completed_at, skipped) VALUES (?1, 0, NULL, 0)",
            [step],
        )?;
    }
    Ok(())
}

fn max_sort_order(c: &Connection, table: &str) -> rusqlite::Result<Option<i32>> {
    c.query_row(&format!("SELECT MAX(sort_order) FROM {table}"), [], |r| {
        r.get(0)
    })
}

fn set_sort_order(c: &Connection, table: &str, id: i64, sort_order: i32) -> rusqlite::Result<()> {
    c.execute(
        &format!(
            "UPDATE {table} SET sort_order = ?2, updated_at = CURRENT_TIMESTAMP WHERE id = ?1"
        ),
        params![id, sort_order],
    )?;
    Ok(())
}

fn insert_incident(c: &Connection, started_at: &str) -> rusqlite::Result<i64> {
    c.execute(
        "INSERT INTO incidents (started_at, outcome, created_at) VALUES (?1, 'ongoing', ?2)",
        params![started_at, sqlite_now()],
    )?;
    Ok(c.last_insert_rowid())
}

/// Room's @Upsert for an incident: insert, or replace the row with the same id.
fn save_incident(c: &Connection, i: &IncidentEntity) -> rusqlite::Result<()> {
    c.execute(
        "INSERT OR REPLACE INTO incidents (id, started_at, ended_at, outcome, last_seen_lat, last_seen_lon, \
         last_seen_accuracy, found_lat, found_lon, found_location_name, weather, time_of_day, \
         trigger_identified, wearing, areas_checked, people_contacted, notes, created_at) \
         VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18)",
        params![
            (i.id != 0).then_some(i.id),
            i.started_at,
            i.ended_at,
            i.outcome,
            i.last_seen_lat,
            i.last_seen_lon,
            i.last_seen_accuracy,
            i.found_lat,
            i.found_lon,
            i.found_location_name,
            i.weather,
            i.time_of_day,
            i.trigger_identified,
            i.wearing,
            i.areas_checked,
            i.people_contacted,
            i.notes,
            i.created_at,
        ],
    )?;
    Ok(())
}
