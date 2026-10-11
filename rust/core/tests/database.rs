//! The port of the retired Kotlin DatabaseTest: the L5 contract at unit level. The Rust app must
//! open the database the RN app left behind (spec/storage.md) with every row intact, using
//! the committed RN fixture, spec/fixtures/upgrade/android/SQLite/nijii.db.

use nijii_core::emergency::parse_active_emergency;
use nijii_core::store::{DATABASE_NAME, Store};
use rusqlite::{Connection, types::ValueRef};
use serde_json::{Value, json};
use std::path::{Path, PathBuf};

type Row = Vec<(String, Option<String>)>;

const TABLES: [&str; 7] = [
    "profile",
    "contacts",
    "destinations",
    "incidents",
    "safety_checks",
    "settings",
    "onboarding",
];

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn temp_dir() -> PathBuf {
    let dir = std::env::temp_dir().join(format!("nijii-{}", uuid::Uuid::new_v4()));
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

fn rn_fixture_copy() -> PathBuf {
    let db = temp_dir().join(DATABASE_NAME);
    std::fs::copy(
        root().join("spec/fixtures/upgrade/android/SQLite/nijii.db"),
        &db,
    )
    .unwrap();
    db
}

/// Every row of `table` as column -> value text, in rowid order.
fn rows(path: &Path, table: &str) -> Vec<Row> {
    let c = Connection::open(path).unwrap();
    let mut st = c
        .prepare(&format!("SELECT * FROM `{table}` ORDER BY rowid"))
        .unwrap();
    let names: Vec<String> = st.column_names().iter().map(|n| n.to_string()).collect();
    st.query_map([], |r| {
        Ok(names
            .iter()
            .enumerate()
            .map(|(i, n)| {
                let v = match r.get_ref(i).unwrap() {
                    ValueRef::Null => None,
                    ValueRef::Integer(v) => Some(v.to_string()),
                    ValueRef::Real(v) => Some(v.to_string()),
                    ValueRef::Text(t) => Some(String::from_utf8_lossy(t).into_owned()),
                    ValueRef::Blob(b) => Some(format!("{b:?}")),
                };
                (n.clone(), v)
            })
            .collect())
    })
    .unwrap()
    .collect::<Result<_, _>>()
    .unwrap()
}

fn user_version(path: &Path) -> i64 {
    Connection::open(path)
        .unwrap()
        .query_row("PRAGMA user_version", [], |r| r.get(0))
        .unwrap()
}

#[test]
fn rn_database_migrates_with_every_row_intact() {
    let path = rn_fixture_copy();
    let before: Vec<(&str, Vec<Row>)> = TABLES.iter().map(|t| (*t, rows(&path, t))).collect();
    assert!(!before[0].1.is_empty(), "fixture has a profile");
    assert_eq!(user_version(&path), 0, "an RN database has no user_version");

    let store = Store::open(&path).unwrap();
    assert_eq!(
        store.migrated_from(),
        Some(1),
        "reported as data_migrated from the RN schema"
    );
    assert_eq!(
        store.data_migrated_properties(1, 12).unwrap(),
        json!({
            "from_version": 1, "to_version": 2, "ok": true, "duration_ms": 12, "has_profile": true,
            "contacts_count": 1, "places_count": 1, "incidents_count": 1,
        })
        .as_object()
        .unwrap()
        .clone()
    );
    drop(store);

    for (table, expected) in &before {
        assert_eq!(
            expected,
            &rows(&path, table),
            "rows of {table} after migration"
        );
    }
    assert_eq!(user_version(&path), 2);
}

#[test]
fn app_reads_the_rn_data() {
    let path = rn_fixture_copy();
    let device_id_before = rows(&path, "settings")
        .into_iter()
        .find(|r| r[0].1.as_deref() == Some("device_id"))
        .unwrap()[1]
        .1
        .clone();
    let store = Store::open(&path).unwrap();

    let profile = store.profile().unwrap().expect("profile");
    assert_eq!(profile.name, "Margaret Smith");
    assert_eq!(profile.nickname.as_deref(), Some("Maggie"));
    assert_eq!(profile.date_of_birth.as_deref(), Some("03/15/1940"));
    assert_eq!(profile.dominant_hand.as_deref(), Some("left"));
    assert_eq!(profile.mobility_level.as_deref(), Some("Uses cane"));

    let contacts = store.contacts().unwrap();
    assert_eq!(contacts.len(), 1);
    assert_eq!(contacts[0].name, "John Smith");
    assert!(contacts[0].notify_on_emergency);
    let places = store.destinations().unwrap();
    assert_eq!(places.len(), 1);
    assert_eq!(places[0].name, "Riverside Park");

    assert_eq!(
        store.setting("device_id").unwrap(),
        device_id_before,
        "device_id survives"
    );
    assert_eq!(store.device_id().unwrap(), device_id_before.unwrap());
    assert_eq!(
        store.setting("theme_preference").unwrap().as_deref(),
        Some("dark")
    );
    let emergency = parse_active_emergency(store.setting("active_emergency").unwrap().as_deref())
        .expect("emergency");
    assert_eq!(emergency.checked_steps, vec!["home_search", "neighbors"]);
    assert_eq!(emergency.wearing, "Blue jacket");
    let incidents = store.incidents().unwrap();
    assert_eq!(incidents.len(), 1);
    assert_eq!(incidents[0].outcome.as_deref(), Some("ongoing"));
    assert_eq!(Some(incidents[0].id), emergency.incident_id);

    assert!(
        store.is_onboarded().unwrap(),
        "onboarding is not shown again"
    );
}

#[test]
fn migrated_database_reopens_without_migrating() {
    let path = rn_fixture_copy();
    assert_eq!(Store::open(&path).unwrap().migrated_from(), Some(1));
    let again = Store::open(&path).unwrap();
    assert_eq!(
        again.migrated_from(),
        None,
        "nothing to migrate the second time"
    );
    assert_eq!(again.profile().unwrap().unwrap().name, "Margaret Smith");
}

#[test]
fn fresh_install_creates_version_two() {
    let path = temp_dir().join(DATABASE_NAME);
    let store = Store::open(&path).unwrap();
    assert_eq!(
        store.migrated_from(),
        None,
        "a fresh install isn't a migration"
    );
    store.update_profile(&[("name", Some("Ana"))]).unwrap();
    assert_eq!(store.profile().unwrap().unwrap().name, "Ana");
    drop(store);
    assert_eq!(user_version(&path), 2);
}

/// (name, type, notnull, default, pk) per column, as Room validates a table.
fn table_info(c: &Connection, table: &str) -> Vec<(String, String, bool, Option<String>, i64)> {
    let mut st = c.prepare(&format!("PRAGMA table_info(`{table}`)")).unwrap();
    st.query_map([], |r| {
        Ok((r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?))
    })
    .unwrap()
    .collect::<Result<_, _>>()
    .unwrap()
}

/// The on-disk schema is what Room v2 produces (spec/room-schema-v2.json, the schema the KMP app exported), whether the
/// file is new or migrated from the RN app's.
#[test]
fn schema_is_rooms_version_two_fresh_and_migrated() {
    let exported: Value = serde_json::from_str(
        &std::fs::read_to_string(root().join("spec/room-schema-v2.json")).unwrap(),
    )
    .unwrap();
    let database = &exported["database"];
    let fresh = temp_dir().join(DATABASE_NAME);
    drop(Store::open(&fresh).unwrap());
    let migrated = rn_fixture_copy();
    drop(Store::open(&migrated).unwrap());

    for path in [&fresh, &migrated] {
        let c = Connection::open(path).unwrap();
        for entity in database["entities"].as_array().unwrap() {
            let table = entity["tableName"].as_str().unwrap();
            let expected: Vec<_> = entity["fields"]
                .as_array()
                .unwrap()
                .iter()
                .map(|f| {
                    let pk = entity["primaryKey"]["columnNames"]
                        .as_array()
                        .unwrap()
                        .iter()
                        .position(|n| n == &f["columnName"]);
                    (
                        f["columnName"].as_str().unwrap().to_string(),
                        f["affinity"].as_str().unwrap().to_string(),
                        f["notNull"].as_bool().unwrap_or(false),
                        f["defaultValue"].as_str().map(str::to_string),
                        pk.map_or(0, |p| p as i64 + 1),
                    )
                })
                .collect();
            assert_eq!(
                table_info(&c, table),
                expected,
                "columns of {table} in {}",
                path.display()
            );
        }
        let hash: String = c
            .query_row(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(hash, database["identityHash"].as_str().unwrap());
        let index: i64 = c
            .query_row(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_safety_checks_item_key'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(index, 1);
    }
}

#[test]
fn a_newer_database_is_refused_not_downgraded() {
    let path = temp_dir().join(DATABASE_NAME);
    Connection::open(&path)
        .unwrap()
        .execute_batch("PRAGMA user_version = 3")
        .unwrap();
    assert!(Store::open(&path).is_err());
    assert_eq!(user_version(&path), 3);
}
