//! The twin of kmp/shared's StoreTest.

use nijii_core::emergency::ActiveEmergency;
use nijii_core::profile::ProfileField;
use nijii_core::store::{ContactEntity, DATABASE_NAME, DestinationEntity, Store, StoreError};
use serde_json::{Value, json};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("..")
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
    let seed: Value =
        serde_json::from_str(&std::fs::read_to_string(root().join("spec/seed.json")).unwrap())
            .unwrap();
    let steps: Vec<&str> = seed["onboardingSteps"]
        .as_array()
        .unwrap()
        .iter()
        .map(|s| s.as_str().unwrap())
        .collect();
    assert_eq!(steps, Store::ONBOARDING_STEPS);
    let checks: Vec<(&str, &str)> = seed["safetyChecks"]
        .as_array()
        .unwrap()
        .iter()
        .map(|c| {
            (
                c["category"].as_str().unwrap(),
                c["item_key"].as_str().unwrap(),
            )
        })
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

/// A launch that finds every seed row writes nothing (no sqlite_sequence bump, so no commit
/// waiting on the disk); one that finds a row missing puts back just that row.
#[test]
fn seeding_writes_only_what_is_missing() {
    let path = temp_db();
    let store = Store::open(&path).unwrap();
    store.seed().unwrap();
    store.complete_step("welcome").unwrap();
    let sql = |q: &str| -> i64 {
        rusqlite::Connection::open(&path)
            .unwrap()
            .query_row(q, [], |r| r.get(0))
            .unwrap()
    };
    let sequence = "SELECT seq FROM sqlite_sequence WHERE name = 'safety_checks'";
    assert_eq!(sql(sequence), 12);

    store.seed().unwrap();
    assert_eq!(sql(sequence), 12, "a warm launch doesn't touch the table");

    let delete = |q: &str| {
        rusqlite::Connection::open(&path)
            .unwrap()
            .execute(q, [])
            .unwrap()
    };
    delete("DELETE FROM onboarding WHERE step = 'complete'");
    delete("DELETE FROM safety_checks WHERE item_key = 'door_chimes'");
    store.seed().unwrap();
    assert_eq!(store.onboarding_steps().unwrap().len(), 6);
    assert_eq!(store.safety_checks().unwrap().len(), 12);
    let welcome = store
        .onboarding_steps()
        .unwrap()
        .into_iter()
        .find(|s| s.step == "welcome")
        .unwrap();
    assert!(
        welcome.completed,
        "rows that were there are left as they were"
    );
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
        Some(
            r#"{"startedAt":"2026-10-06T16:00:00.000Z","wearing":"Blue jacket","checkedSteps":["neighbors"],"isActive":true,"incidentId":3}"#
        )
    );
    assert_eq!(store.active_emergency().unwrap(), Some(e));
    store.clear_active_emergency().unwrap();
    assert_eq!(
        store.setting(Store::ACTIVE_EMERGENCY).unwrap().as_deref(),
        Some("")
    );
    assert_eq!(store.active_emergency().unwrap(), None);
}

#[test]
fn incidents_record_outcomes() {
    let store = fresh_store();
    let id = store.create_incident("2026-10-06T16:00:00.000Z").unwrap();
    store
        .record_incident(
            Some(id),
            "2026-10-06T16:00:00.000Z",
            &strings(&["call_911"]),
            "",
            Some("911_called"),
            None,
        )
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
    assert_eq!(
        incident.ended_at.as_deref(),
        Some("2026-10-06T16:10:00.000Z")
    );
    assert_eq!(
        incident.areas_checked.as_deref(),
        Some(r#"["call_911","neighbors"]"#)
    );
    assert_eq!(incident.wearing.as_deref(), Some("Blue jacket"));
    assert!(
        incident.created_at.is_some(),
        "the row keeps its created_at"
    );

    // An emergency from before incidents were recorded gets its row on first update.
    let created = store
        .record_incident(
            None,
            "2026-10-07T09:00:00.000Z",
            &[],
            "",
            Some("911_called"),
            None,
        )
        .unwrap();
    let all = store.incidents().unwrap();
    assert_eq!(
        all.iter()
            .find(|i| i.id == created)
            .unwrap()
            .outcome
            .as_deref(),
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
    store
        .update_profile(&[(ProfileField::FullName, Some("Margaret Smith".into()))])
        .unwrap();
    store
        .add_contact(ContactEntity::new("John Smith", "5551234567"), None)
        .unwrap();
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
            (ProfileField::FullName, Some("Margaret Smith".into())),
            (ProfileField::MedicalConditions, Some("Dementia".into())),
            (ProfileField::Likes, Some("Gardening".into())),
        ])
        .unwrap();
    store
        .add_contact(ContactEntity::new("Ana", "1"), None)
        .unwrap();
    store
        .add_contact(
            ContactEntity {
                notify_on_emergency: false,
                ..ContactEntity::new("Bo", "2")
            },
            None,
        )
        .unwrap();
    store
        .add_destination(DestinationEntity::new("Riverside Park"), None)
        .unwrap();
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
    let uuid_v4 =
        regex::Regex::new("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
            .unwrap();
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
    let bo = store
        .add_contact(ContactEntity::new("Bo", "2"), None)
        .unwrap();
    let order = |s: &Store| {
        s.contacts()
            .unwrap()
            .into_iter()
            .map(|c| (c.name, c.sort_order))
            .collect::<Vec<_>>()
    };
    assert_eq!(
        order(&store),
        vec![("Ana".to_string(), 0), ("Bo".to_string(), 1)]
    );
    assert!(
        store
            .contacts()
            .unwrap()
            .iter()
            .all(|c| c.created_at.is_some())
    );

    // Clearing fields in the edit form clears them (F-35).
    let edited = ContactEntity {
        name: "Ana Lopez".into(),
        relationship: None,
        notes: None,
        ..store
            .contacts()
            .unwrap()
            .into_iter()
            .find(|c| c.id == ana)
            .unwrap()
    };
    store.update_contact(&edited).unwrap();
    let saved = store
        .contacts()
        .unwrap()
        .into_iter()
        .find(|c| c.id == ana)
        .unwrap();
    assert_eq!(saved.name, "Ana Lopez");
    assert_eq!(saved.relationship, None);
    assert_eq!(saved.notes, None);

    let mut reversed = store.contacts().unwrap();
    reversed.reverse();
    store.reorder_contacts(&reversed).unwrap();
    let names = |s: &Store| {
        s.contacts()
            .unwrap()
            .into_iter()
            .map(|c| c.name)
            .collect::<Vec<_>>()
    };
    assert_eq!(names(&store), vec!["Bo", "Ana Lopez"]);

    store.delete_contact(bo).unwrap();
    assert_eq!(names(&store), vec!["Ana Lopez"]);
}

#[test]
fn emergency_contacts_are_those_who_get_the_alert() {
    let store = fresh_store();
    store
        .add_contact(ContactEntity::new("Ana", "1"), None)
        .unwrap();
    store
        .add_contact(
            ContactEntity {
                notify_on_emergency: false,
                ..ContactEntity::new("Bo", "2")
            },
            None,
        )
        .unwrap();
    let names: Vec<_> = store
        .emergency_contacts()
        .unwrap()
        .into_iter()
        .map(|c| c.name)
        .collect();
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
    store
        .add_destination(DestinationEntity::new("Church"), None)
        .unwrap();
    let cleared = DestinationEntity {
        reason: None,
        ..store
            .destinations()
            .unwrap()
            .into_iter()
            .find(|d| d.id == park)
            .unwrap()
    };
    store.update_destination(&cleared).unwrap();
    assert_eq!(
        store
            .destinations()
            .unwrap()
            .into_iter()
            .find(|d| d.id == park)
            .unwrap()
            .reason,
        None
    );
    let mut reversed = store.destinations().unwrap();
    reversed.reverse();
    store.reorder_destinations(&reversed).unwrap();
    let names = |s: &Store| {
        s.destinations()
            .unwrap()
            .into_iter()
            .map(|d| d.name)
            .collect::<Vec<_>>()
    };
    assert_eq!(names(&store), vec!["Church", "Park"]);
    store.delete_destination(park).unwrap();
    assert_eq!(names(&store), vec!["Church"]);
}

#[test]
fn update_profile_sets_given_fields_and_keeps_the_rest() {
    let store = fresh_store();
    store
        .update_profile(&[
            (ProfileField::FullName, Some("Margaret Smith".into())),
            (ProfileField::Nickname, Some("Maggie".into())),
        ])
        .unwrap();
    store
        .update_profile(&[
            (ProfileField::Height, Some("5'6\"".into())),
            (ProfileField::Nickname, None),
        ])
        .unwrap();
    let profile = store.profile().unwrap().unwrap();
    assert_eq!(profile.name, "Margaret Smith");
    assert_eq!(profile.nickname, None);
    assert_eq!(profile.height.as_deref(), Some("5'6\""));
}

#[test]
fn settings_round_trip_and_replace() {
    let store = fresh_store();
    assert_eq!(store.setting(Store::LANGUAGE_PREFERENCE).unwrap(), None);
    store.put_setting(Store::LANGUAGE_PREFERENCE, "es").unwrap();
    store.put_setting(Store::LANGUAGE_PREFERENCE, "en").unwrap();
    assert_eq!(
        store
            .setting(Store::LANGUAGE_PREFERENCE)
            .unwrap()
            .as_deref(),
        Some("en")
    );
}

/// The photo cleaner's calls, as the URIs it was told to keep.
fn record_cleaning(store: &Store) -> Arc<Mutex<Vec<Option<String>>>> {
    let cleaned = Arc::new(Mutex::new(Vec::new()));
    let c = cleaned.clone();
    store.set_photo_cleaner(Some(Box::new(move |keeping: Option<&str>| {
        c.lock().unwrap().push(keeping.map(str::to_string))
    })));
    cleaned
}

/// Makes the database refuse `event` on `table` (when `condition` holds), as a full disk would.
fn fail_on(store: &Store, event: &str, table: &str, condition: &str) {
    store
        .execute_sql(&format!(
            "CREATE TRIGGER refuse BEFORE {event} ON {table} WHEN {condition} \
             BEGIN SELECT RAISE(ABORT, 'refused'); END"
        ))
        .unwrap();
}

fn photo(field_value: &str) -> [(ProfileField, Option<String>); 1] {
    [(ProfileField::PhotoUri, Some(field_value.to_string()))]
}

#[test]
fn delete_account_forgets_the_device_id_and_removes_the_photos() {
    let store = fresh_store();
    let cleaned = record_cleaning(&store);
    store.seed().unwrap();
    let before = store.device_id().unwrap();
    store
        .update_profile(&photo("file:///files/profile_photo_1.jpg"))
        .unwrap();
    cleaned.lock().unwrap().clear();

    store.clear_all_data().unwrap();
    assert_eq!(*cleaned.lock().unwrap(), vec![None]);
    assert_ne!(
        before,
        store.device_id().unwrap(),
        "the cached ID outlived the settings row it came from"
    );
}

#[test]
fn delete_account_failing_half_way_changes_nothing_and_keeps_the_photos() {
    let store = fresh_store();
    let cleaned = record_cleaning(&store);
    store.seed().unwrap();
    for step in Store::ONBOARDING_STEPS {
        store.complete_step(step).unwrap();
    }
    store
        .update_profile(&[(ProfileField::FullName, Some("Margaret Smith".into()))])
        .unwrap();
    store
        .add_contact(ContactEntity::new("John Smith", "5551234567"), None)
        .unwrap();
    store.put_setting(Store::THEME_PREFERENCE, "dark").unwrap();
    // The profile, contacts and the rest are deleted before the settings are.
    fail_on(&store, "DELETE", "settings", "1");

    assert!(store.clear_all_data().is_err());
    assert!(cleaned.lock().unwrap().is_empty());
    assert_eq!(store.profile().unwrap().unwrap().name, "Margaret Smith");
    assert_eq!(store.contacts().unwrap()[0].name, "John Smith");
    assert_eq!(
        store.setting(Store::THEME_PREFERENCE).unwrap().as_deref(),
        Some("dark")
    );
    assert!(store.is_onboarded().unwrap());
}

#[test]
fn saving_a_new_photo_cleans_up_only_when_the_photo_changed() {
    let store = fresh_store();
    let cleaned = record_cleaning(&store);
    store.seed().unwrap();
    store
        .save_profile(|mut p| {
            p.name = "Margaret Smith".into();
            p
        })
        .unwrap();
    assert!(
        cleaned.lock().unwrap().is_empty(),
        "a save that leaves the photo alone has nothing to clean"
    );
    store
        .update_profile(&photo("file:///files/profile_photo_1.jpg"))
        .unwrap();
    store
        .update_profile(&[(ProfileField::Nickname, Some("Peggy".into()))])
        .unwrap();
    store
        .update_profile(&photo("file:///files/profile_photo_2.jpg"))
        .unwrap();
    store
        .update_profile(&[(ProfileField::PhotoUri, None)])
        .unwrap();
    assert_eq!(
        *cleaned.lock().unwrap(),
        vec![
            Some("file:///files/profile_photo_1.jpg".to_string()),
            Some("file:///files/profile_photo_2.jpg".to_string()),
            None
        ]
    );
}

#[test]
fn a_failed_profile_save_keeps_the_previous_photo() {
    let store = fresh_store();
    let cleaned = record_cleaning(&store);
    store.seed().unwrap();
    store
        .update_profile(&photo("file:///files/profile_photo_1.jpg"))
        .unwrap();
    cleaned.lock().unwrap().clear();
    fail_on(&store, "INSERT", "profile", "1");

    assert!(
        store
            .update_profile(&photo("file:///files/profile_photo_2.jpg"))
            .is_err()
    );
    assert!(cleaned.lock().unwrap().is_empty());
    assert_eq!(
        store.profile().unwrap().unwrap().photo_uri.as_deref(),
        Some("file:///files/profile_photo_1.jpg")
    );
}

#[test]
fn a_cleaner_that_fails_does_not_fail_the_write() {
    let store = fresh_store();
    store.set_photo_cleaner(Some(Box::new(|_: Option<&str>| panic!("disk gone"))));
    store.seed().unwrap();
    store
        .update_profile(&photo("file:///files/profile_photo_1.jpg"))
        .unwrap();
    store.clear_all_data().unwrap();
    assert_eq!(store.profile().unwrap(), None);
}

#[test]
fn clearing_the_emergency_failing_half_way_keeps_both_settings() {
    let store = fresh_store();
    store
        .save_active_emergency(&ActiveEmergency {
            started_at: "2026-10-06T16:00:00.000Z".into(),
            wearing: String::new(),
            checked_steps: vec![],
            incident_id: None,
        })
        .unwrap();
    store
        .put_setting(Store::EMERGENCY_AWAY_FROM, "1000")
        .unwrap();
    fail_on(
        &store,
        "INSERT",
        "settings",
        &format!("NEW.key = '{}'", Store::EMERGENCY_AWAY_FROM),
    );

    assert!(store.clear_active_emergency().is_err());
    assert!(store.active_emergency().unwrap().is_some());
    assert_eq!(
        store
            .setting(Store::EMERGENCY_AWAY_FROM)
            .unwrap()
            .as_deref(),
        Some("1000")
    );
}

#[test]
fn reorder_failing_half_way_keeps_the_old_order() {
    let store = fresh_store();
    for name in ["Ana", "Bo", "Cy"] {
        store
            .add_contact(ContactEntity::new(name, "1"), None)
            .unwrap();
    }
    for name in ["Park", "Church", "Shop"] {
        store
            .add_destination(DestinationEntity::new(name), None)
            .unwrap();
    }
    // Reversed, the first row moves to 0 and the last to 2: the move to 2 is refused.
    fail_on(&store, "UPDATE", "contacts", "NEW.sort_order = 2");
    let mut reversed = store.contacts().unwrap();
    reversed.reverse();
    assert!(store.reorder_contacts(&reversed).is_err());
    let order: Vec<_> = store
        .contacts()
        .unwrap()
        .into_iter()
        .map(|c| (c.name, c.sort_order))
        .collect();
    assert_eq!(
        order,
        vec![("Ana".into(), 0), ("Bo".into(), 1), ("Cy".into(), 2)]
    );

    store.execute_sql("DROP TRIGGER refuse").unwrap();
    fail_on(&store, "UPDATE", "destinations", "NEW.sort_order = 2");
    let mut reversed = store.destinations().unwrap();
    reversed.reverse();
    assert!(store.reorder_destinations(&reversed).is_err());
    let order: Vec<_> = store
        .destinations()
        .unwrap()
        .into_iter()
        .map(|d| (d.name, d.sort_order))
        .collect();
    assert_eq!(
        order,
        vec![("Park".into(), 0), ("Church".into(), 1), ("Shop".into(), 2)]
    );
}

#[test]
fn concurrent_adds_get_distinct_sort_orders() {
    let store = Arc::new(Store::open(temp_db()).unwrap());
    let threads: Vec<_> = (0..25)
        .map(|i| {
            let store = store.clone();
            std::thread::spawn(move || {
                store
                    .add_contact(ContactEntity::new(format!("Contact {i}"), "1"), None)
                    .unwrap();
                store
                    .add_destination(DestinationEntity::new(format!("Place {i}")), None)
                    .unwrap();
            })
        })
        .collect();
    threads.into_iter().for_each(|t| t.join().unwrap());
    let mut contacts: Vec<_> = store
        .contacts()
        .unwrap()
        .iter()
        .map(|c| c.sort_order)
        .collect();
    contacts.sort();
    let mut places: Vec<_> = store
        .destinations()
        .unwrap()
        .iter()
        .map(|d| d.sort_order)
        .collect();
    places.sort();
    assert_eq!(contacts, (0..25).collect::<Vec<_>>());
    assert_eq!(places, (0..25).collect::<Vec<_>>());
}

#[test]
fn concurrent_profile_saves_keep_each_others_fields() {
    let store = Arc::new(Store::open(temp_db()).unwrap());
    let fields = [
        ProfileField::Nickname,
        ProfileField::Height,
        ProfileField::Weight,
        ProfileField::HairColor,
        ProfileField::EyeColor,
        ProfileField::Likes,
        ProfileField::Allergies,
        ProfileField::SafeWord,
    ];
    let threads: Vec<_> = fields
        .into_iter()
        .map(|field| {
            let store = store.clone();
            std::thread::spawn(move || {
                store
                    .update_profile(&[(field, Some("set".into()))])
                    .unwrap()
            })
        })
        .collect();
    threads.into_iter().for_each(|t| t.join().unwrap());
    let profile = store.profile().unwrap().unwrap();
    for field in fields {
        assert_eq!(profile.field(field), Some("set"), "{field:?}");
    }
}

#[test]
fn a_closed_store_fails_every_call() {
    let store = fresh_store();
    store.seed().unwrap();
    store.close();
    assert!(matches!(store.profile(), Err(StoreError::Closed)));
    assert!(matches!(store.seed(), Err(StoreError::Closed)));
    assert!(matches!(
        store.put_setting(Store::THEME_PREFERENCE, "dark"),
        Err(StoreError::Closed)
    ));
}

#[test]
fn a_renamed_table_fails_its_calls_until_it_is_back() {
    let store = fresh_store();
    store
        .update_profile(&[(ProfileField::FullName, Some("Ana".into()))])
        .unwrap();
    store
        .execute_sql("ALTER TABLE profile RENAME TO profile_gone")
        .unwrap();
    assert!(store.profile().is_err());
    assert!(store.contacts().is_ok(), "the other tables still work");
    store
        .execute_sql("ALTER TABLE profile_gone RENAME TO profile")
        .unwrap();
    assert_eq!(store.profile().unwrap().unwrap().name, "Ana");
}
