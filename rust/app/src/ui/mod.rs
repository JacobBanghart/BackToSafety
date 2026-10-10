//! Shared components (the twin of androidApp ui/ and iosApp UI/). Owned by the shell lane.

/// A screen not ported yet: shows its source so a capture makes the gap obvious.
#[macro_export]
macro_rules! stub {
    ($name:ident, $id:literal, $src:literal) => {
        #[component]
        pub fn $name() -> Element {
            rsx! {
                div { class: "screen", id: concat!($id, "-screen"), style: "padding: 48px 24px",
                    p { class: "type-headline", {stringify!($name)} }
                    p { class: "type-body", style: "color: var(--text-secondary)", {concat!("Not ported yet: ", $src)} }
                }
            }
        }
    };
}
