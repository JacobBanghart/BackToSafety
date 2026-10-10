//! What's reported at launch (androidApp AppReady.kt, App.kt's launch effect).

use nijii_core::alerts::Properties;
use nijii_core::analytics::Analytics;
use nijii_core::startup::*;
use nijii_core::store::{DATABASE_NAME, Store, StoreError};
use rusqlite::Connection;
use serde_json::{Value, json};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, MutexGuard};

/// The analytics sink is process-wide, so tests that read it run one at a time.
static SINK_LOCK: Mutex<()> = Mutex::new(());

struct Sink {
    _serial: MutexGuard<'static, ()>,
    tracked: Arc<Mutex<Vec<(String, Properties)>>>,
}

impl Sink {
    fn new() -> Sink {
        let serial = SINK_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let tracked = Arc::new(Mutex::new(Vec::new()));
        let t = tracked.clone();
        Analytics::set_sink(move |name, props| {
            t.lock().unwrap().push((name.to_string(), props.clone()))
        });
        Sink {
            _serial: serial,
            tracked,
        }
    }

    fn events(&self) -> Vec<(String, Properties)> {
        self.tracked.lock().unwrap().clone()
    }
}

impl Drop for Sink {
    fn drop(&mut self) {
        Analytics::clear_sink();
    }
}

fn props(v: Value) -> Properties {
    v.as_object().unwrap().clone()
}

fn temp_dir() -> PathBuf {
    let dir = std::env::temp_dir().join(format!("nijii-{}", uuid::Uuid::new_v4()));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

fn rn_fixture_copy() -> PathBuf {
    let db = temp_dir().join(DATABASE_NAME);
    std::fs::copy(
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../spec/fixtures/upgrade/android/SQLite/nijii.db"),
        &db,
    )
    .unwrap();
    db
}

#[test]
fn process_uptime_comes_from_proc() {
    // pid (comm with spaces) state ppid ... starttime is the 22nd field.
    let stat = "4242 (my app (x)) S 1 4242 4242 0 -1 4194560 1 2 3 4 5 6 7 8 20 0 9 0 150000 123456 78 18446744073709551615";
    // started at tick 150000 = 1500 s after boot; the phone has been up 1502.5 s.
    assert_eq!(
        parse_process_uptime_ms(stat, "1502.50 9999.00\n", 100),
        Some(2500)
    );
    assert_eq!(parse_process_uptime_ms("garbage", "1.0 1.0", 100), None);
    assert_eq!(parse_process_uptime_ms(stat, "", 100), None);
    // This process is running, so /proc says it has been up for some time.
    if cfg!(target_os = "linux") {
        assert!(process_uptime_ms().is_some());
    }
}

#[test]
fn app_ready_carries_startup_then_readiness_then_the_device() {
    let p = app_ready_properties(
        812,
        props(json!({"has_profile": true, "places_count": 1})),
        props(json!({"notifications_enabled": true, "exact_alarms": false})),
    );
    assert_eq!(
        serde_json::to_string(&p).unwrap(),
        r#"{"startup_ms":812,"has_profile":true,"places_count":1,"notifications_enabled":true,"exact_alarms":false}"#
    );
}

#[test]
fn app_ready_is_reported_once_with_what_is_stored() {
    let sink = Sink::new();
    let store = Store::open_in_memory().unwrap();
    store
        .update_profile(&[
            ("name", Some("Ana")),
            ("medicalConditions", Some("Dementia")),
        ])
        .unwrap();
    let ready = AppReady::new();
    let device = props(json!({"notifications_enabled": true, "exact_alarms": true}));
    assert!(ready.report(900, &store, device.clone()));
    assert!(!ready.report(950, &store, device), "once per process");
    let events = sink.events();
    assert_eq!(events.len(), 1);
    assert_eq!(events[0].0, "app_ready");
    assert_eq!(
        events[0].1,
        props(json!({
            "startup_ms": 900, "has_profile": true, "has_photo": false, "has_medical": true,
            "profile_fields_filled": 1, "contacts_count": 0, "alert_contacts_count": 0,
            "places_count": 0, "notifications_enabled": true, "exact_alarms": true,
        }))
    );
}

#[test]
fn data_migrated_reports_what_came_through() {
    let sink = Sink::new();
    let store = Store::open(rn_fixture_copy()).unwrap();
    report_data_migration(&store, &Ok(()));
    let events = sink.events();
    assert_eq!(events.len(), 1);
    assert_eq!(events[0].0, "data_migrated");
    let mut p = events[0].1.clone();
    assert!(p.remove("duration_ms").is_some());
    assert_eq!(
        p,
        props(json!({
            "from_version": 1, "to_version": 2, "ok": true, "has_profile": true,
            "contacts_count": 1, "places_count": 1, "incidents_count": 1,
        }))
    );
}

#[test]
fn data_migrated_reports_a_failure_after_the_migration() {
    let sink = Sink::new();
    let store = Store::open(rn_fixture_copy()).unwrap();
    report_data_migration(&store, &Err(StoreError::UnknownProfileField("x".into())));
    let p = &sink.events()[0].1;
    assert_eq!(p["ok"], false);
    assert_eq!(p["from_version"], 1);
    assert_eq!(p["error"], "UnknownProfileField");
}

#[test]
fn nothing_is_reported_when_nothing_migrated() {
    let sink = Sink::new();
    let path = temp_dir().join(DATABASE_NAME);
    report_data_migration(&Store::open(&path).unwrap(), &Ok(()));
    report_data_migration(&Store::open_in_memory().unwrap(), &Ok(()));
    let rn = rn_fixture_copy();
    drop(Store::open(&rn).unwrap());
    report_data_migration(&Store::open(&rn).unwrap(), &Ok(())); // already migrated
    assert!(sink.events().is_empty());
}

#[test]
fn a_migration_that_cannot_open_is_reported_as_failed() {
    let sink = Sink::new();
    let path = rn_fixture_copy();
    // Damage the RN database so the migration can't copy its contacts.
    Connection::open(&path)
        .unwrap()
        .execute_batch("DROP TABLE contacts")
        .unwrap();
    let error = Store::open(&path).err().expect("the migration fails");
    report_open_failure(&path, &error, 7);
    let events = sink.events();
    assert_eq!(events.len(), 1);
    assert_eq!(
        events[0].1,
        props(
            json!({"from_version": 1, "to_version": 2, "ok": false, "duration_ms": 7, "error": "SqliteError"})
        )
    );
    // The failed migration left the data as it was (rolled back), still at version 1.
    let version: i64 = Connection::open(&path)
        .unwrap()
        .query_row("PRAGMA user_version", [], |r| r.get(0))
        .unwrap();
    assert_eq!(version, 1);
}

#[test]
fn an_open_failure_that_is_not_a_migration_reports_nothing() {
    let sink = Sink::new();
    let path = temp_dir().join(DATABASE_NAME);
    Connection::open(&path)
        .unwrap()
        .execute_batch("PRAGMA user_version = 3")
        .unwrap();
    let error = Store::open(&path).err().unwrap();
    report_open_failure(&path, &error, 1);
    assert!(sink.events().is_empty());
}

#[test]
fn lifecycle_events_follow_the_posthog_sdk() {
    let v = |version: &str, build: &str| AppVersion {
        version: version.into(),
        build: build.into(),
    };
    let current = v("1.3.3", "13");

    let first = lifecycle_events(None, &current);
    assert_eq!(
        first.iter().map(|e| e.0).collect::<Vec<_>>(),
        vec!["Application Installed", "Application Opened"]
    );
    assert_eq!(
        first[0].1,
        props(json!({"version": "1.3.3", "build": "13"}))
    );
    assert_eq!(
        first[1].1,
        props(json!({"version": "1.3.3", "build": "13", "from_background": false}))
    );

    let same = lifecycle_events(Some(&current), &current);
    assert_eq!(
        same.iter().map(|e| e.0).collect::<Vec<_>>(),
        vec!["Application Opened"]
    );

    let updated = lifecycle_events(Some(&v("1.3.2", "12")), &current);
    assert_eq!(
        updated.iter().map(|e| e.0).collect::<Vec<_>>(),
        vec!["Application Updated", "Application Opened"]
    );
    assert_eq!(
        updated[0].1,
        props(
            json!({"previous_version": "1.3.2", "previous_build": "12", "version": "1.3.3", "build": "13"})
        )
    );

    assert_eq!(AppVersion::parse(&current.serialize()), Some(current));
    assert_eq!(AppVersion::parse("nonsense"), None);
}
