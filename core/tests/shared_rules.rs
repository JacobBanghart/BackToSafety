//! The twins of kmp/shared's AbbreviateIdTest, ProfileFieldTest, ReadinessTest,
//! HomeCountsPluralTest and PlaceholderContrastTest: small rules both apps share.

use nijii_core::formatters::abbreviate_id;
use nijii_core::profile::{PROFILE_TEXT_FIELDS, Profile, ProfileField, ProfileKeyboard};
use nijii_core::readiness::{ReadinessCheck, ReadinessItem, readiness_checklist};
use nijii_core::store::ProfileEntity;
use nijii_core::{Translations, Translator};
use serde_json::{Value, json};
use std::collections::HashSet;
use std::path::{Path, PathBuf};

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("..")
}

fn spec(name: &str) -> Value {
    serde_json::from_str(&std::fs::read_to_string(root().join("spec").join(name)).unwrap()).unwrap()
}

// --- AbbreviateIdTest

#[test]
fn a_uuid_keeps_its_start_and_end() {
    assert_eq!(
        abbreviate_id("3f2a9c1b-7d4e-4b2a-9c1d-0a5e6f7b41ef", 8, 4),
        "3f2a9c1b…41ef"
    );
}

#[test]
fn a_short_value_is_shown_whole() {
    assert_eq!(abbreviate_id("—", 8, 4), "—");
    assert_eq!(abbreviate_id("1234567890123", 8, 4), "1234567890123");
}

// --- ProfileFieldTest

#[test]
fn keys_are_unique_and_every_column_has_a_field() {
    let keys: Vec<&str> = ProfileField::ALL.iter().map(|f| f.as_key()).collect();
    assert_eq!(
        keys.iter().collect::<HashSet<_>>().len(),
        keys.len(),
        "duplicate keys: {keys:?}"
    );
    // The profile table's columns as Room names them; id, created_at and updated_at are
    // bookkeeping, not profile fields.
    let schema = spec("room-schema-v2.json");
    let profile = schema["database"]["entities"]
        .as_array()
        .unwrap()
        .iter()
        .find(|e| e["tableName"] == "profile")
        .unwrap();
    let mut columns: Vec<&str> = profile["fields"]
        .as_array()
        .unwrap()
        .iter()
        .map(|f| f["fieldPath"].as_str().unwrap())
        .filter(|f| !["id", "createdAt", "updatedAt"].contains(f))
        .collect();
    let mut sorted = keys.clone();
    columns.sort();
    sorted.sort();
    assert_eq!(
        columns, sorted,
        "the profile table's columns vs ProfileField"
    );
    // Every key is a Profile property (the vectors' profile JSON uses them).
    let all: serde_json::Map<String, Value> = keys
        .iter()
        .map(|k| (k.to_string(), json!(format!("v-{k}"))))
        .collect();
    let p: Profile = serde_json::from_value(Value::Object(all)).unwrap();
    for field in ProfileField::ALL {
        assert_eq!(p.get(field), Some(format!("v-{}", field.as_key())));
    }
}

#[test]
fn the_text_fields_are_all_but_the_chosen_ones() {
    let text: HashSet<_> = PROFILE_TEXT_FIELDS.into_iter().collect();
    let expected: HashSet<_> = ProfileField::ALL
        .into_iter()
        .filter(|f| !matches!(f, ProfileField::DominantHand | ProfileField::MobilityLevel))
        .collect();
    assert_eq!(text, expected);
    assert_eq!(PROFILE_TEXT_FIELDS.len(), text.len(), "each once");
}

#[test]
fn each_field_is_read_and_written_on_its_own() {
    for field in ProfileField::ALL {
        let written = ProfileEntity::new("")
            .with(field, Some(format!("v-{}", field.key())))
            .to_profile();
        for other in ProfileField::ALL {
            let expected = if other == field {
                Some(format!("v-{}", field.key()))
            } else if other == ProfileField::FullName {
                Some(String::new())
            } else {
                None
            };
            assert_eq!(
                written.get(other),
                expected,
                "{} written, {} read",
                field.key(),
                other.key()
            );
        }
    }
}

#[test]
fn fields_say_how_they_are_typed() {
    let f = ProfileField::MedicAlertHotline;
    assert_eq!(f.keyboard(), ProfileKeyboard::Phone);
    assert!(f.has_hint() && !f.multiline());
    assert_eq!(f.format_input("5551234567".into()), "(555) 123-4567");
    assert_eq!(ProfileField::Height.format_input("56".into()), "5'6\"");
    assert_eq!(
        ProfileField::DateOfBirth.keyboard(),
        ProfileKeyboard::Number
    );
    assert!(ProfileField::FullName.capitalize_words());
    assert!(ProfileField::Likes.multiline());
    assert_eq!(ProfileField::Likes.format_input("tea".into()), "tea");
    assert_eq!(
        ProfileField::from_key("medicAlertId"),
        Some(ProfileField::MedicAlertId)
    );
}

// --- ReadinessTest

fn done(checks: &[ReadinessCheck]) -> Vec<ReadinessItem> {
    checks.iter().filter(|c| c.done).map(|c| c.item).collect()
}

fn named(name: &str) -> Profile {
    Profile {
        name: name.into(),
        ..Profile::default()
    }
}

#[test]
fn no_profile_and_nothing_saved_is_nothing_done() {
    let checks = readiness_checklist(None, 0, 0, 0);
    assert_eq!(
        checks.iter().map(|c| c.item).collect::<Vec<_>>(),
        ReadinessItem::ALL
    );
    assert!(done(&checks).is_empty());
}

#[test]
fn a_name_alone_is_not_a_description() {
    assert!(done(&readiness_checklist(Some(&named("Margaret")), 0, 0, 0)).is_empty());
}

#[test]
fn each_line_is_done_by_its_own_data() {
    let gray = Profile {
        hair_color: Some("Gray".into()),
        ..named("Margaret")
    };
    assert_eq!(
        done(&readiness_checklist(Some(&gray), 2, 1, 0)),
        vec![ReadinessItem::Description, ReadinessItem::Contacts]
    );
    let photo = Profile {
        photo_uri: Some("file:///p.jpg".into()),
        ..named("Margaret")
    };
    assert_eq!(
        done(&readiness_checklist(Some(&photo), 0, 0, 1)),
        vec![ReadinessItem::Photo, ReadinessItem::Places]
    );
}

fn contacts(saved: usize, in_alert_circle: usize) -> ReadinessCheck {
    readiness_checklist(None, saved, in_alert_circle, 0)
        .into_iter()
        .find(|c| c.item == ReadinessItem::Contacts)
        .unwrap()
}

#[test]
fn contacts_are_ready_only_with_someone_in_the_alert_circle() {
    let ready = contacts(3, 1);
    assert!(ready.done);
    assert!(!ready.needs_alert_circle);
    assert_eq!(ready.label_key, "contacts");
    assert_eq!(ready.action_key, "done");
}

#[test]
fn contacts_with_no_one_in_the_alert_circle_say_turn_it_on() {
    // Saved contacts that no one would be texted: the search's Alert Circle would reach nobody.
    let off = contacts(2, 0);
    assert!(!off.done);
    assert!(off.needs_alert_circle);
    assert_eq!(off.label_key, "contactsNoAlertCircle");
    assert_eq!(off.action_key, "turnOn");
}

#[test]
fn no_contacts_at_all_say_add() {
    let none = contacts(0, 0);
    assert!(!none.done);
    assert!(!none.needs_alert_circle);
    assert_eq!(none.label_key, "contacts");
    assert_eq!(none.action_key, "add");
}

#[test]
fn readiness_items_have_their_string_keys() {
    let keys: Vec<String> = ReadinessItem::ALL.iter().map(|i| i.key()).collect();
    assert_eq!(keys, ["photo", "description", "contacts", "places"]);
}

// --- HomeCountsPluralTest

fn translations() -> Translations {
    let mut files = Vec::new();
    for locale in std::fs::read_dir(root().join("i18n/locales"))
        .unwrap()
        .flatten()
    {
        if !locale.path().is_dir() {
            continue;
        }
        for file in std::fs::read_dir(locale.path()).unwrap().flatten() {
            let p = file.path();
            if p.extension().is_some_and(|e| e == "json") {
                files.push((
                    locale.file_name().to_string_lossy().into_owned(),
                    p.file_stem().unwrap().to_string_lossy().into_owned(),
                    std::fs::read_to_string(&p).unwrap(),
                ));
            }
        }
    }
    Translations::from_json(
        files
            .iter()
            .map(|(l, n, t)| (l.as_str(), n.as_str(), t.as_str())),
    )
}

/// Home's Contacts and Places tiles say "1 saved" / "1 guardado" in the singular, not
/// "1 guardados".
#[test]
fn the_saved_counts_agree_with_their_number() {
    let tr = std::sync::Arc::new(translations());
    let es: Translator = tr.translator("es", "home");
    let en: Translator = tr.translator("en", "home");
    for key in ["quickActions.contactsSaved", "quickActions.placesSaved"] {
        let count = |t: &Translator, n: i64| t.tv(key, &[("count", json!(n))]);
        assert_eq!(count(&es, 1), "1 guardado", "{key}");
        assert_eq!(count(&es, 2), "2 guardados", "{key}");
        assert_eq!(count(&en, 1), "1 saved", "{key}");
        assert_eq!(count(&en, 3), "3 saved", "{key}");
    }
}

// --- PlaceholderContrastTest

fn luminance(hex: &str) -> f64 {
    let rgb = u32::from_str_radix(hex.trim_start_matches('#'), 16).unwrap();
    let channel = |shift: u32| {
        let c = f64::from((rgb >> shift) & 0xFF) / 255.0;
        if c <= 0.03928 {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).powf(2.4)
        }
    };
    0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

fn contrast(a: &str, b: &str) -> f64 {
    let (x, y) = (luminance(a), luminance(b));
    (x.max(y) + 0.05) / (x.min(y) + 0.05)
}

/// Placeholders ("e.g., 150 lbs") are thin grey text that people read to know what to type, so
/// they hold 7:1 against every fill a field sits on, in both modes. At the 4.5:1 floor the
/// anti-aliased glyphs measured nearer 3:1 on the dark field fill.
#[test]
fn placeholders_hold_seven_to_one_on_every_field_fill() {
    let tokens = spec("design-tokens.json");
    for mode in ["light", "dark"] {
        let colors = &tokens["colors"][mode];
        let placeholder = colors["inputPlaceholder"].as_str().unwrap();
        for fill in ["inputBackground", "card"] {
            let fill = colors[fill].as_str().unwrap();
            let ratio = contrast(placeholder, fill);
            assert!(ratio >= 7.0, "{mode} placeholder on {fill} is {ratio:.2}:1");
        }
    }
}
