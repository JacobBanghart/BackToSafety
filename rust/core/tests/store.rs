//! The twin of kmp/shared's StoreTest.

use nijii_core::emergency::ActiveEmergency;
use nijii_core::store::{ContactEntity, DATABASE_NAME, DestinationEntity, Store};
use serde_json::{Value, json};
use std::path::{Path, PathBuf};

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn fresh_store() -> Store {
    Store::open_in_memory().unwrap()
}

fn temp_db() -> PathBuf {
    let dir = std::env::temp_dir().join(format!("nijii-{}", uuid::Uuid::new_v4()));
    std::fs::create_dir_all(&dir).unwrap();
    dir.join(DATABASE_NAME)
}

fn strings(items: &[&str]) -> Vec<String> {
    items.iter().map(|s| s.to_string()).collect()
}

#[test]
fn seeds_the_same_rows_as_the_rn_app() {
    let seed: Value = serde_json::from_str(&std::fs::read_to_string(root().join("spec/seed.json")).unwrap()).unwrap();
    let steps: Vec<&str> = seed["onboardingSteps"].as_array().unwrap().iter().map(|s| s.as_str().unwrap()).collect();
    assert_eq!(steps, Store::ONBOARDING_STEPS);
    let checks: Vec<(&str, &str)> = seed["safetyChecks"]
        .as_array()
        .unwrap()
        .iter()
        .map(|c| (c["category"].as_str().unwrap(), c["item_key"].as_str().unwrap()))
        .collect();
    assert_eq!(checks, Store::SAFETY_CHECKS);
}

#[test]
fn seeding_inserts_the_rows_once() {
    let store = fresh_store();
    store.seed().unwrap();
    store.seed().unwrap();
    assert_eq!(store.onboarding_steps().unwrap().len(), 6);
    let checks = store.safety_checks().unwrap();
    assert_eq!(checks.len(), 12);
    assert!(checks.iter().all(|c| !c.completed));
}

#[test]
fn onboarding_completes_step_by_step() {
    let store = fresh_store();
    store.seed().unwrap();
    store.seed().unwrap(); // every launch seeds; the second time inserts nothing
    assert!(!store.is_onboarded().unwrap());
    for step in &Store::ONBOARDING_STEPS[..5] {
        store.complete_step(step).unwrap();
    }
    assert!(!store.is_onboarded().unwrap());
    store.complete_step("complete").unwrap();
    assert!(store.is_onboarded().unwrap());
}

#[test]
fn profile_saves_merge() {
    let store = fresh_store();
    store
        .save_profile(|p| nijii_core::store::ProfileEntity {
            name: "Margaret Smith".into(),
            nickname: Some("Maggie".into()),
            ..p
        })
        .unwrap();
    store
        .save_profile(|p| nijii_core::store::ProfileEntity {
            height: Some("5'6\"".into()),
            ..p
        })
        .unwrap();
    let profile = store.profile().unwrap().unwrap();
    assert_eq!(profile.name, "Margaret Smith");
    assert_eq!(profile.nickname.as_deref(), Some("Maggie"));
    assert_eq!(profile.height.as_deref(), Some("5'6\""));
    let entity = store.profile_entity().unwrap().unwrap();
    assert!(entity.created_at.is_some() && entity.updated_at.is_some());
}

#[test]
fn active_emergency_round_trips_in_the_rn_format() {
    let store = fresh_store();
    let e = ActiveEmergency {
        started_at: "2026-10-06T16:00:00.000Z".into(),
        wearing: "Blue jacket".into(),
        checked_steps: strings(&["neighbors"]),
        incident_id: Some(3),
    };
    store.save_active_emergency(&e).unwrap();
    assert_eq!(
        store.setting(Store::ACTIVE_EMERGENCY).unwrap().as_deref(),
        Some(r#"{"startedAt":"2026-10-06T16:00:00.000Z","wearing":"Blue jacket","checkedSteps":["neighbors"],"isActive":true,"incidentId":3}"#)
    );
    assert_eq!(store.active_emergency().unwrap(), Some(e));
    store.clear_active_emergency().unwrap();
    assert_eq!(store.setting(Store::ACTIVE_EMERGENCY).unwrap().as_deref(), Some(""));
    assert_eq!(store.active_emergency().unwrap(), None);
}

#[test]
fn incidents_record_outcomes() {
    let store = fresh_store();
    let id = store.create_incident("2026-10-06T16:00:00.000Z").unwrap();
    store
        .record_incident(Some(id), "2026-10-06T16:00:00.000Z", &strings(&["call_911"]), "", Some("911_called"), None)
        .unwrap();
    store
        .record_incident(
            Some(id),
            "2026-10-06T16:00:00.000Z",
            &strings(&["call_911", "neighbors"]),
            "Blue jacket",
            Some("found"),
            Some("2026-10-06T16:10:00.000Z"),
        )
        .unwrap();
    let incidents = store.incidents().unwrap();
    assert_eq!(incidents.len(), 1);
    let incident = &incidents[0];
    assert_eq!(incident.id, id);
    assert_eq!(incident.outcome.as_deref(), Some("found"));
    assert_eq!(incident.ended_at.as_deref(), Some("2026-10-06T16:10:00.000Z"));
    assert_eq!(incident.areas_checked.as_deref(), Some(r#"["call_911","neighbors"]"#));
    assert_eq!(incident.wearing.as_deref(), Some("Blue jacket"));
    assert!(incident.created_at.is_some(), "the row keeps its created_at");

    // An emergency from before incidents were recorded gets its row on first update.
    let created = store
        .record_incident(None, "2026-10-07T09:00:00.000Z", &[], "", Some("911_called"), None)
        .unwrap();
    let all = store.incidents().unwrap();
    assert_eq!(
        all.iter().find(|i| i.id == created).unwrap().outcome.as_deref(),
        Some("911_called")
    );
    assert_eq!(all[0].id, created, "newest first");
}

#[test]
fn delete_account_empties_everything_and_restarts_onboarding() {
    let store = fresh_store();
    store.seed().unwrap();
    for step in Store::ONBOARDING_STEPS {
        store.complete_step(step).unwrap();
    }
    store.update_profile(&[("name", Some("Margaret Smith"))]).unwrap();
    store.add_contact(ContactEntity::new("John Smith", "5551234567"), None).unwrap();
    store.put_setting(Store::THEME_PREFERENCE, "dark").unwrap();
    assert!(store.is_onboarded().unwrap());

    store.clear_all_data().unwrap();
    assert_eq!(store.profile().unwrap(), None);
    assert!(store.contacts().unwrap().is_empty());
    assert_eq!(store.setting(Store::THEME_PREFERENCE).unwrap(), None);
    assert!(!store.is_onboarded().unwrap());
}

#[test]
fn readiness_counts_what_is_stored() {
    let store = fresh_store();
    let props = |v: Value| v.as_object().unwrap().clone();
    assert_eq!(
        store.readiness_properties().unwrap(),
        props(json!({
            "has_profile": false, "has_photo": false, "has_medical": false, "profile_fields_filled": 0,
            "contacts_count": 0, "alert_contacts_count": 0, "places_count": 0,
        }))
    );
    store
        .update_profile(&[
            ("name", Some("Margaret Smith")),
            ("medicalConditions", Some("Dementia")),
            ("likes", Some("Gardening")),
        ])
        .unwrap();
    store.add_contact(ContactEntity::new("Ana", "1"), None).unwrap();
    store
        .add_contact(
            ContactEntity {
                notify_on_emergency: false,
                ..ContactEntity::new("Bo", "2")
            },
            None,
        )
        .unwrap();
    store.add_destination(DestinationEntity::new("Riverside Park"), None).unwrap();
    assert_eq!(
        store.readiness_properties().unwrap(),
        props(json!({
            "has_profile": true, "has_photo": false, "has_medical": true, "profile_fields_filled": 2,
            "contacts_count": 2, "alert_contacts_count": 1, "places_count": 1,
        }))
    );
}

#[test]
fn device_id_is_created_once_and_kept() {
    let path = temp_db();
    let first = Store::open(&path).unwrap().device_id().unwrap();
    let uuid_v4 = regex::Regex::new("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").unwrap();
    assert!(uuid_v4.is_match(&first), "{first}");
    assert_eq!(Store::open(&path).unwrap().device_id().unwrap(), first);
}

#[test]
fn contacts_add_edit_reorder_delete() {
    let store = fresh_store();
    let ana = store
        .add_contact(
            ContactEntity {
                relationship: Some("Neighbor".into()),
                notes: Some("Has a key".into()),
                ..ContactEntity::new("Ana", "1")
            },
            None,
        )
        .unwrap();
    let bo = store.add_contact(ContactEntity::new("Bo", "2"), None).unwrap();
    let order = |s: &Store| s.contacts().unwrap().into_iter().map(|c| (c.name, c.sort_order)).collect::<Vec<_>>();
    assert_eq!(order(&store), vec![("Ana".to_string(), 0), ("Bo".to_string(), 1)]);
    assert!(store.contacts().unwrap().iter().all(|c| c.created_at.is_some()));

    // Clearing fields in the edit form clears them (F-35).
    let edited = ContactEntity {
        name: "Ana Lopez".into(),
        relationship: None,
        notes: None,
        ..store.contacts().unwrap().into_iter().find(|c| c.id == ana).unwrap()
    };
    store.update_contact(&edited).unwrap();
    let saved = store.contacts().unwrap().into_iter().find(|c| c.id == ana).unwrap();
    assert_eq!(saved.name, "Ana Lopez");
    assert_eq!(saved.relationship, None);
    assert_eq!(saved.notes, None);

    let mut reversed = store.contacts().unwrap();
    reversed.reverse();
    store.reorder_contacts(&reversed).unwrap();
    let names = |s: &Store| s.contacts().unwrap().into_iter().map(|c| c.name).collect::<Vec<_>>();
    assert_eq!(names(&store), vec!["Bo", "Ana Lopez"]);

    store.delete_contact(bo).unwrap();
    assert_eq!(names(&store), vec!["Ana Lopez"]);
}

#[test]
fn emergency_contacts_are_those_who_get_the_alert() {
    let store = fresh_store();
    store.add_contact(ContactEntity::new("Ana", "1"), None).unwrap();
    store
        .add_contact(ContactEntity { notify_on_emergency: false, ..ContactEntity::new("Bo", "2") }, None)
        .unwrap();
    let names: Vec<_> = store.emergency_contacts().unwrap().into_iter().map(|c| c.name).collect();
    assert_eq!(names, vec!["Ana"]);
}

#[test]
fn destinations_add_edit_reorder_delete() {
    let store = fresh_store();
    let park = store
        .add_destination(
            DestinationEntity {
                reason: Some("Walks".into()),
                category: Some("walking_route".into()),
                ..DestinationEntity::new("Park")
            },
            None,
        )
        .unwrap();
    store.add_destination(DestinationEntity::new("Church"), None).unwrap();
    let cleared = DestinationEntity {
        reason: None,
        ..store.destinations().unwrap().into_iter().find(|d| d.id == park).unwrap()
    };
    store.update_destination(&cleared).unwrap();
    assert_eq!(store.destinations().unwrap().into_iter().find(|d| d.id == park).unwrap().reason, None);
    let mut reversed = store.destinations().unwrap();
    reversed.reverse();
    store.reorder_destinations(&reversed).unwrap();
    let names = |s: &Store| s.destinations().unwrap().into_iter().map(|d| d.name).collect::<Vec<_>>();
    assert_eq!(names(&store), vec!["Church", "Park"]);
    store.delete_destination(park).unwrap();
    assert_eq!(names(&store), vec!["Church"]);
}

#[test]
fn update_profile_sets_given_fields_and_keeps_the_rest() {
    let store = fresh_store();
    store.update_profile(&[("name", Some("Margaret Smith")), ("nickname", Some("Maggie"))]).unwrap();
    store.update_profile(&[("height", Some("5'6\"")), ("nickname", None)]).unwrap();
    let profile = store.profile().unwrap().unwrap();
    assert_eq!(profile.name, "Margaret Smith");
    assert_eq!(profile.nickname, None);
    assert_eq!(profile.height.as_deref(), Some("5'6\""));
}

#[test]
fn update_profile_rejects_an_unknown_field_without_writing() {
    let store = fresh_store();
    assert!(store.update_profile(&[("name", Some("Ana")), ("shoeSize", Some("7"))]).is_err());
    assert_eq!(store.profile().unwrap(), None);
}

#[test]
fn settings_round_trip_and_replace() {
    let store = fresh_store();
    assert_eq!(store.setting(Store::LANGUAGE_PREFERENCE).unwrap(), None);
    store.put_setting(Store::LANGUAGE_PREFERENCE, "es").unwrap();
    store.put_setting(Store::LANGUAGE_PREFERENCE, "en").unwrap();
    assert_eq!(store.setting(Store::LANGUAGE_PREFERENCE).unwrap().as_deref(), Some("en"));
}
