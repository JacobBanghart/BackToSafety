use serde::Deserialize;

/// The person being cared for: the `profile` row (spec/storage.md). Blank fields are None.
#[derive(Clone, Debug, Default, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Profile {
    pub name: String,
    pub nickname: Option<String>,
    pub date_of_birth: Option<String>,
    pub photo_uri: Option<String>,
    pub height: Option<String>,
    pub weight: Option<String>,
    pub hair_color: Option<String>,
    pub eye_color: Option<String>,
    pub identifying_marks: Option<String>,
    pub medical_conditions: Option<String>,
    pub medications: Option<String>,
    pub allergies: Option<String>,
    pub cognitive_status: Option<String>,
    pub dominant_hand: Option<String>,
    pub mobility_level: Option<String>,
    pub communication_preference: Option<String>,
    pub escalation_signs: Option<String>,
    pub deescalation_techniques: Option<String>,
    pub approach_guidance: Option<String>,
    pub likes: Option<String>,
    pub dislikes_triggers: Option<String>,
    pub safe_word: Option<String>,
    pub locative_device_info: Option<String>,
    pub id_bracelets: Option<String>,
    pub medic_alert_id: Option<String>,
    pub medic_alert_hotline: Option<String>,
}

/// A field counts as set when it isn't None or empty (the RN app's truthiness).
pub fn set(value: &Option<String>) -> Option<&str> {
    value.as_deref().filter(|v| !v.is_empty())
}
