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
    /// When the search started (the emergency's start), formatted for display. Named for the old
    /// "last seen" label.
    pub last_seen_time: Option<String>,
    /// For the age line; screens pass the current date.
    pub today: NaiveDate,
    /// The start's clock time as the locale says it, with no seconds ("9:00 AM"), for the
    /// spoken script.
    pub last_seen_clock: Option<String>,
    /// The day the search started, in the device's time zone, so the script can say "today".
    pub last_seen_date: Option<NaiveDate>,
}

/// A piece of the 911 script: words to read out, or a blank the caller fills in aloud (the
/// location, which the app doesn't collect, or an unknown time). The screens show a blank as a
/// highlighted gap to fill, not as bracketed text to read out to the dispatcher.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct ScriptPart {
    pub text: String,
    pub blank: bool,
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

/// When the search started, as it is said on the phone: "9:00 AM today" or "9:00 AM yesterday"
/// (no date, no seconds), else the full date and time.
fn spoken_start(input: &ReadoutInput, t: &Translator) -> Option<String> {
    let clock = set(&input.last_seen_clock);
    let date = input.last_seen_date;
    match clock {
        Some(clock) if date == Some(input.today) => {
            Some(t.tv("script.timeToday", &[("time", json!(clock))]))
        }
        Some(clock) if date.is_some() && date == input.today.pred_opt() => {
            Some(t.tv("script.timeYesterday", &[("time", json!(clock))]))
        }
        _ => set(&input.last_seen_time).map(str::to_string),
    }
}

/// Placeholder for a blank while a sentence is translated: the sentence is split around it.
const BLANK: &str = "\u{0}";

/// The 911 script in pieces, its blanks marked (see [`ScriptPart`]); [`build_script`] is the
/// same text.
pub fn build_script_parts(input: &ReadoutInput, t: &Translator) -> Vec<ScriptPart> {
    let p = &input.profile;
    let appearance = describe_appearance(p, t);
    let important = describe_important_details(p, t);
    let words = |text: String| ScriptPart { text, blank: false };
    let mut sentences: Vec<Vec<ScriptPart>> = Vec::new();
    let mut say = |text: String| sentences.push(vec![words(text)]);

    say(t.t("script.opening"));
    say(t.tv("script.name", &[("name", json!(p.name))]));
    if let Some(age) = set(&p.date_of_birth).and_then(|d| age_on(d, input.today)) {
        say(t.tv("script.age", &[("age", json!(age))]));
    }
    let started = spoken_start(input, t);
    if let Some(time) = &started {
        say(t.tv("script.lastSeenTime", &[("time", json!(time))]));
    }
    // A sentence with one blank in it: "Last known location: ____."
    let with_blank = |key: &str, blank_key: &str| -> Vec<ScriptPart> {
        let sentence = t.tv(key, &[("blank", json!(BLANK))]);
        let (before, after) = sentence.split_once(BLANK).unwrap_or((&sentence, ""));
        let mut parts = Vec::new();
        if !before.is_empty() {
            parts.push(words(before.to_string()));
        }
        parts.push(ScriptPart {
            text: t.t(blank_key),
            blank: true,
        });
        if !after.is_empty() {
            parts.push(words(after.to_string()));
        }
        parts
    };
    if started.is_none() {
        sentences.push(with_blank("script.lastSeenUnknown", "script.blankTime"));
    }
    // Location isn't collected (blocked on purpose): the caller fills it in.
    sentences.push(with_blank("script.locationUnknown", "script.blankLocation"));
    let mut say = |text: String| sentences.push(vec![words(text)]);
    if !appearance.is_empty() {
        say(t.tv("script.appearance", &[("desc", json!(appearance))]));
    }
    if !important.is_empty() {
        say(t.tv("script.additionalContext", &[("desc", json!(important))]));
    }
    if let Some(id) = set(&p.medic_alert_id) {
        say(t.tv("script.medicAlertId", &[("id", json!(id))]));
    }
    if set(&p.photo_uri).is_some() {
        say(t.t("script.photoAvailable"));
    }
    say(t.t("script.silverAlert"));

    // Sentences are separated by one space; runs of plain words are joined into one part, so
    // the pieces alternate between words and blanks.
    let mut out: Vec<ScriptPart> = Vec::new();
    for (i, sentence) in sentences.into_iter().enumerate() {
        let separator = (i > 0).then(|| words(" ".to_string()));
        for part in separator.into_iter().chain(sentence) {
            match out.last_mut() {
                Some(last) if !last.blank && !part.blank => last.text += &part.text,
                _ => out.push(part),
            }
        }
    }
    out
}

/// The 911 script as one text, for copying: each blank in square brackets
/// ("Last known location: [fill in location].") so a pasted copy still shows the gap.
pub fn build_script(input: &ReadoutInput, t: &Translator) -> String {
    build_script_parts(input, t)
        .into_iter()
        .map(|p| {
            if p.blank {
                format!("[{}]", p.text)
            } else {
                p.text
            }
        })
        .collect()
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
