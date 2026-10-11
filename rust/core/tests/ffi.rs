//! The UniFFI surface (src/ffi.rs), called the way the generated bindings call it: same
//! functions, owned arguments, `PropValue` bags. The rules behind them are tested elsewhere;
//! these check the shapes crossing the boundary.

use nijii_core::alerts::ScheduledAlert;
use nijii_core::analytics::AnalyticsEvent;
use nijii_core::emergency::{ActiveEmergency, CountdownAlert};
use nijii_core::emergency_away::{AlertScheduler, EmergencyAway};
use nijii_core::ffi::*;
use nijii_core::i18n::Translations;
use nijii_core::profile::Profile;
use nijii_core::store::{ContactEntity, ProfileEntity, Store};
use std::collections::HashMap;
use std::sync::{Arc, Mutex, MutexGuard};

/// The analytics sink and the clock are process-wide, so tests that touch them run one at a time.
static SERIAL: Mutex<()> = Mutex::new(());

fn serial() -> MutexGuard<'static, ()> {
    SERIAL.lock().unwrap_or_else(|e| e.into_inner())
}

fn text(s: &str) -> PropValue {
    PropValue::Text { value: s.into() }
}

#[derive(Default)]
struct Recorder(Mutex<Vec<(String, HashMap<String, PropValue>)>>);

impl AnalyticsSink for Recorder {
    fn on_event(&self, name: String, properties: HashMap<String, PropValue>) {
        self.0.lock().unwrap().push((name, properties));
    }
}

#[derive(Default)]
struct Scheduler(Mutex<Vec<ScheduledAlert>>);

impl AlertScheduler for Scheduler {
    fn schedule(&self, alerts: Vec<ScheduledAlert>) {
        *self.0.lock().unwrap() = alerts;
    }
    fn cancel_all(&self) {
        self.0.lock().unwrap().clear();
    }
}

#[test]
fn the_sink_gets_events_by_wire_name_and_screens_as_dollar_screen() {
    let _serial = serial();
    let rec = Arc::new(Recorder::default());
    analytics_set_sink(Some(rec.clone()));
    analytics_track(
        AnalyticsEvent::SettingsThemeChanged,
        HashMap::from([("theme".to_string(), text("dark"))]),
    );
    analytics_screen("/emergency".into(), HashMap::new());
    analytics_set_sink(None);
    analytics_track(AnalyticsEvent::AppReady, HashMap::new());

    let seen = rec.0.lock().unwrap();
    assert_eq!(seen.len(), 2, "nothing after the sink is cleared");
    assert_eq!(seen[0].0, "settings_theme_changed");
    assert_eq!(seen[0].1["theme"], text("dark"));
    assert_eq!(seen[1].0, analytics_screen_event_name());
    assert_eq!(seen[1].0, "$screen");
    assert_eq!(seen[1].1["$screen_name"], text("/emergency"));
    assert_eq!(
        analytics_event_wire_name(AnalyticsEvent::AppReady),
        "app_ready"
    );
}

#[test]
fn property_values_keep_their_types_both_ways() {
    let p = emergency_resumed_properties(0, 90_000, Some(CountdownAlert::Warning));
    assert_eq!(p["away_s"], PropValue::Integer { value: 90 });
    assert_eq!(p["catch_up"], text("warning"));
    assert_eq!(
        emergency_resumed_properties(0, 1000, None)["catch_up"],
        PropValue::Null
    );
    let p = emergency_ended_properties(0, 61_000, 3);
    assert_eq!(p["checked_count"], PropValue::Integer { value: 3 });
    let ready = readiness_properties(None, 0, 0, 0);
    assert_eq!(ready["has_profile"], PropValue::Bool { value: false });
}

#[test]
fn translations_interpolate_pluralize_and_fall_back() {
    let files = HashMap::from([
        (
            "en".to_string(),
            HashMap::from([(
                "common".to_string(),
                r#"{"hi":"Hello {{name}}","n_one":"{{count}} item","n_other":"{{count}} items","only_en":"E"}"#
                    .to_string(),
            )]),
        ),
        (
            "es".to_string(),
            HashMap::from([("common".to_string(), r#"{"hi":"Hola {{name}}"}"#.to_string())]),
        ),
    ]);
    let tr = Translations::ffi_from_json(files);
    let es = tr.clone().ffi_translator("es".into(), "common".into());
    assert_eq!(es.ffi_locale(), "es");
    assert_eq!(
        es.ffi_tv("hi".into(), HashMap::from([("name".into(), text("Ana"))])),
        "Hola Ana"
    );
    assert_eq!(es.ffi_t("only_en".into()), "E", "English fallback");
    assert_eq!(es.ffi_t("nope".into()), "nope", "a missing key is itself");
    let en = tr.ffi_translator("en".into(), "common".into());
    let count = |n| HashMap::from([("count".to_string(), PropValue::Integer { value: n })]);
    assert_eq!(en.ffi_tv("n".into(), count(1)), "1 item");
    assert_eq!(en.ffi_tv("n".into(), count(2)), "2 items");
    assert_eq!(
        en.ffi_tv(
            "n".into(),
            HashMap::from([("count".into(), PropValue::Decimal { value: 3.0 })])
        ),
        "3 items",
        "a whole decimal prints as an integer, as Kotlin's Double did"
    );
}

#[test]
fn dates_cross_as_year_month_day() {
    let d = parse_dob("2/9/1990".into()).unwrap();
    assert_eq!(
        d,
        Date {
            year: 1990,
            month: 2,
            day: 9
        }
    );
    assert_eq!(format_dob(d), "02/09/1990");
    assert_eq!(parse_dob("02/30/1990".into()), None);
    let today = Date {
        year: 2026,
        month: 2,
        day: 8,
    };
    assert_eq!(age_on("02/09/1990".into(), today), Some(35));
    assert_eq!(
        age_on(
            "02/09/1990".into(),
            Date {
                month: 2,
                day: 9,
                ..today
            }
        ),
        Some(36)
    );
    assert_eq!(
        age_on("02/09/1990".into(), Date { month: 13, ..today }),
        None
    );
}

#[test]
fn the_store_works_through_the_ffi_shapes() {
    let store = Store::ffi_open_in_memory().unwrap();
    store.ffi_seed().unwrap();
    assert!(!store.ffi_is_onboarded().unwrap());
    assert_eq!(store.ffi_onboarding_steps().unwrap().len(), 6);

    // The first save creates the row, stamped; a later partial save keeps the rest.
    store
        .ffi_save_profile_entity(ProfileEntity {
            name: "Ana".into(),
            ..ProfileEntity::default()
        })
        .unwrap();
    let first = store.ffi_profile_entity().unwrap().unwrap();
    assert!(first.created_at.is_some() && first.updated_at.is_some());
    store
        .ffi_update_profile(HashMap::from([
            ("nickname".to_string(), Some("Nan".to_string())),
            ("height".to_string(), None),
        ]))
        .unwrap();
    let p: Profile = store.ffi_profile().unwrap().unwrap();
    assert_eq!(
        (p.name.as_str(), p.nickname.as_deref()),
        ("Ana", Some("Nan"))
    );
    let bad = store.ffi_update_profile(HashMap::from([("nope".to_string(), None)]));
    assert!(matches!(bad, Err(StoreFailure::UnknownProfileField { field }) if field == "nope"));

    let id = store
        .ffi_add_contact(ContactEntity::new("Bo", "5551234567"), None)
        .unwrap();
    let contacts = store.ffi_contacts().unwrap();
    assert_eq!((contacts.len(), contacts[0].id), (1, id));
    assert_eq!(store.ffi_emergency_contacts().unwrap().len(), 1);

    let keys = setting_keys();
    store
        .ffi_put_setting(keys.theme_preference.clone(), "dark".into())
        .unwrap();
    assert_eq!(
        store.ffi_setting(keys.theme_preference).unwrap().as_deref(),
        Some("dark")
    );
    let ready = store.ffi_readiness_properties().unwrap();
    assert_eq!(ready["contacts_count"], PropValue::Integer { value: 1 });
}

#[test]
fn emergency_away_runs_over_the_foreign_scheduler() {
    let _serial = serial();
    let store = Store::ffi_open_in_memory().unwrap();
    let scheduler = Arc::new(Scheduler::default());
    let away = EmergencyAway::ffi_new(store.clone(), scheduler.clone());
    let started = "2026-10-06T16:00:00Z";
    store
        .ffi_save_active_emergency(ActiveEmergency {
            started_at: started.into(),
            wearing: String::new(),
            checked_steps: vec![],
            incident_id: None,
        })
        .unwrap();
    let started_ms = active_emergency_started_at_ms(
        parse_active_emergency(store.ffi_setting("active_emergency".into()).unwrap()).unwrap(),
    )
    .unwrap();
    away.ffi_left(started_ms + 60_000).unwrap();
    assert_eq!(
        scheduler.0.lock().unwrap().len(),
        2,
        "both alerts still to come"
    );
    let caught = away.ffi_shown(started_ms + 15 * 60_000 + 1000).unwrap();
    assert_eq!(caught, Some(CountdownAlert::Expired));
    assert!(scheduler.0.lock().unwrap().is_empty());
}

#[test]
fn the_readout_takes_a_date_and_a_translator() {
    let tr = Translations::ffi_from_json(HashMap::from([(
        "en".to_string(),
        HashMap::from([(
            "readout".to_string(),
            r#"{"script":{"opening":"Hi.","name":"Name {{name}}.","age":"Age {{age}}.","lastSeenUnknown":"?","locationUnknown":"??","silverAlert":"S"}}"#.to_string(),
        )]),
    )]));
    let t = tr.ffi_translator("en".into(), "readout".into());
    let input = ReadoutInput {
        profile: Profile {
            name: "Ana".into(),
            date_of_birth: Some("02/09/1990".into()),
            ..Profile::default()
        },
        last_seen_time: None,
        today: Date {
            year: 2026,
            month: 10,
            day: 1,
        },
    };
    assert_eq!(build_script(input, t), "Hi. Name Ana. Age 36. ? ?? S");
}
