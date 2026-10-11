//! spec/analytics-events.json -> AnalyticsEvent, as kmp/buildSrc's GenerateAnalyticsEvents
//! does for the Kotlin apps. The app can only send an event the spec lists, under exactly its
//! name: dashboards key on these.

use std::{env, fs, path::Path};

/// The variant name for a wire name: `app_ready` -> `AppReady`. A number keeps its underscores
/// (`emergency_911_called` -> `Emergency_911_Called`), so UniFFI's SCREAMING_SNAKE_CASE for Kotlin
/// reads EMERGENCY_911_CALLED, as the Kotlin core's generated enum did.
fn camel(wire: &str) -> String {
    let parts: Vec<String> = wire
        .split('_')
        .map(|part| {
            let mut chars = part.chars();
            chars
                .next()
                .map(|c| c.to_ascii_uppercase().to_string() + chars.as_str())
                .unwrap_or_default()
        })
        .collect();
    let numeric = |p: &str| p.starts_with(|c: char| c.is_ascii_digit());
    let mut out = String::new();
    for (i, part) in parts.iter().enumerate() {
        if i > 0 && (numeric(part) || numeric(&parts[i - 1])) {
            out.push('_');
        }
        out += part;
    }
    out
}

fn main() {
    let spec = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../spec/analytics-events.json");
    println!("cargo:rerun-if-changed={}", spec.display());
    println!("cargo:rerun-if-changed=build.rs");
    let json: serde_json::Value =
        serde_json::from_str(&fs::read_to_string(&spec).unwrap()).unwrap();
    let events: Vec<&str> = json["events"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e.as_str().unwrap())
        .collect();

    let mut out = String::from(
        "// Generated from spec/analytics-events.json by core/build.rs. Do not edit.\n\n\
         /// The PostHog events both apps send; [`AnalyticsEvent::wire_name`] is what dashboards see.\n\
         #[allow(non_camel_case_types)]\n\
         #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, uniffi::Enum)]\n\
         pub enum AnalyticsEvent {\n",
    );
    for e in &events {
        out += &format!("    {},\n", camel(e));
    }
    out += "}\n\nimpl AnalyticsEvent {\n    pub const ALL: [AnalyticsEvent; ";
    out += &format!("{}] = [\n", events.len());
    for e in &events {
        out += &format!("        AnalyticsEvent::{},\n", camel(e));
    }
    out += "    ];\n\n    pub fn wire_name(self) -> &'static str {\n        match self {\n";
    for e in &events {
        out += &format!("            AnalyticsEvent::{} => \"{e}\",\n", camel(e));
    }
    out += "        }\n    }\n}\n";

    fs::write(
        Path::new(&env::var("OUT_DIR").unwrap()).join("analytics_event.rs"),
        out,
    )
    .unwrap();
}
