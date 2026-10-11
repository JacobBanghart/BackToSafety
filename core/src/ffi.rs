//! The UniFFI surface: what `android/app` and `ios` use from the core. Records and
//! enums are derived where they're defined; this module holds what needs a different shape to
//! cross the boundary (functions with `&[..]`, closures, `usize`, dates, JSON values) and the
//! foreign traits the apps implement (`AnalyticsSink`, and `AlertScheduler` in
//! `emergency_away`).
//!
//! Rules of the surface, so both platforms can wrap it thinly:
//!
//! - Integer types are `i32` where Kotlin's core used `Int` and `i64` where it used `Long`
//!   (no unsigned types: they'd turn into `UInt`/`ULong`).
//! - Property bags (analytics properties, translation variables) are `{string -> PropValue}`.
//! - Everything is synchronous; the Store takes a lock around its SQLite connection. The apps
//!   call it off the main thread (Dispatchers.IO / a Task).
//! - Calls that take a translator take the `Translator` object, not a function.

use crate::age;
use crate::alerts::{self, AlertDelivery, DialTarget, Properties, ScheduledAlert, SmsResult};
use crate::analytics::{Analytics, AnalyticsEvent};
use crate::clock::AppClock;
use crate::emergency::{self, ActiveEmergency, ChecklistStep, CountdownAlert};
use crate::emergency_away::{AlertScheduler, EmergencyAway};
use crate::formatters;
use crate::i18n::{Translations, Translator};
use crate::mobility;
use crate::phone;
use crate::photo;
use crate::profile::Profile;
use crate::readout::{self, VehicleCheckKind};
use crate::startup::{self, AppReady};
use crate::store::{
    self, ContactEntity, DestinationEntity, IncidentEntity, OnboardingStepEntity, ProfileEntity,
    SafetyCheckEntity, Store, StoreError,
};
use chrono::{Datelike, NaiveDate};
use serde_json::Value;
use std::collections::HashMap;
use std::sync::Arc;

// ---------------------------------------------------------------------------------------------
// Property values

/// A value in a property bag: what an analytics property or a translation variable can be.
/// `Null` properties are dropped by the app's sink, as the PostHog calls do.
#[derive(Clone, Debug, PartialEq, uniffi::Enum)]
pub enum PropValue {
    Null,
    Bool { value: bool },
    Integer { value: i64 },
    Decimal { value: f64 },
    Text { value: String },
}

impl From<&Value> for PropValue {
    fn from(v: &Value) -> Self {
        match v {
            Value::Null => PropValue::Null,
            Value::Bool(b) => PropValue::Bool { value: *b },
            Value::Number(n) => match n.as_i64() {
                Some(i) => PropValue::Integer { value: i },
                None => PropValue::Decimal {
                    value: n.as_f64().unwrap_or(0.0),
                },
            },
            Value::String(s) => PropValue::Text { value: s.clone() },
            other => PropValue::Text {
                value: other.to_string(),
            },
        }
    }
}

impl From<PropValue> for Value {
    fn from(v: PropValue) -> Self {
        match v {
            PropValue::Null => Value::Null,
            PropValue::Bool { value } => Value::Bool(value),
            PropValue::Integer { value } => Value::from(value),
            PropValue::Decimal { value } => Value::from(value),
            PropValue::Text { value } => Value::String(value),
        }
    }
}

fn props_out(p: Properties) -> HashMap<String, PropValue> {
    p.iter().map(|(k, v)| (k.clone(), v.into())).collect()
}

fn props_in(p: HashMap<String, PropValue>) -> Properties {
    p.into_iter().map(|(k, v)| (k, v.into())).collect()
}

// ---------------------------------------------------------------------------------------------
// Errors

/// Everything the Store can fail with. The messages are for logs. The analytics `error`
/// property takes the variant name ([`StoreError::type_name`] on the Rust side).
#[derive(Debug, uniffi::Error)]
pub enum StoreFailure {
    Sqlite {
        detail: String,
    },
    Io {
        detail: String,
    },
    /// `update_profile` was given a field that isn't a Profile property.
    UnknownProfileField {
        field: String,
    },
    /// The file was written by a newer schema than this build knows.
    NewerDatabase {
        version: i32,
    },
}

impl std::fmt::Display for StoreFailure {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            StoreFailure::Sqlite { detail } | StoreFailure::Io { detail } => f.write_str(detail),
            StoreFailure::UnknownProfileField { field } => {
                write!(f, "unknown profile field: {field}")
            }
            StoreFailure::NewerDatabase { version } => {
                write!(f, "database is from a newer version ({version})")
            }
        }
    }
}

impl From<StoreError> for StoreFailure {
    fn from(e: StoreError) -> Self {
        match e {
            StoreError::Sqlite(_) => StoreFailure::Sqlite {
                detail: e.to_string(),
            },
            StoreError::Io(_) => StoreFailure::Io {
                detail: e.to_string(),
            },
            StoreError::UnknownProfileField(field) => StoreFailure::UnknownProfileField { field },
            StoreError::NewerDatabase(version) => StoreFailure::NewerDatabase { version },
        }
    }
}

type FfiResult<T> = Result<T, StoreFailure>;

// ---------------------------------------------------------------------------------------------
// Analytics

/// The app's analytics destination (the PostHog SDK). Screens arrive as the name `$screen`
/// with the route in `$screen_name`.
#[uniffi::export(with_foreign)]
pub trait AnalyticsSink: Send + Sync {
    fn on_event(&self, name: String, properties: HashMap<String, PropValue>);
}

/// Replaces the sink; None goes back to dropping everything (no PostHog key).
#[uniffi::export]
pub fn analytics_set_sink(sink: Option<Arc<dyn AnalyticsSink>>) {
    match sink {
        Some(sink) => Analytics::set_sink(move |name, properties| {
            sink.on_event(name.to_string(), props_out(properties.clone()))
        }),
        None => Analytics::clear_sink(),
    }
}

#[uniffi::export]
pub fn analytics_track(event: AnalyticsEvent, properties: HashMap<String, PropValue>) {
    Analytics::track(event, props_in(properties));
}

#[uniffi::export]
pub fn analytics_screen(path: String, properties: HashMap<String, PropValue>) {
    Analytics::screen(&path, props_in(properties));
}

/// The name a screen view reaches the sink under.
#[uniffi::export]
pub fn analytics_screen_event_name() -> String {
    crate::analytics::SCREEN.to_string()
}

/// The event's name as dashboards see it.
#[uniffi::export]
pub fn analytics_event_wire_name(event: AnalyticsEvent) -> String {
    event.wire_name().to_string()
}

// ---------------------------------------------------------------------------------------------
// Clock

/// The app's "now" in epoch milliseconds (frozen or shifted when test seams are on).
#[uniffi::export]
pub fn app_clock_now_ms() -> i64 {
    AppClock::now_ms()
}

/// Turns the debug clock seam on; builds without test seams leave it off.
#[uniffi::export]
pub fn app_clock_set_test_seams(enabled: bool) {
    AppClock::set_test_seams(enabled);
}

#[uniffi::export]
pub fn app_clock_advance(ms: i64) {
    AppClock::advance(ms);
}

#[uniffi::export]
pub fn app_clock_freeze(at_ms: i64) {
    AppClock::freeze(at_ms);
}

/// Applies a `backtosafety://debug/clock?at=<ISO>&advance=<seconds>` link; false if `uri`
/// isn't one.
#[uniffi::export]
pub fn app_clock_handle_link(uri: String) -> bool {
    AppClock::handle_link(&uri)
}

// ---------------------------------------------------------------------------------------------
// Translations

#[uniffi::export]
impl Translations {
    /// From the locale files' text: locale -> namespace -> JSON.
    #[uniffi::constructor(name = "from_json")]
    pub fn ffi_from_json(files: HashMap<String, HashMap<String, String>>) -> Arc<Translations> {
        Arc::new(Translations::from_json(files.iter().flat_map(
            |(locale, ns)| {
                ns.iter()
                    .map(move |(ns, text)| (locale.as_str(), ns.as_str(), text.as_str()))
            },
        )))
    }

    /// `t` bound to a locale and a default namespace.
    #[uniffi::method(name = "translator")]
    pub fn ffi_translator(self: Arc<Self>, locale: String, namespace: String) -> Arc<Translator> {
        Arc::new(self.translator(&locale, &namespace))
    }
}

#[uniffi::export]
impl Translator {
    /// `t(key)` with no variables.
    #[uniffi::method(name = "t")]
    pub fn ffi_t(&self, key: String) -> String {
        self.t(&key)
    }

    /// `t(key, vars)`: `{{name}}` placeholders; `count` picks the plural, `ns` another
    /// namespace and `defaultValue` stands in for a missing key.
    #[uniffi::method(name = "tv")]
    pub fn ffi_tv(&self, key: String, vars: HashMap<String, PropValue>) -> String {
        let vars: Vec<(String, Value)> = vars.into_iter().map(|(k, v)| (k, v.into())).collect();
        let vars: Vec<(&str, Value)> = vars.iter().map(|(k, v)| (k.as_str(), v.clone())).collect();
        self.tv(&key, &vars)
    }

    #[uniffi::method(name = "locale")]
    pub fn ffi_locale(&self) -> String {
        self.locale.clone()
    }
}

// ---------------------------------------------------------------------------------------------
// Dates, age, formatters, phone

/// A calendar date (kotlinx-datetime's LocalDate / Swift's DateComponents).
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Record)]
pub struct Date {
    pub year: i32,
    pub month: i32,
    pub day: i32,
}

impl From<NaiveDate> for Date {
    fn from(d: NaiveDate) -> Self {
        Date {
            year: d.year(),
            month: d.month() as i32,
            day: d.day() as i32,
        }
    }
}

impl Date {
    fn to_naive(self) -> Option<NaiveDate> {
        NaiveDate::from_ymd_opt(
            self.year,
            u32::try_from(self.month).ok()?,
            u32::try_from(self.day).ok()?,
        )
    }
}

#[uniffi::export]
pub fn parse_dob(value: String) -> Option<Date> {
    age::parse_dob(&value).map(Date::from)
}

/// Whole years between `dob` and `today`; None if the DOB is unparseable or in the future.
#[uniffi::export]
pub fn age_on(dob: String, today: Date) -> Option<i32> {
    age::age_on(&dob, today.to_naive()?)
}

#[uniffi::export]
pub fn format_dob(date: Date) -> String {
    date.to_naive().map(age::format_dob).unwrap_or_default()
}

#[uniffi::export]
pub fn format_height_input(value: String) -> String {
    formatters::format_height_input(&value)
}

#[uniffi::export]
pub fn format_weight_input(value: String) -> String {
    formatters::format_weight_input(&value)
}

#[uniffi::export]
pub fn format_medic_alert_id_input(value: String) -> String {
    formatters::format_medic_alert_id_input(&value)
}

#[uniffi::export]
pub fn format_dob_input(value: String) -> String {
    formatters::format_dob_input(&value)
}

#[uniffi::export]
pub fn format_phone_number(phone: String) -> String {
    phone::format_phone_number(&phone)
}

#[uniffi::export]
pub fn format_phone_input(value: String) -> String {
    phone::format_phone_input(&value)
}

#[uniffi::export]
pub fn strip_phone_formatting(phone: String) -> String {
    phone::strip_phone_formatting(&phone)
}

#[uniffi::export]
pub fn normalize_sms_recipient(phone: String) -> String {
    phone::normalize_sms_recipient(&phone)
}

#[uniffi::export]
pub fn normalize_unique_sms_recipients(phones: Vec<String>) -> Vec<String> {
    phone::normalize_unique_sms_recipients(&phones)
}

/// The file name at the end of a stored photo URI.
#[uniffi::export]
pub fn photo_file_name(uri: String) -> Option<String> {
    photo::photo_file_name(&uri)
}

// ---------------------------------------------------------------------------------------------
// Mobility and readout

/// A mobility option: the English label that's stored, and its translation key (profile
/// namespace).
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct MobilityOption {
    pub label: String,
    pub key: String,
}

/// The options in display order.
#[uniffi::export]
pub fn mobility_options() -> Vec<MobilityOption> {
    mobility::MOBILITY_OPTION_KEYS
        .iter()
        .map(|(label, key)| MobilityOption {
            label: label.to_string(),
            key: key.to_string(),
        })
        .collect()
}

#[uniffi::export]
pub fn describe_mobility(stored: String, t: Arc<Translator>) -> String {
    mobility::describe_mobility(&stored, &t)
}

/// What the info sheet is built from; `last_seen_time` arrives already formatted.
#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct ReadoutInput {
    pub profile: Profile,
    #[uniffi(default = None)]
    pub last_seen_time: Option<String>,
    /// For the age line; screens pass the current date.
    pub today: Date,
}

impl ReadoutInput {
    fn to_core(&self) -> readout::ReadoutInput {
        readout::ReadoutInput {
            profile: self.profile.clone(),
            last_seen_time: self.last_seen_time.clone(),
            // An impossible date has no age line, like a missing one.
            today: self
                .today
                .to_naive()
                .unwrap_or(NaiveDate::from_ymd_opt(1970, 1, 1).expect("valid")),
        }
    }
}

#[uniffi::export]
pub fn needs_vehicle_check(mobility_level: Option<String>) -> bool {
    readout::needs_vehicle_check(mobility_level.as_deref())
}

#[uniffi::export]
pub fn vehicle_check_kind(mobility_level: String) -> VehicleCheckKind {
    readout::vehicle_check_kind(&mobility_level)
}

#[uniffi::export]
pub fn vehicle_check_kind_key(kind: VehicleCheckKind) -> String {
    kind.key().to_string()
}

#[uniffi::export]
pub fn describe_appearance(profile: Profile, t: Arc<Translator>) -> String {
    readout::describe_appearance(&profile, &t)
}

#[uniffi::export]
pub fn describe_important_details(profile: Profile, t: Arc<Translator>) -> String {
    readout::describe_important_details(&profile, &t)
}

/// `t` is bound to the `readout` namespace.
#[uniffi::export]
pub fn build_copy_block(input: ReadoutInput, t: Arc<Translator>) -> String {
    readout::build_copy_block(&input.to_core(), &t)
}

#[uniffi::export]
pub fn build_script(input: ReadoutInput, t: Arc<Translator>) -> String {
    readout::build_script(&input.to_core(), &t)
}

#[uniffi::export]
pub fn missing_script_details(input: ReadoutInput, t: Arc<Translator>) -> Vec<String> {
    readout::missing_script_details(&input.to_core(), &t)
}

// ---------------------------------------------------------------------------------------------
// Emergency and alerts

/// The search window, in seconds (15 minutes).
#[uniffi::export]
pub fn search_window_seconds() -> i32 {
    emergency::SEARCH_WINDOW_SECONDS as i32
}

#[uniffi::export]
pub fn warning_at_seconds() -> i32 {
    emergency::WARNING_AT_SECONDS as i32
}

/// `t` is bound to the `emergency` namespace.
#[uniffi::export]
pub fn build_initial_steps(t: Arc<Translator>, emergency_number: String) -> Vec<ChecklistStep> {
    emergency::build_initial_steps(&t, &emergency_number)
}

#[uniffi::export]
pub fn seconds_remaining(started_at_ms: i64, now_ms: i64) -> i32 {
    emergency::seconds_remaining(started_at_ms, now_ms) as i32
}

#[uniffi::export]
pub fn countdown_alerts(prev: i32, next: i32) -> Vec<CountdownAlert> {
    emergency::countdown_alerts(prev.into(), next.into())
}

#[uniffi::export]
pub fn countdown_alert_key(alert: CountdownAlert) -> String {
    alert.key().to_string()
}

#[uniffi::export]
pub fn format_countdown(seconds: i32) -> String {
    emergency::format_countdown(seconds.into())
}

#[uniffi::export]
pub fn build_alert_sms(
    t: Arc<Translator>,
    name: Option<String>,
    started_time: String,
    wearing: String,
) -> String {
    emergency::build_alert_sms(&t, name.as_deref(), &started_time, &wearing)
}

#[uniffi::export]
pub fn direction_hint(t: Arc<Translator>, dominant_hand: Option<String>) -> Option<String> {
    emergency::direction_hint(&t, dominant_hand.as_deref())
}

/// The stored active_emergency value, parsed; None when nothing is active.
#[uniffi::export]
pub fn parse_active_emergency(raw: Option<String>) -> Option<ActiveEmergency> {
    emergency::parse_active_emergency(raw.as_deref())
}

#[uniffi::export]
pub fn serialize_active_emergency(emergency: ActiveEmergency) -> String {
    emergency::serialize_active_emergency(&emergency)
}

#[uniffi::export]
pub fn active_emergency_started_at_ms(emergency: ActiveEmergency) -> Option<i64> {
    emergency.started_at_ms()
}

#[uniffi::export]
pub fn alert_due_at_ms(alert: CountdownAlert, started_at_ms: i64) -> i64 {
    alerts::alert_due_at_ms(alert, started_at_ms)
}

#[uniffi::export]
pub fn alerts_to_schedule(started_at_ms: i64, now_ms: i64) -> Vec<ScheduledAlert> {
    alerts::alerts_to_schedule(started_at_ms, now_ms)
}

#[uniffi::export]
pub fn catch_up_alert(
    started_at_ms: i64,
    away_from_ms: i64,
    now_ms: i64,
) -> Option<CountdownAlert> {
    alerts::catch_up_alert(started_at_ms, away_from_ms, now_ms)
}

#[uniffi::export]
pub fn alert_delivery_key(delivery: AlertDelivery) -> String {
    delivery.key().to_string()
}

#[uniffi::export]
pub fn sms_result_key(result: SmsResult) -> String {
    result.key().to_string()
}

#[uniffi::export]
pub fn dial_target_key(target: DialTarget) -> String {
    target.key().to_string()
}

#[uniffi::export]
pub fn countdown_alert_properties(
    alert: CountdownAlert,
    delivery: AlertDelivery,
) -> HashMap<String, PropValue> {
    props_out(alerts::countdown_alert_properties(alert, delivery))
}

#[uniffi::export]
pub fn emergency_resumed_properties(
    away_from_ms: i64,
    now_ms: i64,
    caught_up: Option<CountdownAlert>,
) -> HashMap<String, PropValue> {
    props_out(alerts::emergency_resumed_properties(
        away_from_ms,
        now_ms,
        caught_up,
    ))
}

#[uniffi::export]
pub fn emergency_ended_properties(
    started_at_ms: i64,
    now_ms: i64,
    checked_count: i32,
) -> HashMap<String, PropValue> {
    props_out(alerts::emergency_ended_properties(
        started_at_ms,
        now_ms,
        checked_count.max(0) as usize,
    ))
}

#[uniffi::export]
pub fn readiness_properties(
    profile: Option<Profile>,
    contacts: i32,
    contacts_alerted: i32,
    places: i32,
) -> HashMap<String, PropValue> {
    props_out(alerts::readiness_properties(
        profile.as_ref(),
        contacts.max(0) as usize,
        contacts_alerted.max(0) as usize,
        places.max(0) as usize,
    ))
}

#[uniffi::export]
pub fn sms_result_properties(result: SmsResult, recipients: i32) -> HashMap<String, PropValue> {
    props_out(alerts::sms_result_properties(
        result,
        recipients.max(0) as usize,
    ))
}

#[uniffi::export]
pub fn dial_failed_properties(target: DialTarget, screen: String) -> HashMap<String, PropValue> {
    props_out(alerts::dial_failed_properties(target, &screen))
}

/// `error_type` is the error's type name (the `StoreFailure` variant name), never its message.
#[uniffi::export]
pub fn save_failed_properties(
    screen: String,
    action: String,
    error_type: Option<String>,
) -> HashMap<String, PropValue> {
    props_out(alerts::save_failed_properties(
        &screen,
        &action,
        error_type.as_deref(),
    ))
}

#[uniffi::export]
pub fn data_migration_failed_properties(
    from_version: i32,
    duration_ms: i64,
    error_type: Option<String>,
) -> HashMap<String, PropValue> {
    props_out(store::data_migration_failed_properties(
        from_version,
        duration_ms.max(0) as u64,
        error_type.as_deref(),
    ))
}

// ---------------------------------------------------------------------------------------------
// Store

/// The keys of the settings the apps read and write.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct SettingKeys {
    pub active_emergency: String,
    pub emergency_away_from: String,
    pub notifications_prompted: String,
    pub device_id: String,
    pub theme_preference: String,
    pub language_preference: String,
}

#[uniffi::export]
pub fn setting_keys() -> SettingKeys {
    SettingKeys {
        active_emergency: Store::ACTIVE_EMERGENCY.into(),
        emergency_away_from: Store::EMERGENCY_AWAY_FROM.into(),
        notifications_prompted: Store::NOTIFICATIONS_PROMPTED.into(),
        device_id: Store::DEVICE_ID.into(),
        theme_preference: Store::THEME_PREFERENCE.into(),
        language_preference: Store::LANGUAGE_PREFERENCE.into(),
    }
}

#[uniffi::export]
impl Store {
    /// Opens the database at `path` (creating it, and its folder, on a fresh install), first
    /// adopting a database the RN app left there and migrating older schemas to the current
    /// one. The apps open `<files>/SQLite/nijii.db`, where the KMP app kept it.
    #[uniffi::constructor(name = "open")]
    pub fn ffi_open(path: String) -> FfiResult<Arc<Store>> {
        Ok(Arc::new(Store::open(path)?))
    }

    /// A fresh, private database (tests and previews).
    #[uniffi::constructor(name = "open_in_memory")]
    pub fn ffi_open_in_memory() -> FfiResult<Arc<Store>> {
        Ok(Arc::new(Store::open_in_memory()?))
    }

    #[uniffi::method(name = "migrated_from")]
    pub fn ffi_migrated_from(&self) -> Option<i32> {
        self.migrated_from()
    }

    #[uniffi::method(name = "migration_duration_ms")]
    pub fn ffi_migration_duration_ms(&self) -> Option<i64> {
        self.migration_duration_ms().map(|ms| ms as i64)
    }

    #[uniffi::method(name = "seed")]
    pub fn ffi_seed(&self) -> FfiResult<()> {
        Ok(self.seed()?)
    }

    #[uniffi::method(name = "is_onboarded")]
    pub fn ffi_is_onboarded(&self) -> FfiResult<bool> {
        Ok(self.is_onboarded()?)
    }

    #[uniffi::method(name = "complete_step")]
    pub fn ffi_complete_step(&self, step: String) -> FfiResult<()> {
        Ok(self.complete_step(&step)?)
    }

    #[uniffi::method(name = "onboarding_steps")]
    pub fn ffi_onboarding_steps(&self) -> FfiResult<Vec<OnboardingStepEntity>> {
        Ok(self.onboarding_steps()?)
    }

    #[uniffi::method(name = "safety_checks")]
    pub fn ffi_safety_checks(&self) -> FfiResult<Vec<SafetyCheckEntity>> {
        Ok(self.safety_checks()?)
    }

    #[uniffi::method(name = "profile")]
    pub fn ffi_profile(&self) -> FfiResult<Option<Profile>> {
        Ok(self.profile()?)
    }

    /// The stored profile row itself (with its timestamps).
    #[uniffi::method(name = "profile_entity")]
    pub fn ffi_profile_entity(&self) -> FfiResult<Option<ProfileEntity>> {
        Ok(self.profile_entity()?)
    }

    /// Saves the whole profile row; the first save creates the single row, later ones update it.
    #[uniffi::method(name = "save_profile_entity")]
    pub fn ffi_save_profile_entity(&self, entity: ProfileEntity) -> FfiResult<()> {
        Ok(self.save_profile_entity(entity)?)
    }

    /// A partial profile save by field name (the Profile property names, camelCase): given
    /// fields are set (None clears), the rest kept.
    #[uniffi::method(name = "update_profile")]
    pub fn ffi_update_profile(&self, values: HashMap<String, Option<String>>) -> FfiResult<()> {
        let values: Vec<(&str, Option<&str>)> = values
            .iter()
            .map(|(k, v)| (k.as_str(), v.as_deref()))
            .collect();
        Ok(self.update_profile(&values)?)
    }

    #[uniffi::method(name = "add_contact")]
    pub fn ffi_add_contact(
        &self,
        contact: ContactEntity,
        sort_order: Option<i32>,
    ) -> FfiResult<i64> {
        Ok(self.add_contact(contact, sort_order)?)
    }

    #[uniffi::method(name = "update_contact")]
    pub fn ffi_update_contact(&self, contact: ContactEntity) -> FfiResult<()> {
        Ok(self.update_contact(&contact)?)
    }

    #[uniffi::method(name = "delete_contact")]
    pub fn ffi_delete_contact(&self, id: i64) -> FfiResult<()> {
        Ok(self.delete_contact(id)?)
    }

    #[uniffi::method(name = "reorder_contacts")]
    pub fn ffi_reorder_contacts(&self, ordered: Vec<ContactEntity>) -> FfiResult<()> {
        Ok(self.reorder_contacts(&ordered)?)
    }

    #[uniffi::method(name = "add_destination")]
    pub fn ffi_add_destination(
        &self,
        destination: DestinationEntity,
        sort_order: Option<i32>,
    ) -> FfiResult<i64> {
        Ok(self.add_destination(destination, sort_order)?)
    }

    #[uniffi::method(name = "update_destination")]
    pub fn ffi_update_destination(&self, destination: DestinationEntity) -> FfiResult<()> {
        Ok(self.update_destination(&destination)?)
    }

    #[uniffi::method(name = "delete_destination")]
    pub fn ffi_delete_destination(&self, id: i64) -> FfiResult<()> {
        Ok(self.delete_destination(id)?)
    }

    #[uniffi::method(name = "reorder_destinations")]
    pub fn ffi_reorder_destinations(&self, ordered: Vec<DestinationEntity>) -> FfiResult<()> {
        Ok(self.reorder_destinations(&ordered)?)
    }

    /// Every contact, in display order.
    #[uniffi::method(name = "contacts")]
    pub fn ffi_contacts(&self) -> FfiResult<Vec<ContactEntity>> {
        Ok(self.contacts()?)
    }

    /// Contacts the emergency alert texts.
    #[uniffi::method(name = "emergency_contacts")]
    pub fn ffi_emergency_contacts(&self) -> FfiResult<Vec<ContactEntity>> {
        Ok(self.emergency_contacts()?)
    }

    #[uniffi::method(name = "destinations")]
    pub fn ffi_destinations(&self) -> FfiResult<Vec<DestinationEntity>> {
        Ok(self.destinations()?)
    }

    #[uniffi::method(name = "active_emergency")]
    pub fn ffi_active_emergency(&self) -> FfiResult<Option<ActiveEmergency>> {
        Ok(self.active_emergency()?)
    }

    #[uniffi::method(name = "save_active_emergency")]
    pub fn ffi_save_active_emergency(&self, emergency: ActiveEmergency) -> FfiResult<()> {
        Ok(self.save_active_emergency(&emergency)?)
    }

    #[uniffi::method(name = "clear_active_emergency")]
    pub fn ffi_clear_active_emergency(&self) -> FfiResult<()> {
        Ok(self.clear_active_emergency()?)
    }

    /// Every incident, newest first.
    #[uniffi::method(name = "incidents")]
    pub fn ffi_incidents(&self) -> FfiResult<Vec<IncidentEntity>> {
        Ok(self.incidents()?)
    }

    #[uniffi::method(name = "create_incident")]
    pub fn ffi_create_incident(&self, started_at: String) -> FfiResult<i64> {
        Ok(self.create_incident(&started_at)?)
    }

    /// Records what happened: `outcome` and `ended_at` when given, and always the checked
    /// steps and clothing. A missing row is created first.
    #[uniffi::method(name = "record_incident")]
    pub fn ffi_record_incident(
        &self,
        incident_id: Option<i64>,
        started_at: String,
        checked_steps: Vec<String>,
        wearing: String,
        outcome: Option<String>,
        ended_at: Option<String>,
    ) -> FfiResult<i64> {
        Ok(self.record_incident(
            incident_id,
            &started_at,
            &checked_steps,
            &wearing,
            outcome.as_deref(),
            ended_at.as_deref(),
        )?)
    }

    /// Settings > Delete Account: empties every table, then re-creates the onboarding steps.
    #[uniffi::method(name = "clear_all_data")]
    pub fn ffi_clear_all_data(&self) -> FfiResult<()> {
        Ok(self.clear_all_data()?)
    }

    /// data_migrated's properties after migrating from `from_version` in `duration_ms`.
    #[uniffi::method(name = "data_migrated_properties")]
    pub fn ffi_data_migrated_properties(
        &self,
        from_version: i32,
        duration_ms: i64,
    ) -> FfiResult<HashMap<String, PropValue>> {
        Ok(props_out(self.data_migrated_properties(
            from_version,
            duration_ms.max(0) as u64,
        )?))
    }

    /// app_ready's readiness properties from what's stored.
    #[uniffi::method(name = "readiness_properties")]
    pub fn ffi_readiness_properties(&self) -> FfiResult<HashMap<String, PropValue>> {
        Ok(props_out(self.readiness_properties()?))
    }

    /// The anonymous per-install ID.
    #[uniffi::method(name = "device_id")]
    pub fn ffi_device_id(&self) -> FfiResult<String> {
        Ok(self.device_id()?)
    }

    #[uniffi::method(name = "setting")]
    pub fn ffi_setting(&self, key: String) -> FfiResult<Option<String>> {
        Ok(self.setting(&key)?)
    }

    #[uniffi::method(name = "put_setting")]
    pub fn ffi_put_setting(&self, key: String, value: String) -> FfiResult<()> {
        Ok(self.put_setting(&key, &value)?)
    }
}

// ---------------------------------------------------------------------------------------------
// Emergency away

#[uniffi::export]
impl EmergencyAway {
    #[uniffi::constructor(name = "new")]
    pub fn ffi_new(store: Arc<Store>, scheduler: Arc<dyn AlertScheduler>) -> Arc<Self> {
        Arc::new(Self::new(store, scheduler))
    }

    /// The screen's countdown moved from `prev_seconds_left` to `next_seconds_left` at
    /// `now_ms`: the alerts to give now (vibration), reported as countdown_alert.
    #[uniffi::method(name = "in_app_alerts")]
    pub fn ffi_in_app_alerts(
        &self,
        prev_seconds_left: i32,
        next_seconds_left: i32,
        now_ms: i64,
    ) -> Vec<CountdownAlert> {
        self.in_app_alerts(prev_seconds_left.into(), next_seconds_left.into(), now_ms)
    }

    /// The emergency screen stopped showing: schedules the alerts still to come.
    #[uniffi::method(name = "left")]
    pub fn ffi_left(&self, now_ms: i64) -> FfiResult<()> {
        Ok(self.left(now_ms)?)
    }

    /// The emergency screen is showing: cancels the scheduled alerts, reports the time away and
    /// returns the alert to catch up on, if one came due.
    #[uniffi::method(name = "shown")]
    pub fn ffi_shown(&self, now_ms: i64) -> FfiResult<Option<CountdownAlert>> {
        Ok(self.shown(now_ms)?)
    }

    /// The emergency ended: nothing more to deliver.
    #[uniffi::method(name = "ended")]
    pub fn ffi_ended(&self) -> FfiResult<()> {
        Ok(self.ended()?)
    }

    #[uniffi::method(name = "should_ask_for_notifications")]
    pub fn ffi_should_ask_for_notifications(&self) -> FfiResult<bool> {
        Ok(self.should_ask_for_notifications()?)
    }

    #[uniffi::method(name = "notifications_answered")]
    pub fn ffi_notifications_answered(&self, granted: bool) -> FfiResult<()> {
        Ok(self.notifications_answered(granted)?)
    }
}

// ---------------------------------------------------------------------------------------------
// Startup

/// Seeds the store, and reports data_migrated if opening migrated an older database. Call once
/// at launch, before the first screen reads.
#[uniffi::export]
pub fn seed_and_report_migration(store: Arc<Store>) -> FfiResult<()> {
    Ok(startup::seed_and_report_migration(&store)?)
}

/// app_ready, once per process.
#[uniffi::export]
impl AppReady {
    #[uniffi::constructor(name = "new")]
    pub fn ffi_new() -> Arc<Self> {
        Arc::new(AppReady::new())
    }

    /// Reports app_ready unless it already was: `startup_ms` is the cold start time, `device`
    /// what only the device knows (notifications_enabled, exact_alarms). True if it reported.
    #[uniffi::method(name = "report")]
    pub fn ffi_report(
        &self,
        startup_ms: i64,
        store: Arc<Store>,
        device: HashMap<String, PropValue>,
    ) -> bool {
        self.report(startup_ms.max(0) as u64, &store, props_in(device))
    }
}
