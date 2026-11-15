use crate::{formatters, phone};
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

/// The keyboard a profile text field asks for.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum ProfileKeyboard {
    Text,
    Number,
    Phone,
}

/// The profile's fields, defined once: [`Profile`], the `profile` row and both apps' screens read
/// and write them through this list. [`ProfileField::key`] is the [`Profile`] property's name in
/// camelCase, which is also the RN form key (testID `profile-field-<key>`) and the key of the
/// field's strings (`fields.<key>`, `fields.<key>Placeholder`, `fields.<key>Hint`). Adding a field
/// is an entry here plus the [`Profile`] and `ProfileEntity` fields; the `match`es in
/// [`Profile::get`] and `ProfileEntity::with` won't compile without it, and the profile field
/// test fails for a column with no entry.
///
/// The rest describes how the screens input a field. The date of birth has its own input (a date
/// picker beside the text), and [`ProfileField::DominantHand`] and [`ProfileField::MobilityLevel`]
/// are chosen, not typed ([`PROFILE_TEXT_FIELDS`] is the rest).
///
/// The person's name is `FullName` (Kotlin `FULL_NAME`, Swift `.fullName`), as the Kotlin core
/// named it so that it couldn't collide with an enum's own `name`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum ProfileField {
    FullName,
    Nickname,
    DateOfBirth,
    PhotoUri,
    Height,
    Weight,
    HairColor,
    EyeColor,
    IdentifyingMarks,
    MedicalConditions,
    Medications,
    Allergies,
    CognitiveStatus,
    DominantHand,
    MobilityLevel,
    CommunicationPreference,
    EscalationSigns,
    DeescalationTechniques,
    ApproachGuidance,
    Likes,
    DislikesTriggers,
    SafeWord,
    LocativeDeviceInfo,
    IdBracelets,
    MedicAlertId,
    MedicAlertHotline,
}

/// The fields typed into a text input: all but the two that are chosen from options.
pub const PROFILE_TEXT_FIELDS: [ProfileField; 24] = {
    let mut out = [ProfileField::FullName; 24];
    let mut i = 0;
    let mut j = 0;
    while i < ProfileField::ALL.len() {
        let f = ProfileField::ALL[i];
        if !matches!(f, ProfileField::DominantHand | ProfileField::MobilityLevel) {
            out[j] = f;
            j += 1;
        }
        i += 1;
    }
    out
};

impl ProfileField {
    /// Every field, in the order the screens show them.
    pub const ALL: [ProfileField; 26] = [
        Self::FullName,
        Self::Nickname,
        Self::DateOfBirth,
        Self::PhotoUri,
        Self::Height,
        Self::Weight,
        Self::HairColor,
        Self::EyeColor,
        Self::IdentifyingMarks,
        Self::MedicalConditions,
        Self::Medications,
        Self::Allergies,
        Self::CognitiveStatus,
        Self::DominantHand,
        Self::MobilityLevel,
        Self::CommunicationPreference,
        Self::EscalationSigns,
        Self::DeescalationTechniques,
        Self::ApproachGuidance,
        Self::Likes,
        Self::DislikesTriggers,
        Self::SafeWord,
        Self::LocativeDeviceInfo,
        Self::IdBracelets,
        Self::MedicAlertId,
        Self::MedicAlertHotline,
    ];

    /// The [`Profile`] property's camelCase name (the RN form key).
    pub fn as_key(self) -> &'static str {
        match self {
            Self::FullName => "name",
            Self::Nickname => "nickname",
            Self::DateOfBirth => "dateOfBirth",
            Self::PhotoUri => "photoUri",
            Self::Height => "height",
            Self::Weight => "weight",
            Self::HairColor => "hairColor",
            Self::EyeColor => "eyeColor",
            Self::IdentifyingMarks => "identifyingMarks",
            Self::MedicalConditions => "medicalConditions",
            Self::Medications => "medications",
            Self::Allergies => "allergies",
            Self::CognitiveStatus => "cognitiveStatus",
            Self::DominantHand => "dominantHand",
            Self::MobilityLevel => "mobilityLevel",
            Self::CommunicationPreference => "communicationPreference",
            Self::EscalationSigns => "escalationSigns",
            Self::DeescalationTechniques => "deescalationTechniques",
            Self::ApproachGuidance => "approachGuidance",
            Self::Likes => "likes",
            Self::DislikesTriggers => "dislikesTriggers",
            Self::SafeWord => "safeWord",
            Self::LocativeDeviceInfo => "locativeDeviceInfo",
            Self::IdBracelets => "idBracelets",
            Self::MedicAlertId => "medicAlertId",
            Self::MedicAlertHotline => "medicAlertHotline",
        }
    }

    /// The field whose key is `key`.
    pub fn from_key(key: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|f| f.as_key() == key)
    }

    /// The input is a text area rather than a single line.
    pub fn is_multiline(self) -> bool {
        matches!(
            self,
            Self::IdentifyingMarks
                | Self::MedicalConditions
                | Self::Medications
                | Self::Allergies
                | Self::CognitiveStatus
                | Self::CommunicationPreference
                | Self::EscalationSigns
                | Self::DeescalationTechniques
                | Self::ApproachGuidance
                | Self::Likes
                | Self::DislikesTriggers
                | Self::LocativeDeviceInfo
                | Self::IdBracelets
        )
    }

    /// The screens show the `fields.<key>Hint` line under the label.
    pub fn shows_hint(self) -> bool {
        matches!(
            self,
            Self::IdentifyingMarks
                | Self::CognitiveStatus
                | Self::EscalationSigns
                | Self::DeescalationTechniques
                | Self::Likes
                | Self::DislikesTriggers
                | Self::SafeWord
                | Self::LocativeDeviceInfo
                | Self::IdBracelets
                | Self::MedicAlertHotline
        )
    }

    pub fn input_keyboard(self) -> ProfileKeyboard {
        match self {
            Self::DateOfBirth | Self::Height | Self::Weight => ProfileKeyboard::Number,
            Self::MedicAlertHotline => ProfileKeyboard::Phone,
            _ => ProfileKeyboard::Text,
        }
    }

    /// Typed text starts each word with a capital (names); otherwise each sentence.
    pub fn capitalizes_words(self) -> bool {
        matches!(self, Self::FullName | Self::Nickname)
    }

    /// What the field's input does to typed text (profile.tsx formatFieldInput).
    pub fn format(self, value: &str) -> String {
        match self {
            Self::DateOfBirth => formatters::format_dob_input(value),
            Self::Height => formatters::format_height_input(value),
            Self::Weight => formatters::format_weight_input(value),
            Self::MedicAlertId => formatters::format_medic_alert_id_input(value),
            Self::MedicAlertHotline => phone::format_phone_input(value),
            _ => value.to_string(),
        }
    }
}

/// The field's description, through UniFFI: methods on the generated enum, named as the Kotlin
/// core's properties (`field.key()` for `field.key`).
#[uniffi::export]
impl ProfileField {
    pub fn key(&self) -> String {
        self.as_key().to_string()
    }

    pub fn multiline(&self) -> bool {
        self.is_multiline()
    }

    pub fn has_hint(&self) -> bool {
        self.shows_hint()
    }

    pub fn keyboard(&self) -> ProfileKeyboard {
        self.input_keyboard()
    }

    pub fn capitalize_words(&self) -> bool {
        self.capitalizes_words()
    }

    pub fn format_input(&self, value: String) -> String {
        self.format(&value)
    }
}

impl Profile {
    /// The value of `field` (None when unset).
    pub fn field(&self, field: ProfileField) -> Option<&str> {
        match field {
            ProfileField::FullName => Some(self.name.as_str()),
            ProfileField::Nickname => self.nickname.as_deref(),
            ProfileField::DateOfBirth => self.date_of_birth.as_deref(),
            ProfileField::PhotoUri => self.photo_uri.as_deref(),
            ProfileField::Height => self.height.as_deref(),
            ProfileField::Weight => self.weight.as_deref(),
            ProfileField::HairColor => self.hair_color.as_deref(),
            ProfileField::EyeColor => self.eye_color.as_deref(),
            ProfileField::IdentifyingMarks => self.identifying_marks.as_deref(),
            ProfileField::MedicalConditions => self.medical_conditions.as_deref(),
            ProfileField::Medications => self.medications.as_deref(),
            ProfileField::Allergies => self.allergies.as_deref(),
            ProfileField::CognitiveStatus => self.cognitive_status.as_deref(),
            ProfileField::DominantHand => self.dominant_hand.as_deref(),
            ProfileField::MobilityLevel => self.mobility_level.as_deref(),
            ProfileField::CommunicationPreference => self.communication_preference.as_deref(),
            ProfileField::EscalationSigns => self.escalation_signs.as_deref(),
            ProfileField::DeescalationTechniques => self.deescalation_techniques.as_deref(),
            ProfileField::ApproachGuidance => self.approach_guidance.as_deref(),
            ProfileField::Likes => self.likes.as_deref(),
            ProfileField::DislikesTriggers => self.dislikes_triggers.as_deref(),
            ProfileField::SafeWord => self.safe_word.as_deref(),
            ProfileField::LocativeDeviceInfo => self.locative_device_info.as_deref(),
            ProfileField::IdBracelets => self.id_bracelets.as_deref(),
            ProfileField::MedicAlertId => self.medic_alert_id.as_deref(),
            ProfileField::MedicAlertHotline => self.medic_alert_hotline.as_deref(),
        }
    }
}

/// `profile[field]` through UniFFI: `profile.get(field)` (Swift `profile.get(field:)`).
#[uniffi::export]
impl Profile {
    pub fn get(&self, field: ProfileField) -> Option<String> {
        self.field(field).map(str::to_string)
    }
}
