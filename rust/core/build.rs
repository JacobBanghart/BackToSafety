//! spec/analytics-events.json -> AnalyticsEvent, as kmp/buildSrc's GenerateAnalyticsEvents
//! does for the Kotlin apps. The app can only send an event the spec lists, under exactly its
//! name: dashboards key on these.

use std::{env, fs, path::Path};

fn camel(wire: &str) -> String {
    wire.split('_')
        .map(|part| {
            let mut chars = part.chars();
            chars
                .next()
                .map(|c| c.to_ascii_uppercase().to_string() + chars.as_str())
                .unwrap_or_default()
        })
        .collect()
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
         #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]\n\
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
