//! Onboarding: port of app/onboarding/*.tsx (oracle: kmp/androidApp/src/main/kotlin/com/backtosafety/app/onboarding/).

use crate::{Route, Settings, ThemePreference, stub, t};
use dioxus::prelude::*;

pub const CSS: &str = include_str!("onboarding.css");

const LOGO: &str = include_str!(concat!(env!("OUT_DIR"), "/logo.uri"));

#[component]
pub fn Welcome() -> Element {
    let tr = t("onboarding");
    let mut settings = use_context::<Signal<Settings>>();
    let mut has_scrolled = use_signal(|| false);
    let nav = use_navigator();

    let features = [("⏱️", "timer"), ("📋", "readout"), ("🔒", "privacy")];
    let themes = [
        (ThemePreference::Light, "light", "light", "☀️"),
        (ThemePreference::Dark, "dark", "dark", "🌙"),
        (ThemePreference::System, "system", "auto", "📱"),
    ];
    let current = settings.read().theme;
    let language = settings.read().language.clone();

    rsx! {
        div { class: "screen welcome",
            div {
                class: "scroll",
                onscroll: move |e| {
                    if !has_scrolled() && e.data().scroll_top() > 10.0 {
                        has_scrolled.set(true);
                    }
                },
                div { class: "content",
                    div { class: "hero",
                        img { class: "logo", src: LOGO }
                        p { class: "title", {tr.t("welcome.title")} }
                        p { class: "description type-body-large", {tr.t("welcome.description")} }
                    }
                    div { class: "features",
                        for (icon, key) in features {
                            div { class: "feature",
                                div { class: "feature-icon", "{icon}" }
                                div { class: "feature-content",
                                    p { class: "type-body-bold", {tr.t(&format!("welcome.features.{key}.title"))} }
                                    p { class: "feature-text type-body", {tr.t(&format!("welcome.features.{key}.description"))} }
                                }
                            }
                        }
                    }
                    div { class: "section",
                        p { class: "label type-caption", {tr.t("welcome.themeLabel")} }
                        div { class: "options",
                            for (value, id, label, icon) in themes {
                                button {
                                    id: "onboarding-welcome-theme-{id}",
                                    class: if current == value { "option selected" } else { "option" },
                                    onclick: move |_| settings.write().theme = value,
                                    p { class: "icon", "{icon}" }
                                    p { class: "option-text", {tr.t(&format!("welcome.themeOptions.{label}"))} }
                                }
                            }
                        }
                    }
                    if cfg!(debug_assertions) {
                        div { class: "section",
                            p { class: "label type-caption", {tr.t("welcome.languageLabel")} }
                            div { class: "options",
                                for (lang, label) in [("en", "English"), ("es", "Español")] {
                                    button {
                                        id: "onboarding-welcome-language-{lang}",
                                        class: if language == lang { "option selected" } else { "option" },
                                        onclick: move |_| settings.write().language = lang.into(),
                                        p { class: "option-text", "{label}" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            div { class: "footer",
                if !has_scrolled() {
                    p { class: "scroll-hint type-caption", {tr.t("welcome.scrollHint")} }
                }
                button {
                    id: "onboarding-get-started",
                    class: "button",
                    onclick: move |_| { nav.push(Route::Name {}); },
                    p { {tr.t("welcome.getStarted")} }
                }
                p { class: "privacy type-caption", {tr.t("welcome.privacy")} }
            }
        }
    }
}

stub!(Name, "onboarding-name", "app/onboarding/name.tsx");
stub!(Photo, "onboarding-photo", "app/onboarding/photo.tsx");
stub!(
    Appearance,
    "onboarding-appearance",
    "app/onboarding/appearance.tsx"
);
stub!(Contact, "onboarding-contact", "app/onboarding/contact.tsx");
stub!(
    Complete,
    "onboarding-complete",
    "app/onboarding/complete.tsx"
);
