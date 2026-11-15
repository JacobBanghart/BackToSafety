//! When the countdown alerts are due, what to schedule on the phone while the emergency
//! screen isn't showing, what to catch up on when it shows again, and the properties of the
//! metrics around them. Held to spec/vectors/alerts.json.

use crate::analytics::{Analytics, AnalyticsEvent};
use crate::emergency::{CountdownAlert, SEARCH_WINDOW_SECONDS, WARNING_AT_SECONDS};
use crate::profile::{Profile, ProfileField};
use serde_json::{Map, Value, json};

pub type Properties = Map<String, Value>;

fn props<const N: usize>(pairs: [(&str, Value); N]) -> Properties {
    pairs.into_iter().map(|(k, v)| (k.to_string(), v)).collect()
}

/// When `alert` is due for an emergency started at `started_at_ms`: the moment
/// [countdown_alerts] fires it.
pub fn alert_due_at_ms(alert: CountdownAlert, started_at_ms: i64) -> i64 {
    match alert {
        // The warning fires when fewer than WARNING_AT_SECONDS remain: one second after the mark.
        CountdownAlert::Warning => {
            started_at_ms + (SEARCH_WINDOW_SECONDS - WARNING_AT_SECONDS + 1) * 1000
        }
        CountdownAlert::Expired => started_at_ms + SEARCH_WINDOW_SECONDS * 1000,
    }
}

/// An alert for the phone to deliver as a notification at `fire_at_ms`.
#[derive(Clone, Copy, Debug, PartialEq, uniffi::Record)]
pub struct ScheduledAlert {
    pub alert: CountdownAlert,
    pub fire_at_ms: i64,
}

/// The alerts to schedule when the emergency screen stops showing at `now_ms`: those not
/// yet due, earliest first.
pub fn alerts_to_schedule(started_at_ms: i64, now_ms: i64) -> Vec<ScheduledAlert> {
    CountdownAlert::ALL
        .into_iter()
        .map(|alert| ScheduledAlert {
            alert,
            fire_at_ms: alert_due_at_ms(alert, started_at_ms),
        })
        .filter(|s| s.fire_at_ms > now_ms)
        .collect()
}

/// The alert to show when the emergency screen shows again at `now_ms`, after being away
/// since `away_from_ms`: the most serious one that came due in between, or None.
pub fn catch_up_alert(
    started_at_ms: i64,
    away_from_ms: i64,
    now_ms: i64,
) -> Option<CountdownAlert> {
    [CountdownAlert::Expired, CountdownAlert::Warning]
        .into_iter()
        .find(|&a| (away_from_ms + 1..=now_ms).contains(&alert_due_at_ms(a, started_at_ms)))
}

/// How a countdown alert reached the caregiver.
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum AlertDelivery {
    InApp,
    CatchUp,
}

impl AlertDelivery {
    pub fn key(self) -> &'static str {
        match self {
            AlertDelivery::InApp => "in_app",
            AlertDelivery::CatchUp => "catch_up",
        }
    }
}

pub fn countdown_alert_properties(alert: CountdownAlert, delivery: AlertDelivery) -> Properties {
    props([
        ("kind", json!(alert.key())),
        ("delivery", json!(delivery.key())),
    ])
}

pub fn emergency_resumed_properties(
    away_from_ms: i64,
    now_ms: i64,
    caught_up: Option<CountdownAlert>,
) -> Properties {
    props([
        ("away_s", json!(((now_ms - away_from_ms) / 1000).max(0))),
        ("catch_up", json!(caught_up.map(CountdownAlert::key))),
    ])
}

pub fn emergency_ended_properties(
    started_at_ms: i64,
    now_ms: i64,
    checked_count: usize,
) -> Properties {
    props([
        ("checked_count", json!(checked_count)),
        (
            "duration_s",
            json!(((now_ms - started_at_ms) / 1000).max(0)),
        ),
    ])
}

fn filled(v: &Option<String>) -> bool {
    v.as_deref().is_some_and(|s| !s.trim().is_empty())
}

/// emergency_911_called's properties: the dialer opened at `now_ms`, `started_at_ms` into the
/// emergency. `seconds_elapsed` is the real time since the start, so it goes past the 15-minute
/// window (the countdown stops at zero; this doesn't).
pub fn emergency_911_called_properties(
    started_at_ms: i64,
    now_ms: i64,
    checked_count: usize,
) -> Properties {
    props([
        (
            "seconds_elapsed",
            json!(((now_ms - started_at_ms) / 1000).max(0)),
        ),
        ("checked_count", json!(checked_count)),
    ])
}

/// How ready the info sheet is, sent with app_ready: what's filled in before anyone needs it.
/// `contacts_alerted` counts contacts with notify-on-emergency on.
pub fn readiness_properties(
    profile: Option<&Profile>,
    contacts: usize,
    contacts_alerted: usize,
    places: usize,
) -> Properties {
    // The name is required, so it says nothing about how much is filled in; "unknown" is the
    // hand's unset value.
    let fields = profile.map_or(0, |p| {
        ProfileField::ALL
            .into_iter()
            .filter(|&field| {
                let value = p.field(field);
                field != ProfileField::FullName
                    && !(field == ProfileField::DominantHand && value == Some("unknown"))
                    && value.is_some_and(|v| !v.trim().is_empty())
            })
            .count()
    });
    props([
        ("has_profile", json!(profile.is_some())),
        (
            "has_photo",
            json!(profile.is_some_and(|p| filled(&p.photo_uri))),
        ),
        (
            "has_medical",
            json!(profile.is_some_and(|p| filled(&p.medical_conditions))),
        ),
        ("profile_fields_filled", json!(fields)),
        ("contacts_count", json!(contacts)),
        ("alert_contacts_count", json!(contacts_alerted)),
        ("places_count", json!(places)),
    ])
}

/// What happened to the alert text for the alert circle (emergency_sms_result).
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum SmsResult {
    Sent,
    Cancelled,
    Failed,
    /// The device can't send texts.
    Unavailable,
    /// Handed to the messaging app, which doesn't report back (Android).
    HandedOff,
}

impl SmsResult {
    pub const ALL: [SmsResult; 5] = [
        Self::Sent,
        Self::Cancelled,
        Self::Failed,
        Self::Unavailable,
        Self::HandedOff,
    ];

    pub fn key(self) -> &'static str {
        match self {
            Self::Sent => "sent",
            Self::Cancelled => "cancelled",
            Self::Failed => "failed",
            Self::Unavailable => "unavailable",
            Self::HandedOff => "handed_off",
        }
    }
}

/// Whether the alert text may have reached the contacts: what emergency_contacts_alerted means.
/// True when the composer sent it (iOS) or the messaging app has it (Android, which doesn't say
/// more); false when the user cancelled, it failed, or the device can't text. Both apps decide
/// by this, so the event counts the same on each.
pub fn contacts_alerted(result: SmsResult) -> bool {
    matches!(result, SmsResult::Sent | SmsResult::HandedOff)
}

/// emergency_sms_result's properties.
pub fn sms_result_properties(result: SmsResult, recipients: usize) -> Properties {
    props([
        ("result", json!(result.key())),
        ("recipient_count", json!(recipients)),
    ])
}

/// Reports what became of the alert text: emergency_contacts_alerted if it [`contacts_alerted`],
/// then emergency_sms_result with the result itself (which says more than the first does).
pub fn track_sms_result(result: SmsResult, recipients: usize) {
    if contacts_alerted(result) {
        Analytics::track(
            AnalyticsEvent::EmergencyContactsAlerted,
            props([("recipient_count", json!(recipients))]),
        );
    }
    Analytics::track(
        AnalyticsEvent::EmergencySmsResult,
        sms_result_properties(result, recipients),
    );
}

/// Which number a dial was for (dial_failed).
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum DialTarget {
    Emergency,
    Contact,
    Medicalert,
}

impl DialTarget {
    pub const ALL: [DialTarget; 3] = [Self::Emergency, Self::Contact, Self::Medicalert];

    pub fn key(self) -> &'static str {
        match self {
            Self::Emergency => "emergency",
            Self::Contact => "contact",
            Self::Medicalert => "medicalert",
        }
    }
}

pub fn dial_failed_properties(target: DialTarget, screen: &str) -> Properties {
    props([("target", json!(target.key())), ("screen", json!(screen))])
}

/// save_failed's properties. `error_type` is the error's type name, never its message.
pub fn save_failed_properties(screen: &str, action: &str, error_type: Option<&str>) -> Properties {
    props([
        ("screen", json!(screen)),
        ("action", json!(action)),
        ("error", json!(error_type.unwrap_or("unknown"))),
    ])
}
