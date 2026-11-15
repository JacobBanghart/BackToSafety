//! What home's "Ready for an emergency" card lists: the things that make the 911 script and the
//! search stronger, each filled in or not.

use crate::profile::{Profile, set};

/// A line of the readiness card. Its key is the i18n key under home `readiness.items`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum ReadinessItem {
    /// A photo for the dispatcher and the people searching.
    Photo,
    /// Something a stranger could recognise them by (height, weight, hair, eyes, marks).
    Description,
    /// At least one contact in the Alert Circle (texted when Alert Circle is tapped in a search).
    Contacts,
    /// At least one place they might go.
    Places,
}

impl ReadinessItem {
    pub const ALL: [ReadinessItem; 4] =
        [Self::Photo, Self::Description, Self::Contacts, Self::Places];

    pub fn as_key(self) -> &'static str {
        match self {
            Self::Photo => "photo",
            Self::Description => "description",
            Self::Contacts => "contacts",
            Self::Places => "places",
        }
    }
}

/// The item's i18n key, through UniFFI (`item.key()` for the Kotlin core's `item.key`).
#[uniffi::export]
impl ReadinessItem {
    pub fn key(&self) -> String {
        self.as_key().to_string()
    }
}

/// One line of the readiness card. `needs_alert_circle`: contacts are saved but none is in the
/// Alert Circle, so a search's Alert Circle would text no one; the line says so rather than
/// "Add". `label_key` is the line's label (home `readiness.items.<label_key>`) and `action_key`
/// what its action says (home `readiness.<action_key>`), both from the rest.
#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct ReadinessCheck {
    pub item: ReadinessItem,
    pub done: bool,
    pub needs_alert_circle: bool,
    pub label_key: String,
    pub action_key: String,
}

impl ReadinessCheck {
    pub fn new(item: ReadinessItem, done: bool, needs_alert_circle: bool) -> Self {
        let label_key = if needs_alert_circle {
            "contactsNoAlertCircle"
        } else {
            item.as_key()
        };
        let action_key = if done {
            "done"
        } else if needs_alert_circle {
            "turnOn"
        } else {
            "add"
        };
        ReadinessCheck {
            item,
            done,
            needs_alert_circle,
            label_key: label_key.to_string(),
            action_key: action_key.to_string(),
        }
    }
}

/// The readiness card's lines, in order, from what's stored. A missing profile counts as nothing
/// filled in; the description uses the same fields as the script's appearance line. Contacts are
/// ready only when at least one of them (`in_alert_circle`) gets the emergency texts.
pub fn readiness_checklist(
    profile: Option<&Profile>,
    contacts: usize,
    in_alert_circle: usize,
    places: usize,
) -> Vec<ReadinessCheck> {
    let described = profile.is_some_and(|p| {
        [
            &p.height,
            &p.weight,
            &p.hair_color,
            &p.eye_color,
            &p.identifying_marks,
        ]
        .into_iter()
        .any(|v| set(v).is_some())
    });
    vec![
        ReadinessCheck::new(
            ReadinessItem::Photo,
            profile.is_some_and(|p| set(&p.photo_uri).is_some()),
            false,
        ),
        ReadinessCheck::new(ReadinessItem::Description, described, false),
        ReadinessCheck::new(
            ReadinessItem::Contacts,
            in_alert_circle > 0,
            contacts > 0 && in_alert_circle == 0,
        ),
        ReadinessCheck::new(ReadinessItem::Places, places > 0, false),
    ]
}
