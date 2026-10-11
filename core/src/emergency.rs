//! The emergency search protocol: the checklist, the 15-minute countdown and the contact
//! alert text. Held to spec/vectors/emergency.json. `t` is bound to the `emergency` namespace.

use crate::i18n::Translator;
use chrono::DateTime;
use serde_json::{Map, Value, json};

pub const SEARCH_WINDOW_SECONDS: i64 = 15 * 60;
pub const WARNING_AT_SECONDS: i64 = 5 * 60;

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct ChecklistStep {
    pub id: String,
    pub step: i32,
    pub title: String,
    pub description: String,
    pub hint: Option<String>,
    pub urgent: bool,
    pub checked: bool,
}

pub const STEP_IDS: [&str; 11] = [
    "home_search",
    "outside_immediate",
    "neighbors",
    "radius_search",
    "high_risk",
    "familiar_places",
    "call_911",
    "silver_alert",
    "share_info",
    "coordinate",
    "document",
];
const URGENT_STEPS: [&str; 2] = ["high_risk", "call_911"];

pub fn build_initial_steps(t: &Translator, emergency_number: &str) -> Vec<ChecklistStep> {
    let number = [("emergencyNumber", json!(emergency_number))];
    let vars = |wanted: bool| if wanted { &number[..] } else { &[][..] };
    STEP_IDS
        .iter()
        .enumerate()
        .map(|(i, &id)| ChecklistStep {
            id: id.to_string(),
            step: i as i32 + 1,
            title: t.tv(&format!("steps.{id}.title"), vars(id == "call_911")),
            description: t.tv(
                &format!("steps.{id}.description"),
                vars(id == "silver_alert"),
            ),
            hint: (id == "home_search").then(|| t.t(&format!("steps.{id}.hint"))),
            urgent: URGENT_STEPS.contains(&id),
            checked: false,
        })
        .collect()
}

/// Seconds left in the search window, between 0 and the full window.
pub fn seconds_remaining(started_at_ms: i64, now_ms: i64) -> i64 {
    let elapsed = (now_ms - started_at_ms).div_euclid(1000);
    (SEARCH_WINDOW_SECONDS - elapsed).clamp(0, SEARCH_WINDOW_SECONDS)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum CountdownAlert {
    Warning,
    Expired,
}

impl CountdownAlert {
    pub const ALL: [CountdownAlert; 2] = [CountdownAlert::Warning, CountdownAlert::Expired];

    pub fn key(self) -> &'static str {
        match self {
            CountdownAlert::Warning => "warning",
            CountdownAlert::Expired => "expired",
        }
    }

    pub fn from_key(key: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|a| a.key() == key)
    }
}

/// Alerts due when the countdown moves from `prev` to `next` seconds left. Ticks can skip
/// seconds (the app was in the background), so alerts fire on crossing a threshold.
pub fn countdown_alerts(prev: i64, next: i64) -> Vec<CountdownAlert> {
    let mut alerts = Vec::new();
    if prev >= WARNING_AT_SECONDS && next < WARNING_AT_SECONDS && next > 0 {
        alerts.push(CountdownAlert::Warning);
    }
    if prev > 0 && next == 0 {
        alerts.push(CountdownAlert::Expired);
    }
    alerts
}

fn pad2(n: i64) -> String {
    format!("{:0>2}", n.to_string())
}

/// MM:SS for the countdown.
pub fn format_countdown(seconds: i64) -> String {
    format!("{}:{}", pad2(seconds / 60), pad2(seconds % 60))
}

/// Text sent to emergency contacts. `started_time` arrives already formatted.
pub fn build_alert_sms(
    t: &Translator,
    name: Option<&str>,
    started_time: &str,
    wearing: &str,
) -> String {
    let wearing_text = if wearing.is_empty() {
        String::new()
    } else {
        t.tv("smsWearing", &[("wearing", json!(wearing))])
    };
    let name = name
        .filter(|n| !n.is_empty())
        .map(str::to_string)
        .unwrap_or_else(|| t.t("smsUnknownName"));
    t.tv(
        "smsMessage",
        &[
            ("name", json!(name)),
            ("time", json!(started_time)),
            ("wearing", json!(wearing_text)),
        ],
    )
}

/// Which way they may veer, from their dominant hand.
pub fn direction_hint(t: &Translator, dominant_hand: Option<&str>) -> Option<String> {
    match dominant_hand {
        Some("left") => Some(t.t("directionHint.left")),
        Some("right") => Some(t.t("directionHint.right")),
        _ => None,
    }
}

/// The `active_emergency` settings record (spec/storage.md).
#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct ActiveEmergency {
    pub started_at: String,
    pub wearing: String,
    pub checked_steps: Vec<String>,
    /// The incidents row for this emergency; absent for emergencies started before it existed.
    #[uniffi(default = None)]
    pub incident_id: Option<i64>,
}

impl ActiveEmergency {
    pub fn started_at_ms(&self) -> Option<i64> {
        parse_instant_ms(&self.started_at)
    }
}

pub fn parse_instant_ms(iso: &str) -> Option<i64> {
    DateTime::parse_from_rfc3339(iso)
        .ok()
        .map(|d| d.timestamp_millis())
}

/// Parses the stored record. None when nothing is active or the value can't be read.
pub fn parse_active_emergency(raw: Option<&str>) -> Option<ActiveEmergency> {
    let state: Map<String, Value> = serde_json::from_str(raw.filter(|r| !r.is_empty())?).ok()?;
    if state.get("isActive") != Some(&Value::Bool(true)) {
        return None;
    }
    let started_at = state.get("startedAt")?.as_str()?;
    parse_instant_ms(started_at)?;
    Some(ActiveEmergency {
        started_at: started_at.to_string(),
        wearing: state
            .get("wearing")
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_string(),
        checked_steps: state
            .get("checkedSteps")
            .and_then(Value::as_array)
            .map(|a| {
                a.iter()
                    .filter_map(|s| s.as_str().map(str::to_string))
                    .collect()
            })
            .unwrap_or_default(),
        incident_id: state.get("incidentId").and_then(Value::as_i64),
    })
}

/// The stored form of [ActiveEmergency], in the same key order the RN app wrote
/// (JSON.stringify of { startedAt, wearing, checkedSteps, isActive, incidentId? }).
pub fn serialize_active_emergency(e: &ActiveEmergency) -> String {
    let mut o = Map::new();
    o.insert("startedAt".into(), json!(e.started_at));
    o.insert("wearing".into(), json!(e.wearing));
    o.insert("checkedSteps".into(), json!(e.checked_steps));
    o.insert("isActive".into(), json!(true));
    if let Some(id) = e.incident_id {
        o.insert("incidentId".into(), json!(id));
    }
    Value::Object(o).to_string()
}
