mod platform;
mod screens;
mod store;
mod ui;

use dioxus::prelude::*;
use nijii_core::{Translations, Translator};
use screens::{
    contacts::Contacts,
    destinations::Destinations,
    emergency::Emergency,
    home::Home,
    onboarding::{Appearance, Complete, Contact, Name, Photo, Welcome},
    profile::Profile,
    readout::Readout,
    settings::Settings as SettingsScreen,
};
use std::sync::{Arc, LazyLock};

include!(concat!(env!("OUT_DIR"), "/locales.rs"));
const TOKENS_CSS: &str = include_str!(concat!(env!("OUT_DIR"), "/tokens.css"));
const APP_CSS: &str = include_str!("app.css");

static TRANSLATIONS: LazyLock<Arc<Translations>> =
    LazyLock::new(|| Arc::new(Translations::from_json(LOCALES.iter().copied())));

/// `t` for a namespace in the current language.
pub fn t(namespace: &str) -> Translator {
    TRANSLATIONS.translator(
        &use_context::<Signal<Settings>>().read().language,
        namespace,
    )
}

#[derive(Clone, Copy, PartialEq)]
pub enum ThemePreference {
    Light,
    Dark,
    System,
}

#[derive(Clone)]
pub struct Settings {
    pub theme: ThemePreference,
    pub language: String,
}

/// The KMP app's routes (App.kt), at the RN app's paths (which analytics reports).
#[derive(Routable, Clone, PartialEq)]
#[rustfmt::skip]
pub enum Route {
    #[route("/onboarding")] Welcome {},
    #[route("/onboarding/name")] Name {},
    #[route("/onboarding/photo")] Photo {},
    #[route("/onboarding/appearance")] Appearance {},
    #[route("/onboarding/contact")] Contact {},
    #[route("/onboarding/complete")] Complete {},
    #[route("/")] Home {},
    #[route("/emergency")] Emergency {},
    #[route("/settings")] SettingsScreen {},
    #[route("/contacts")] Contacts {},
    #[route("/destinations")] Destinations {},
    #[route("/readout")] Readout {},
    #[route("/profile")] Profile {},
}

fn main() {
    dioxus::launch(App);
}

#[component]
fn App() -> Element {
    store::provide();
    let settings = use_context_provider(|| {
        Signal::new(Settings {
            theme: ThemePreference::System,
            language: "en".into(),
        })
    });
    let theme = match settings.read().theme {
        ThemePreference::Light => "light",
        ThemePreference::Dark => "dark",
        ThemePreference::System => "system",
    };
    rsx! {
        document::Style { {TOKENS_CSS} }
        document::Style { {APP_CSS} }
        document::Style { {screens::CSS.concat()} }
        div { class: "app", "data-theme": theme,
            // Startup routing (onboarded → home, else welcome) is the shell lane's: app/index.tsx.
            Router::<Route> {}
        }
    }
}
