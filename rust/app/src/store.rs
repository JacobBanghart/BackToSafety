//! The app-level store: opened once at startup, seeded like MainActivity/App.kt do, and
//! provided through Dioxus context. Screens call `use_store()`; the emergency screen also
//! uses `use_emergency_away()`.
#![allow(dead_code)] // screens pick these up as they land

use dioxus::prelude::*;
use nijii_core::alerts::ScheduledAlert;
use nijii_core::emergency_away::{AlertScheduler, EmergencyAway};
use nijii_core::store::{DATABASE_NAME, Store};
use std::path::PathBuf;
use std::sync::{Arc, OnceLock};

/// The Android databases dir of the Rust app (the KMP app keeps nijii.db in
/// <filesDir>/SQLite so it can adopt the RN app's file; the Rust app has no RN data).
#[cfg(target_os = "android")]
const ANDROID_DATABASES_DIR: &str = "/data/data/com.backtosafety.rust/databases";

/// Where nijii.db lives. `NIJII_DB` overrides it (desktop runs, debugging).
fn database_path() -> PathBuf {
    if let Some(path) = std::env::var_os("NIJII_DB") {
        return path.into();
    }
    #[cfg(target_os = "android")]
    {
        PathBuf::from(ANDROID_DATABASES_DIR).join(DATABASE_NAME)
    }
    #[cfg(not(target_os = "android"))]
    {
        let base = std::env::var_os("XDG_DATA_HOME")
            .map(PathBuf::from)
            .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".local/share")))
            .unwrap_or_else(std::env::temp_dir);
        base.join("nijii").join(DATABASE_NAME)
    }
}

/// Delivers nothing. The emergency lane replaces this with the AlarmManager scheduler by
/// changing [`alert_scheduler`].
pub struct NoopScheduler;

impl AlertScheduler for NoopScheduler {
    fn schedule(&self, _alerts: &[ScheduledAlert]) {}
    fn cancel_all(&self) {}
}

/// The scheduler the app's [`EmergencyAway`] delivers through.
fn alert_scheduler() -> Arc<dyn AlertScheduler> {
    Arc::new(NoopScheduler)
}

/// What startup builds, once.
#[derive(Clone)]
struct Services {
    store: Arc<Store>,
    away: Arc<EmergencyAway>,
}

static SERVICES: OnceLock<Services> = OnceLock::new();

fn services() -> &'static Services {
    SERVICES.get_or_init(|| {
        let path = database_path();
        // A failure here would otherwise start the app on an empty database that hides the
        // caregiver's data, so stop loudly instead.
        let store = Arc::new(
            Store::open(&path).unwrap_or_else(|e| panic!("can't open {}: {e}", path.display())),
        );
        store.seed().expect("seeding the database");
        let away = Arc::new(EmergencyAway::new(store.clone(), alert_scheduler()));
        Services { store, away }
    })
}

/// The store, for code outside components (platform callbacks). Opens it on first use.
pub fn store() -> Arc<Store> {
    services().store.clone()
}

/// Opens and seeds the store (once per process) and puts it in context. Call it at the
/// top of `App`.
pub fn provide() {
    let s = services().clone();
    use_context_provider(|| s.store.clone());
    use_context_provider(|| s.away.clone());
}

/// The store, from context ([`provide`] ran in `App`).
pub fn use_store() -> Arc<Store> {
    use_context::<Arc<Store>>()
}

/// The countdown-alert logic shared by both apps, from context.
pub fn use_emergency_away() -> Arc<EmergencyAway> {
    use_context::<Arc<EmergencyAway>>()
}
