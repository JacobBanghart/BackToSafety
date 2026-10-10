//! What the app reports at launch, the twin of androidApp's App.kt launch effect and
//! AppReady.kt. The rules are `nijii_core::startup` (tested there); this calls them at the
//! right moments: [`open_store`] before the first screen, [`use_app_ready`] after its first
//! frame.

use crate::platform::posthog;
use dioxus::prelude::*;
use nijii_core::alerts::Properties;
use nijii_core::startup::{
    AppReady, process_uptime_ms, report_data_migration, report_open_failure,
};
use nijii_core::store::Store;
use std::path::Path;
use std::sync::{Arc, OnceLock};
use std::time::Instant;

static LAUNCHED: OnceLock<Instant> = OnceLock::new();
static APP_READY: AppReady = AppReady::new();
static DEVICE_READINESS: OnceLock<Box<dyn Fn() -> Properties + Send + Sync>> = OnceLock::new();

/// Registers what only the device knows for app_ready (`notifications_enabled`, `exact_alarms`
/// in the KMP app: CountdownNotifications). Called once by the lane that owns the alarms; until
/// then app_ready goes without them.
#[allow(dead_code)]
pub fn set_device_readiness(properties: impl Fn() -> Properties + Send + Sync + 'static) {
    let _ = DEVICE_READINESS.set(Box::new(properties));
}

/// Sets up analytics, opens and seeds the store, and reports the migration of an RN database
/// (data_migrated), as App.kt's launch effect does. A store that can't be opened stops the app
/// (see `store.rs`) after reporting a failed migration.
pub fn open_store(path: &Path) -> Arc<Store> {
    LAUNCHED.get_or_init(Instant::now);
    posthog::install(path.parent().unwrap_or(Path::new(".")));
    let opening = Instant::now();
    let store = match Store::open(path) {
        Ok(store) => Arc::new(store),
        Err(e) => {
            report_open_failure(path, &e, opening.elapsed().as_millis() as u64);
            posthog::flush();
            panic!("can't open {}: {e}", path.display());
        }
    };
    let seeded = store.seed();
    report_data_migration(&store, &seeded);
    if let Err(e) = seeded {
        posthog::flush();
        panic!("seeding the database: {e}");
    }
    // The device is the analytics identity (as on iOS).
    if posthog::enabled() {
        if let Ok(id) = store.device_id() {
            posthog::identify(&id);
        }
    }
    store
}

/// app_ready, once per process, after the first screen's first frame. Call it in the root
/// component on every render (it is a hook).
pub fn use_app_ready(store: Arc<Store>) {
    use_effect(move || {
        let startup_ms = process_uptime_ms()
            .or_else(|| LAUNCHED.get().map(|t| t.elapsed().as_millis() as u64))
            .unwrap_or(0);
        let device = DEVICE_READINESS.get().map(|f| f()).unwrap_or_default();
        APP_READY.report(startup_ms, &store, device);
    });
}
