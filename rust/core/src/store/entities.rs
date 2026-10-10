//! The RN app's tables (spec/db-schema.json), in the shape Room manages: schema version 2.
//! Column names are unchanged, so MIGRATION_1_2 copies RN rows straight across. Enum columns
//! (role, category, risk_level, outcome, dominant_hand) are plain TEXT.

use crate::profile::Profile;
use rusqlite::{Row, params};

#[derive(Clone, Debug, PartialEq)]
pub struct ProfileEntity {
    pub id: i64,
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
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
}

impl Default for ProfileEntity {
    fn default() -> Self {
        ProfileEntity {
            id: 1,
            name: String::new(),
            nickname: None,
            date_of_birth: None,
            photo_uri: None,
            height: None,
            weight: None,
            hair_color: None,
            eye_color: None,
            identifying_marks: None,
            medical_conditions: None,
            medications: None,
            allergies: None,
            cognitive_status: None,
            dominant_hand: None,
            mobility_level: None,
            communication_preference: None,
            escalation_signs: None,
            deescalation_techniques: None,
            approach_guidance: None,
            likes: None,
            dislikes_triggers: None,
            safe_word: None,
            locative_device_info: None,
            id_bracelets: None,
            medic_alert_id: None,
            medic_alert_hotline: None,
            created_at: None,
            updated_at: None,
        }
    }
}

impl ProfileEntity {
    pub fn new(name: impl Into<String>) -> Self {
        ProfileEntity {
            name: name.into(),
            ..Default::default()
        }
    }

    pub(crate) fn from_row(r: &Row) -> rusqlite::Result<Self> {
        Ok(ProfileEntity {
            id: r.get("id")?,
            name: r.get("name")?,
            nickname: r.get("nickname")?,
            date_of_birth: r.get("date_of_birth")?,
            photo_uri: r.get("photo_uri")?,
            height: r.get("height")?,
            weight: r.get("weight")?,
            hair_color: r.get("hair_color")?,
            eye_color: r.get("eye_color")?,
            identifying_marks: r.get("identifying_marks")?,
            medical_conditions: r.get("medical_conditions")?,
            medications: r.get("medications")?,
            allergies: r.get("allergies")?,
            cognitive_status: r.get("cognitive_status")?,
            dominant_hand: r.get("dominant_hand")?,
            mobility_level: r.get("mobility_level")?,
            communication_preference: r.get("communication_preference")?,
            escalation_signs: r.get("escalation_signs")?,
            deescalation_techniques: r.get("deescalation_techniques")?,
            approach_guidance: r.get("approach_guidance")?,
            likes: r.get("likes")?,
            dislikes_triggers: r.get("dislikes_triggers")?,
            safe_word: r.get("safe_word")?,
            locative_device_info: r.get("locative_device_info")?,
            id_bracelets: r.get("id_bracelets")?,
            medic_alert_id: r.get("medic_alert_id")?,
            medic_alert_hotline: r.get("medic_alert_hotline")?,
            created_at: r.get("created_at")?,
            updated_at: r.get("updated_at")?,
        })
    }

    /// Room's @Upsert: insert, or replace the row with the same id.
    pub(crate) fn save(&self, c: &rusqlite::Connection) -> rusqlite::Result<()> {
        c.execute(
            "INSERT OR REPLACE INTO profile (id, name, nickname, date_of_birth, photo_uri, height, weight, \
             hair_color, eye_color, identifying_marks, medical_conditions, medications, allergies, \
             cognitive_status, dominant_hand, mobility_level, communication_preference, escalation_signs, \
             deescalation_techniques, approach_guidance, likes, dislikes_triggers, safe_word, \
             locative_device_info, id_bracelets, medic_alert_id, medic_alert_hotline, created_at, updated_at) \
             VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23,?24,?25,?26,?27,?28,?29)",
            params![
                self.id,
                self.name,
                self.nickname,
                self.date_of_birth,
                self.photo_uri,
                self.height,
                self.weight,
                self.hair_color,
                self.eye_color,
                self.identifying_marks,
                self.medical_conditions,
                self.medications,
                self.allergies,
                self.cognitive_status,
                self.dominant_hand,
                self.mobility_level,
                self.communication_preference,
                self.escalation_signs,
                self.deescalation_techniques,
                self.approach_guidance,
                self.likes,
                self.dislikes_triggers,
                self.safe_word,
                self.locative_device_info,
                self.id_bracelets,
                self.medic_alert_id,
                self.medic_alert_hotline,
                self.created_at,
                self.updated_at,
            ],
        )?;
        Ok(())
    }

    pub fn to_profile(&self) -> Profile {
        Profile {
            name: self.name.clone(),
            nickname: self.nickname.clone(),
            date_of_birth: self.date_of_birth.clone(),
            photo_uri: self.photo_uri.clone(),
            height: self.height.clone(),
            weight: self.weight.clone(),
            hair_color: self.hair_color.clone(),
            eye_color: self.eye_color.clone(),
            identifying_marks: self.identifying_marks.clone(),
            medical_conditions: self.medical_conditions.clone(),
            medications: self.medications.clone(),
            allergies: self.allergies.clone(),
            cognitive_status: self.cognitive_status.clone(),
            dominant_hand: self.dominant_hand.clone(),
            mobility_level: self.mobility_level.clone(),
            communication_preference: self.communication_preference.clone(),
            escalation_signs: self.escalation_signs.clone(),
            deescalation_techniques: self.deescalation_techniques.clone(),
            approach_guidance: self.approach_guidance.clone(),
            likes: self.likes.clone(),
            dislikes_triggers: self.dislikes_triggers.clone(),
            safe_word: self.safe_word.clone(),
            locative_device_info: self.locative_device_info.clone(),
            id_bracelets: self.id_bracelets.clone(),
            medic_alert_id: self.medic_alert_id.clone(),
            medic_alert_hotline: self.medic_alert_hotline.clone(),
        }
    }

    /// Sets one field by its Profile property name (camelCase), as `Store::update_profile`
    /// does. None clears it (the name becomes empty). Errors on an unknown field.
    pub(crate) fn set_field(&mut self, field: &str, value: Option<&str>) -> Result<(), String> {
        let v = value.map(str::to_string);
        match field {
            "name" => self.name = v.unwrap_or_default(),
            "nickname" => self.nickname = v,
            "dateOfBirth" => self.date_of_birth = v,
            "photoUri" => self.photo_uri = v,
            "height" => self.height = v,
            "weight" => self.weight = v,
            "hairColor" => self.hair_color = v,
            "eyeColor" => self.eye_color = v,
            "identifyingMarks" => self.identifying_marks = v,
            "medicalConditions" => self.medical_conditions = v,
            "medications" => self.medications = v,
            "allergies" => self.allergies = v,
            "cognitiveStatus" => self.cognitive_status = v,
            "dominantHand" => self.dominant_hand = v,
            "mobilityLevel" => self.mobility_level = v,
            "communicationPreference" => self.communication_preference = v,
            "escalationSigns" => self.escalation_signs = v,
            "deescalationTechniques" => self.deescalation_techniques = v,
            "approachGuidance" => self.approach_guidance = v,
            "likes" => self.likes = v,
            "dislikesTriggers" => self.dislikes_triggers = v,
            "safeWord" => self.safe_word = v,
            "locativeDeviceInfo" => self.locative_device_info = v,
            "idBracelets" => self.id_bracelets = v,
            "medicAlertId" => self.medic_alert_id = v,
            "medicAlertHotline" => self.medic_alert_hotline = v,
            other => return Err(other.to_string()),
        }
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct ContactEntity {
    /// 0 until stored (the database assigns it).
    pub id: i64,
    pub name: String,
    pub phone: String,
    pub relationship: Option<String>,
    pub role: Option<String>,
    pub address: Option<String>,
    pub notify_on_emergency: bool,
    pub share_medical_info: bool,
    pub notes: Option<String>,
    pub sort_order: i32,
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
}

impl Default for ContactEntity {
    fn default() -> Self {
        ContactEntity {
            id: 0,
            name: String::new(),
            phone: String::new(),
            relationship: None,
            role: None,
            address: None,
            notify_on_emergency: true,
            share_medical_info: false,
            notes: None,
            sort_order: 0,
            created_at: None,
            updated_at: None,
        }
    }
}

impl ContactEntity {
    pub fn new(name: impl Into<String>, phone: impl Into<String>) -> Self {
        ContactEntity {
            name: name.into(),
            phone: phone.into(),
            ..Default::default()
        }
    }

    pub(crate) fn from_row(r: &Row) -> rusqlite::Result<Self> {
        Ok(ContactEntity {
            id: r.get("id")?,
            name: r.get("name")?,
            phone: r.get("phone")?,
            relationship: r.get("relationship")?,
            role: r.get("role")?,
            address: r.get("address")?,
            notify_on_emergency: r.get("notify_on_emergency")?,
            share_medical_info: r.get("share_medical_info")?,
            notes: r.get("notes")?,
            sort_order: r.get("sort_order")?,
            created_at: r.get("created_at")?,
            updated_at: r.get("updated_at")?,
        })
    }
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct DestinationEntity {
    /// 0 until stored (the database assigns it).
    pub id: i64,
    pub name: String,
    pub address: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub category: Option<String>,
    pub reason: Option<String>,
    pub distance_from_home: Option<String>,
    pub risk_level: Option<String>,
    pub notes: Option<String>,
    pub sort_order: i32,
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
}

impl DestinationEntity {
    pub fn new(name: impl Into<String>) -> Self {
        DestinationEntity {
            name: name.into(),
            ..Default::default()
        }
    }

    pub(crate) fn from_row(r: &Row) -> rusqlite::Result<Self> {
        Ok(DestinationEntity {
            id: r.get("id")?,
            name: r.get("name")?,
            address: r.get("address")?,
            latitude: r.get("latitude")?,
            longitude: r.get("longitude")?,
            category: r.get("category")?,
            reason: r.get("reason")?,
            distance_from_home: r.get("distance_from_home")?,
            risk_level: r.get("risk_level")?,
            notes: r.get("notes")?,
            sort_order: r.get("sort_order")?,
            created_at: r.get("created_at")?,
            updated_at: r.get("updated_at")?,
        })
    }
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct IncidentEntity {
    /// 0 until stored (the database assigns it).
    pub id: i64,
    pub started_at: String,
    pub ended_at: Option<String>,
    pub outcome: Option<String>,
    pub last_seen_lat: Option<f64>,
    pub last_seen_lon: Option<f64>,
    pub last_seen_accuracy: Option<f64>,
    pub found_lat: Option<f64>,
    pub found_lon: Option<f64>,
    pub found_location_name: Option<String>,
    pub weather: Option<String>,
    pub time_of_day: Option<String>,
    pub trigger_identified: Option<String>,
    pub wearing: Option<String>,
    /// JSON array of checked step IDs.
    pub areas_checked: Option<String>,
    pub people_contacted: Option<String>,
    pub notes: Option<String>,
    pub created_at: Option<String>,
}

impl IncidentEntity {
    pub fn new(started_at: impl Into<String>) -> Self {
        IncidentEntity {
            started_at: started_at.into(),
            ..Default::default()
        }
    }

    pub(crate) fn from_row(r: &Row) -> rusqlite::Result<Self> {
        Ok(IncidentEntity {
            id: r.get("id")?,
            started_at: r.get("started_at")?,
            ended_at: r.get("ended_at")?,
            outcome: r.get("outcome")?,
            last_seen_lat: r.get("last_seen_lat")?,
            last_seen_lon: r.get("last_seen_lon")?,
            last_seen_accuracy: r.get("last_seen_accuracy")?,
            found_lat: r.get("found_lat")?,
            found_lon: r.get("found_lon")?,
            found_location_name: r.get("found_location_name")?,
            weather: r.get("weather")?,
            time_of_day: r.get("time_of_day")?,
            trigger_identified: r.get("trigger_identified")?,
            wearing: r.get("wearing")?,
            areas_checked: r.get("areas_checked")?,
            people_contacted: r.get("people_contacted")?,
            notes: r.get("notes")?,
            created_at: r.get("created_at")?,
        })
    }
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct SafetyCheckEntity {
    pub id: i64,
    pub category: Option<String>,
    pub item_key: String,
    pub completed: bool,
    pub completed_at: Option<String>,
    pub notes: Option<String>,
}

impl SafetyCheckEntity {
    pub(crate) fn from_row(r: &Row) -> rusqlite::Result<Self> {
        Ok(SafetyCheckEntity {
            id: r.get("id")?,
            category: r.get("category")?,
            item_key: r.get("item_key")?,
            completed: r.get("completed")?,
            completed_at: r.get("completed_at")?,
            notes: r.get("notes")?,
        })
    }
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct SettingEntity {
    pub key: String,
    pub value: Option<String>,
    pub updated_at: Option<String>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct OnboardingStepEntity {
    pub step: String,
    pub completed: bool,
    pub completed_at: Option<String>,
    pub skipped: bool,
}

impl OnboardingStepEntity {
    pub(crate) fn from_row(r: &Row) -> rusqlite::Result<Self> {
        Ok(OnboardingStepEntity {
            step: r.get("step")?,
            completed: r.get("completed")?,
            completed_at: r.get("completed_at")?,
            skipped: r.get("skipped")?,
        })
    }
}
