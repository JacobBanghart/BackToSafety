//! The twin of kmp/shared's AnalyticsParityTest, for the Rust app: it sends the events
//! spec/analytics-events.json names, and names the same screens as the KMP app. The Kotlin
//! test also holds the iOS app to the list; that part doesn't apply here.

use nijii_core::analytics::AnalyticsEvent;
use regex::Regex;
use serde_json::Value;
use std::collections::BTreeSet;
use std::path::{Path, PathBuf};

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn spec_events() -> Vec<String> {
    let spec: Value = serde_json::from_str(
        &std::fs::read_to_string(root().join("spec/analytics-events.json")).unwrap(),
    )
    .unwrap();
    spec["events"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e.as_str().unwrap().to_string())
        .collect()
}

fn rust_sources(dir: &str) -> Vec<String> {
    fn walk(dir: &Path, out: &mut Vec<String>) {
        for entry in std::fs::read_dir(dir).unwrap().flatten() {
            let p = entry.path();
            if p.is_dir() {
                walk(&p, out);
            } else if p.extension().is_some_and(|e| e == "rs") {
                out.push(std::fs::read_to_string(&p).unwrap());
            }
        }
    }
    let mut out = Vec::new();
    walk(&root().join(dir), &mut out);
    out
}

/// The wire names of every `AnalyticsEvent::Variant` the Rust app's code mentions.
fn sent_events() -> BTreeSet<String> {
    let by_variant: std::collections::HashMap<String, &str> = AnalyticsEvent::ALL
        .iter()
        .map(|e| (format!("{e:?}"), e.wire_name()))
        .collect();
    let re = Regex::new(r"AnalyticsEvent::([A-Za-z0-9]+)").unwrap();
    ["rust/core/src", "rust/app/src"]
        .into_iter()
        .flat_map(rust_sources)
        .flat_map(|src| {
            re.captures_iter(&src)
                .map(|c| c[1].to_string())
                .collect::<Vec<_>>()
        })
        .filter_map(|variant| by_variant.get(&variant).map(|w| w.to_string()))
        .collect()
}

#[test]
fn generated_events_are_exactly_the_spec_list() {
    let wire: Vec<String> = AnalyticsEvent::ALL
        .iter()
        .map(|e| e.wire_name().to_string())
        .collect();
    assert_eq!(wire, spec_events());
    assert_eq!(
        AnalyticsEvent::Readout911Called.wire_name(),
        "readout_911_called"
    );
    assert_eq!(
        AnalyticsEvent::EmergencyStarted.wire_name(),
        "emergency_started"
    );
}

/// Every event is tracked somewhere in the Rust app or its core. Ignored until the screen
/// lanes have all landed; run it with `cargo test -p nijii-core --test analytics_parity -- --ignored`.
#[test]
#[ignore = "passes once every screen lane has landed"]
fn rust_app_sends_the_spec_events() {
    let events: BTreeSet<String> = spec_events().into_iter().collect();
    let sent = sent_events();
    let missing: Vec<_> = events.difference(&sent).collect();
    assert!(missing.is_empty(), "events nothing sends: {missing:?}");
}

/// Both apps name the same screens: "home" and "settings" are the only named ones.
/// Ignored until the home and settings lanes have landed.
#[test]
#[ignore = "passes once the home and settings lanes have landed"]
fn rust_app_names_the_same_screens() {
    let re = Regex::new(r#"Analytics::screen\(\s*"([a-z_]+)""#).unwrap();
    let screens: BTreeSet<String> = rust_sources("rust/app/src")
        .iter()
        .flat_map(|src| {
            re.captures_iter(src)
                .map(|c| c[1].to_string())
                .collect::<Vec<_>>()
        })
        .collect();
    assert_eq!(screens, ["home", "settings"].map(String::from).into());
}

/// The events the core and the app's startup send for every screen (EmergencyAway, app_ready,
/// data_migrated); the rest belong to the screens and are held to the spec by the ignored test.
#[test]
fn the_core_and_startup_send_their_events() {
    let sent = sent_events();
    for event in [
        "app_ready",
        "data_migrated",
        "countdown_alert",
        "emergency_resumed",
        "notifications_permission",
    ] {
        assert!(sent.contains(event), "{event} is not sent by the core or the app");
    }
}

#[test]
fn nothing_sends_an_event_outside_the_spec() {
    // The enum is generated from the spec, so a stray name can't compile; this checks the
    // source scan itself finds the events the core sends.
    let sent = sent_events();
    assert!(
        sent.contains("countdown_alert")
            && sent.contains("emergency_resumed")
            && sent.contains("notifications_permission")
    );
    let events: BTreeSet<String> = spec_events().into_iter().collect();
    assert!(sent.is_subset(&events));
}
