//! The twin of kmp/shared's EmergencyAwayTest: the countdown alerts both apps deliver, in-app
//! on the emergency screen, scheduled as notifications while it isn't showing, and caught up
//! on returning. The apps' schedulers only deliver what EmergencyAway decides, so these are the
//! alert rules for both.

use nijii_core::alerts::{Properties, ScheduledAlert};
use nijii_core::analytics::Analytics;
use nijii_core::emergency::{ActiveEmergency, CountdownAlert, parse_instant_ms};
use nijii_core::emergency_away::{AlertScheduler, EmergencyAway};
use nijii_core::store::Store;
use serde_json::{Value, json};
use std::sync::{Arc, Mutex, MutexGuard};

#[derive(Default)]
struct FakeScheduler {
    scheduled: Mutex<Vec<ScheduledAlert>>,
}

impl AlertScheduler for FakeScheduler {
    fn schedule(&self, alerts: Vec<ScheduledAlert>) {
        *self.scheduled.lock().unwrap() = alerts;
    }
    fn cancel_all(&self) {
        self.scheduled.lock().unwrap().clear();
    }
}

/// The analytics sink is process-wide, so tests that read it run one at a time.
static SINK_LOCK: Mutex<()> = Mutex::new(());

struct Fixture {
    _serial: MutexGuard<'static, ()>,
    tracked: Arc<Mutex<Vec<(String, Properties)>>>,
    scheduler: Arc<FakeScheduler>,
    store: Arc<Store>,
    away: EmergencyAway,
    start: i64,
}

impl Fixture {
    fn new() -> Fixture {
        let serial = SINK_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let tracked = Arc::new(Mutex::new(Vec::new()));
        let sink = tracked.clone();
        Analytics::set_sink(move |name, props| {
            sink.lock().unwrap().push((name.to_string(), props.clone()))
        });
        let scheduler = Arc::new(FakeScheduler::default());
        let store = Arc::new(Store::open_in_memory().unwrap());
        Fixture {
            _serial: serial,
            tracked,
            scheduler: scheduler.clone(),
            away: EmergencyAway::new(store.clone(), scheduler),
            store,
            start: parse_instant_ms("2026-10-06T15:00:00Z").unwrap(),
        }
    }

    fn at(&self, minutes: i64, seconds: i64) -> i64 {
        self.start + (minutes * 60 + seconds) * 1000
    }

    fn start_emergency(&self) {
        self.store
            .save_active_emergency(&ActiveEmergency {
                started_at: "2026-10-06T15:00:00.000Z".into(),
                wearing: String::new(),
                checked_steps: vec![],
                incident_id: None,
            })
            .unwrap();
    }

    fn scheduled(&self) -> Vec<ScheduledAlert> {
        self.scheduler.scheduled.lock().unwrap().clone()
    }

    fn tracked(&self) -> Vec<(String, Properties)> {
        self.tracked.lock().unwrap().clone()
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        Analytics::clear_sink();
    }
}

fn props(v: Value) -> Properties {
    v.as_object().unwrap().clone()
}

fn alert(kind: CountdownAlert, at_ms: i64) -> ScheduledAlert {
    ScheduledAlert {
        alert: kind,
        fire_at_ms: at_ms,
    }
}

#[test]
fn leaving_schedules_what_is_still_to_come() {
    let f = Fixture::new();
    f.start_emergency();
    f.away.left(f.at(2, 0)).unwrap();
    assert_eq!(
        f.scheduled(),
        vec![
            alert(CountdownAlert::Warning, f.at(10, 1)),
            alert(CountdownAlert::Expired, f.at(15, 0))
        ]
    );
    f.away.shown(f.at(3, 0)).unwrap();
    f.away.left(f.at(12, 0)).unwrap();
    assert_eq!(
        f.scheduled(),
        vec![alert(CountdownAlert::Expired, f.at(15, 0))]
    );
}

#[test]
fn no_emergency_nothing_scheduled() {
    let f = Fixture::new();
    f.away.left(f.at(2, 0)).unwrap();
    assert!(f.scheduled().is_empty());
    assert_eq!(f.away.shown(f.at(20, 0)).unwrap(), None);
    assert!(f.tracked().is_empty());
}

#[test]
fn returning_cancels_and_catches_up() {
    let f = Fixture::new();
    f.start_emergency();
    f.away.in_app_alerts(301, 300, f.at(9, 59));
    f.away.left(f.at(10, 0)).unwrap();
    assert_eq!(
        f.away.shown(f.at(11, 0)).unwrap(),
        Some(CountdownAlert::Warning)
    );
    assert!(f.scheduled().is_empty());
    assert_eq!(
        f.tracked(),
        vec![
            (
                "emergency_resumed".to_string(),
                props(json!({"away_s": 61, "catch_up": "warning"}))
            ),
            (
                "countdown_alert".to_string(),
                props(json!({"kind": "warning", "delivery": "catch_up"}))
            ),
        ]
    );
    // Caught up once: going back and forth doesn't repeat it.
    f.away.left(f.at(11, 30)).unwrap();
    assert_eq!(f.away.shown(f.at(12, 0)).unwrap(), None);
}

#[test]
fn expiry_outranks_the_warning() {
    let f = Fixture::new();
    f.start_emergency();
    f.away.left(f.at(5, 0)).unwrap();
    assert_eq!(
        f.away.shown(f.at(40, 0)).unwrap(),
        Some(CountdownAlert::Expired)
    );
}

#[test]
fn time_away_starts_at_the_last_tick() {
    let f = Fixture::new();
    f.start_emergency();
    // The tick before the warning, then the screen leaves before the next one.
    assert!(f.away.in_app_alerts(300, 300, f.at(10, 0)).is_empty());
    f.away.left(f.at(10, 1)).unwrap();
    assert_eq!(
        f.scheduled(),
        vec![alert(CountdownAlert::Expired, f.at(15, 0))]
    );
    assert_eq!(
        f.away.shown(f.at(10, 5)).unwrap(),
        Some(CountdownAlert::Warning),
        "due between the last tick and leaving"
    );
}

#[test]
fn leaving_twice_keeps_the_first_time_away() {
    let f = Fixture::new();
    f.start_emergency();
    f.away.left(f.at(5, 0)).unwrap(); // to the info sheet
    f.away.left(f.at(11, 0)).unwrap(); // then the app to the background, from there
    assert_eq!(
        f.away.shown(f.at(12, 0)).unwrap(),
        Some(CountdownAlert::Warning)
    );
}

#[test]
fn in_app_alerts_are_reported() {
    let f = Fixture::new();
    assert_eq!(
        f.away.in_app_alerts(300, 299, f.at(10, 1)),
        vec![CountdownAlert::Warning]
    );
    assert_eq!(
        f.away.in_app_alerts(1, 0, f.at(15, 0)),
        vec![CountdownAlert::Expired]
    );
    assert!(f.away.in_app_alerts(200, 199, f.at(11, 41)).is_empty());
    let tracked = f.tracked();
    let kinds: Vec<_> = tracked
        .iter()
        .filter(|(n, _)| n == "countdown_alert")
        .map(|(_, p)| p["kind"].clone())
        .collect();
    assert_eq!(kinds, vec![json!("warning"), json!("expired")]);
    assert!(tracked.iter().all(|(_, p)| p["delivery"] == "in_app"));
}

#[test]
fn ending_cancels_everything() {
    let f = Fixture::new();
    f.start_emergency();
    f.away.left(f.at(2, 0)).unwrap();
    f.away.ended().unwrap();
    assert!(f.scheduled().is_empty());
    f.start_emergency();
    assert_eq!(
        f.away.shown(f.at(20, 0)).unwrap(),
        None,
        "the old time away is gone"
    );
}

#[test]
fn clearing_the_emergency_forgets_the_time_away() {
    let f = Fixture::new();
    f.start_emergency();
    f.away.left(f.at(2, 0)).unwrap();
    f.store.clear_active_emergency().unwrap();
    f.start_emergency();
    assert_eq!(f.away.shown(f.at(20, 0)).unwrap(), None);
}

#[test]
fn notification_permission_is_asked_once() {
    let f = Fixture::new();
    assert!(f.away.should_ask_for_notifications().unwrap());
    f.away.notifications_answered(false).unwrap();
    assert!(!f.away.should_ask_for_notifications().unwrap());
    assert_eq!(
        f.tracked(),
        vec![(
            "notifications_permission".to_string(),
            props(json!({"granted": false}))
        )]
    );
}
