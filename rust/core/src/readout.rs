//! What the info sheet shows and copies: the 911 call script and the full-details block.
//! Held to spec/vectors/readout.json. `t` is bound to the `readout` namespace; times arrive
//! already formatted by the platform.

use crate::age::age_on;
use crate::i18n::Translator;
use crate::mobility::describe_mobility;
use crate::profile::{Profile, set};
use chrono::NaiveDate;
use serde_json::json;

pub struct ReadoutInput {
    pub profile: Profile,
    /// Last-seen time, formatted for display.
    pub last_seen_time: Option<String>,
    /// For the age line; screens pass the current date.
    pub today: NaiveDate,
}

const VEHICLE_MOBILITY_VALUES: [&str; 7] = [
    "motorized wheelchair",
    "mobility scooter",
    "bicycle",
    "has vehicle",
    "manual wheelchair",
    "uses walker",
    "uses cane",
];

pub fn needs_vehicle_check(mobility_level: Option<&str>) -> bool {
    let Some(value) = mobility_level.filter(|m| !m.is_empty()) else {
        return false;
    };
    let value = value.to_lowercase();
    VEHICLE_MOBILITY_VALUES.iter().any(|v| value.contains(v))
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum VehicleCheckKind {
    Vehicle,
    Aid,
}

impl VehicleCheckKind {
    pub fn key(self) -> &'static str {
        match self {
            Self::Vehicle => "vehicle",
            Self::Aid => "aid",
        }
    }
}

/// Which note to show for a vehicle check: a vehicle or bike, or a mobility aid.
pub fn vehicle_check_kind(mobility_level: &str) -> VehicleCheckKind {
    let value = mobility_level.to_lowercase();
    if ["vehicle", "bicycle", "bike", "scooter"]
        .iter()
        .any(|v| value.contains(v))
    {
        VehicleCheckKind::Vehicle
    } else {
        VehicleCheckKind::Aid
    }
}

pub fn describe_appearance(p: &Profile, t: &Translator) -> String {
    let mut parts = Vec::new();
    parts.extend(set(&p.height).map(str::to_string));
    parts.extend(set(&p.weight).map(str::to_string));
    parts.extend(set(&p.hair_color).map(|c| t.tv("copyBlock.hairColor", &[("color", json!(c))])));
    parts.extend(set(&p.eye_color).map(|c| t.tv("copyBlock.eyeColor", &[("color", json!(c))])));
    parts.extend(set(&p.identifying_marks).map(str::to_string));
    parts.join(", ")
}

pub fn describe_important_details(p: &Profile, t: &Translator) -> String {
    let mut parts = Vec::new();
    parts.extend(set(&p.medical_conditions).map(str::to_string));
    parts.extend(set(&p.allergies).map(|v| t.tv("copyBlock.allergies", &[("value", json!(v))])));
    parts.join(". ")
}

pub fn build_copy_block(input: &ReadoutInput, t: &Translator) -> String {
    let p = &input.profile;
    let appearance = describe_appearance(p, t);
    let important = describe_important_details(p, t);
    let value =
        |field: &Option<String>, key: &str| set(field).map(|v| t.tv(key, &[("value", json!(v))]));
    let desc = |d: &str, key: &str| (!d.is_empty()).then(|| t.tv(key, &[("desc", json!(d))]));
    let unknown = t.t("copyBlock.unknown");

    [
        Some(match set(&p.nickname) {
            Some(nick) => t.tv(
                "copyBlock.nameWithNickname",
                &[("name", json!(p.name)), ("nickname", json!(nick))],
            ),
            None => t.tv("copyBlock.name", &[("name", json!(p.name))]),
        }),
        set(&p.date_of_birth).map(|d| t.tv("copyBlock.dob", &[("dob", json!(d))])),
        desc(&appearance, "copyBlock.appearance"),
        desc(&important, "copyBlock.importantDetails"),
        value(&p.medications, "copyBlock.medications"),
        value(&p.cognitive_status, "copyBlock.cognitiveStatus"),
        set(&p.mobility_level).map(|m| {
            t.tv(
                "copyBlock.mobility",
                &[("value", json!(describe_mobility(m, t)))],
            )
        }),
        needs_vehicle_check(p.mobility_level.as_deref())
            .then(|| t.t("copyBlock.mobilityVehicleNote")),
        value(&p.communication_preference, "copyBlock.communication"),
        value(&p.escalation_signs, "copyBlock.escalation"),
        value(&p.dislikes_triggers, "copyBlock.triggers"),
        value(&p.deescalation_techniques, "copyBlock.deescalation"),
        value(&p.likes, "copyBlock.likes"),
        value(&p.approach_guidance, "copyBlock.approach"),
        value(&p.safe_word, "copyBlock.safeWord"),
        Some(t.tv(
            "copyBlock.lastSeen",
            &[(
                "time",
                json!(input.last_seen_time.clone().unwrap_or(unknown.clone())),
            )],
        )),
        // The app doesn't collect location (blocked on purpose); this is a line to fill in.
        Some(t.tv("copyBlock.coordinates", &[("coords", json!(unknown))])),
        value(&p.locative_device_info, "copyBlock.locator"),
        value(&p.id_bracelets, "copyBlock.idBracelet"),
        value(&p.medic_alert_id, "copyBlock.medicAlertId"),
        value(&p.medic_alert_hotline, "copyBlock.medicAlertHotline"),
        // Blank line before the reminder
        Some(String::new()),
        Some(t.t("copyBlock.wearingReminder")),
    ]
    .into_iter()
    .flatten()
    .collect::<Vec<_>>()
    .join("\n")
}

pub fn build_script(input: &ReadoutInput, t: &Translator) -> String {
    let p = &input.profile;
    let appearance = describe_appearance(p, t);
    let important = describe_important_details(p, t);
    let mut parts = vec![
        t.t("script.opening"),
        t.tv("script.name", &[("name", json!(p.name))]),
    ];
    if let Some(age) = set(&p.date_of_birth).and_then(|d| age_on(d, input.today)) {
        parts.push(t.tv("script.age", &[("age", json!(age))]));
    }
    parts.push(match set(&input.last_seen_time) {
        Some(time) => t.tv("script.lastSeenTime", &[("time", json!(time))]),
        None => t.t("script.lastSeenUnknown"),
    });
    // Location isn't collected (blocked on purpose): the caller fills it in.
    parts.push(t.t("script.locationUnknown"));
    if !appearance.is_empty() {
        parts.push(t.tv("script.appearance", &[("desc", json!(appearance))]));
    }
    if !important.is_empty() {
        parts.push(t.tv("script.additionalContext", &[("desc", json!(important))]));
    }
    if let Some(id) = set(&p.medic_alert_id) {
        parts.push(t.tv("script.medicAlertId", &[("id", json!(id))]));
    }
    if set(&p.photo_uri).is_some() {
        parts.push(t.t("script.photoAvailable"));
    }
    parts.push(t.t("script.silverAlert"));
    parts.join(" ")
}

/// What's missing for a stronger script, as translated phrases.
pub fn missing_script_details(input: &ReadoutInput, t: &Translator) -> Vec<String> {
    let mut missing = Vec::new();
    if set(&input.last_seen_time).is_none() {
        missing.push(t.t("sections.script.missing.lastSeenTime"));
    }
    if describe_appearance(&input.profile, t).is_empty() {
        missing.push(t.t("sections.script.missing.appearanceDetails"));
    }
    if describe_important_details(&input.profile, t).is_empty() {
        missing.push(t.t("sections.script.missing.importantDetails"));
    }
    missing
}
