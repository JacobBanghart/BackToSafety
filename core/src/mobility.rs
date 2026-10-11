//! Mobility is stored as comma-separated English option labels plus any "Other" free
//! text (spec/storage.md). Labels stay English in storage; `describe_mobility` translates
//! them for display.

use crate::i18n::Translator;
use serde_json::json;

pub const MOBILITY_OPTION_KEYS: [(&str, &str); 9] = [
    ("Walks independently", "mobilityOptions.walksIndependently"),
    ("Uses cane", "mobilityOptions.usesCane"),
    ("Uses walker", "mobilityOptions.usesWalker"),
    ("Manual wheelchair", "mobilityOptions.manualWheelchair"),
    (
        "Motorized wheelchair",
        "mobilityOptions.motorizedWheelchair",
    ),
    ("Mobility scooter", "mobilityOptions.mobilityScooter"),
    ("Bicycle", "mobilityOptions.bicycle"),
    ("Has vehicle", "mobilityOptions.hasVehicle"),
    ("Other", "mobilityOptions.other"),
];

/// The stored mobility value with known labels translated; custom text passes through.
pub fn describe_mobility(stored: &str, t: &Translator) -> String {
    stored
        .split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|token| {
            match MOBILITY_OPTION_KEYS
                .iter()
                .find(|(label, _)| *label == token)
            {
                Some((_, key)) => t.tv(key, &[("ns", json!("profile"))]),
                None => token.to_string(),
            }
        })
        .collect::<Vec<_>>()
        .join(", ")
}
