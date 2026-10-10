//! Port of app/contacts.tsx (oracle: kmp/androidApp/src/main/kotlin/com/backtosafety/app/contacts/).

use crate::stub;
use dioxus::prelude::*;

pub const CSS: &str = include_str!("contacts.css");

stub!(Contacts, "contacts", "app/contacts.tsx");
