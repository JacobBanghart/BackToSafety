//! What the apps report at launch: app_ready (androidApp AppReady.kt), data_migrated
//! (App.kt's launch effect), and the app lifecycle events the PostHog SDK sends by itself
//! (`captureApplicationLifecycleEvents`). The decisions live here, so they can be tested off
//! the device; the app only calls them at the right moments.

use crate::alerts::Properties;
use crate::analytics::{Analytics, AnalyticsEvent};
use crate::store::{
    DATABASE_VERSION, Result, Store, StoreError, adopt_rn_database,
    data_migration_failed_properties,
};
use serde_json::json;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};

/// How long the process has been running, from /proc, as Android's
/// `SystemClock.uptimeMillis() - Process.getStartUptimeMillis()`. None where /proc is missing.
pub fn process_uptime_ms() -> Option<u64> {
    let stat = std::fs::read_to_string("/proc/self/stat").ok()?;
    let uptime = std::fs::read_to_string("/proc/uptime").ok()?;
    parse_process_uptime_ms(&stat, &uptime, 100)
}

/// `stat` is /proc/<pid>/stat (field 22 is the start time in clock ticks since boot), `uptime`
/// is /proc/uptime (seconds since boot); `clock_ticks_per_second` is `sysconf(_SC_CLK_TCK)`,
/// 100 on Android and Linux.
pub fn parse_process_uptime_ms(
    stat: &str,
    uptime: &str,
    clock_ticks_per_second: u64,
) -> Option<u64> {
    // The command name (field 2) is in parentheses and may hold spaces: count fields after it.
    let after_comm = &stat[stat.rfind(')')? + 1..];
    // Fields from 3 (state): the start time is field 22, the 20th of these.
    let start_ticks: u64 = after_comm.split_whitespace().nth(19)?.parse().ok()?;
    let uptime_s: f64 = uptime.split_whitespace().next()?.parse().ok()?;
    let start_ms = start_ticks * 1000 / clock_ticks_per_second;
    Some(((uptime_s * 1000.0) as u64).saturating_sub(start_ms))
}

/// app_ready's properties: the cold start time, then how ready the app is for an emergency
/// (`readiness`), then what only the device knows (`device`: notifications_enabled, exact_alarms).
pub fn app_ready_properties(
    startup_ms: u64,
    readiness: Properties,
    device: Properties,
) -> Properties {
    let mut p = Properties::new();
    p.insert("startup_ms".into(), json!(startup_ms));
    p.extend(readiness);
    p.extend(device);
    p
}

/// app_ready, once per process: how long a cold start took, from the process starting to the
/// first frame of the first screen (home or welcome), the field measure of load time.
#[derive(Default, uniffi::Object)]
pub struct AppReady {
    reported: AtomicBool,
}

impl AppReady {
    pub const fn new() -> AppReady {
        AppReady {
            reported: AtomicBool::new(false),
        }
    }

    /// Reports app_ready unless it already was. The readiness is gathered after the time is
    /// taken; if it can't be, app_ready goes without it. Returns whether it reported.
    pub fn report(&self, startup_ms: u64, store: &Store, device: Properties) -> bool {
        if self.reported.swap(true, Ordering::SeqCst) {
            return false;
        }
        let readiness = store.readiness_properties().unwrap_or_default();
        Analytics::track(
            AnalyticsEvent::AppReady,
            app_ready_properties(startup_ms, readiness, device),
        );
        true
    }
}

/// After the first query migrated a database the RN app left, reports data_migrated: what came
/// through when `seeded` succeeded, or why it failed.
pub fn report_data_migration(store: &Store, seeded: &Result<()>) {
    let Some(from) = store.migrated_from() else {
        return;
    };
    let duration_ms = store.migration_duration_ms().unwrap_or(0);
    let properties = match seeded {
        Ok(()) => store
            .data_migrated_properties(from, duration_ms)
            .unwrap_or_else(|e| {
                data_migration_failed_properties(from, duration_ms, Some(e.type_name()))
            }),
        Err(e) => data_migration_failed_properties(from, duration_ms, Some(e.type_name())),
    };
    Analytics::track(AnalyticsEvent::DataMigrated, properties);
}

/// Seeds the store (the first query, which is where an older database has migrated before),
/// then reports data_migrated for it. What the apps call once at launch.
pub fn seed_and_report_migration(store: &Store) -> Result<()> {
    let seeded = store.seed();
    report_data_migration(store, &seeded);
    seeded
}

/// `Store::open` failed at `path` after `duration_ms`. If it was migrating an older database
/// (the file is left at the older schema version), reports data_migrated as failed.
pub fn report_open_failure(path: &Path, error: &StoreError, duration_ms: u64) {
    if let Some(from) = adopt_rn_database(path).filter(|v| (1..DATABASE_VERSION).contains(v)) {
        Analytics::track(
            AnalyticsEvent::DataMigrated,
            data_migration_failed_properties(from, duration_ms, Some(error.type_name())),
        );
    }
}

/// The app version and build number, as the PostHog SDK reports them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AppVersion {
    pub version: String,
    pub build: String,
}

impl AppVersion {
    /// "version\nbuild", the form the last-seen version is kept in.
    pub fn serialize(&self) -> String {
        format!("{}\n{}", self.version, self.build)
    }

    pub fn parse(stored: &str) -> Option<AppVersion> {
        let (version, build) = stored.split_once('\n')?;
        Some(AppVersion {
            version: version.into(),
            build: build.trim().into(),
        })
    }
}

/// The events the PostHog SDK sends on launch when `captureApplicationLifecycleEvents` is on:
/// "Application Installed" on the first launch, "Application Updated" when the version or build
/// changed, then "Application Opened" (a cold start is never from the background).
pub fn lifecycle_events(
    previous: Option<&AppVersion>,
    current: &AppVersion,
) -> Vec<(&'static str, Properties)> {
    let mut events = Vec::new();
    let props = |pairs: &[(&str, &str)]| -> Properties {
        pairs
            .iter()
            .map(|(k, v)| (k.to_string(), json!(v)))
            .collect()
    };
    match previous {
        None => events.push((
            "Application Installed",
            props(&[("version", &current.version), ("build", &current.build)]),
        )),
        Some(prev) if prev != current => events.push((
            "Application Updated",
            props(&[
                ("previous_version", &prev.version),
                ("previous_build", &prev.build),
                ("version", &current.version),
                ("build", &current.build),
            ]),
        )),
        Some(_) => {}
    }
    let mut opened = props(&[("version", &current.version), ("build", &current.build)]);
    opened.insert("from_background".into(), json!(false));
    events.push(("Application Opened", opened));
    events
}
