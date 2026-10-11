use serde::Deserialize;

/// The person being cared for: the `profile` row (spec/storage.md). Blank fields are None.
#[derive(Clone, Debug, Default, PartialEq, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase", default)]
pub struct Profile {
    pub name: String,
    #[uniffi(default = None)]
    pub nickname: Option<String>,
    #[uniffi(default = None)]
    pub date_of_birth: Option<String>,
    #[uniffi(default = None)]
    pub photo_uri: Option<String>,
    #[uniffi(default = None)]
    pub height: Option<String>,
    #[uniffi(default = None)]
    pub weight: Option<String>,
    #[uniffi(default = None)]
    pub hair_color: Option<String>,
    #[uniffi(default = None)]
    pub eye_color: Option<String>,
    #[uniffi(default = None)]
    pub identifying_marks: Option<String>,
    #[uniffi(default = None)]
    pub medical_conditions: Option<String>,
    #[uniffi(default = None)]
    pub medications: Option<String>,
    #[uniffi(default = None)]
    pub allergies: Option<String>,
    #[uniffi(default = None)]
    pub cognitive_status: Option<String>,
    #[uniffi(default = None)]
    pub dominant_hand: Option<String>,
    #[uniffi(default = None)]
    pub mobility_level: Option<String>,
    #[uniffi(default = None)]
    pub communication_preference: Option<String>,
    #[uniffi(default = None)]
    pub escalation_signs: Option<String>,
    #[uniffi(default = None)]
    pub deescalation_techniques: Option<String>,
    #[uniffi(default = None)]
    pub approach_guidance: Option<String>,
    #[uniffi(default = None)]
    pub likes: Option<String>,
    #[uniffi(default = None)]
    pub dislikes_triggers: Option<String>,
    #[uniffi(default = None)]
    pub safe_word: Option<String>,
    #[uniffi(default = None)]
    pub locative_device_info: Option<String>,
    #[uniffi(default = None)]
    pub id_bracelets: Option<String>,
    #[uniffi(default = None)]
    pub medic_alert_id: Option<String>,
    #[uniffi(default = None)]
    pub medic_alert_hotline: Option<String>,
}

/// A field counts as set when it isn't None or empty (the RN app's truthiness).
pub fn set(value: &Option<String>) -> Option<&str> {
    value.as_deref().filter(|v| !v.is_empty())
}
