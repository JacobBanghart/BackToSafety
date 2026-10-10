//! Generates what the app reads from the repo's shared spec: CSS variables from
//! spec/design-tokens.json (as DesignTokens is for the Kotlin apps), the i18n/locales JSON,
//! and the logo as a data URI.

use base64::Engine;
use serde_json::Value;
use std::{env, fs, path::Path};

fn kebab(s: &str) -> String {
    s.chars()
        .flat_map(|c| {
            if c.is_uppercase() {
                vec!['-', c.to_ascii_lowercase()]
            } else {
                vec![c]
            }
        })
        .collect()
}

fn main() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let out = Path::new(&env::var("OUT_DIR").unwrap()).to_path_buf();

    let tokens_path = repo.join("spec/design-tokens.json");
    println!("cargo:rerun-if-changed={}", tokens_path.display());
    let tokens: Value = serde_json::from_str(&fs::read_to_string(&tokens_path).unwrap()).unwrap();
    let mut css = String::from(":root {\n");
    for (k, v) in tokens["spacing"].as_object().unwrap() {
        css += &format!("  --space-{k}: {v}px;\n");
    }
    for (k, v) in tokens["radius"].as_object().unwrap() {
        css += &format!("  --radius-{k}: {v}px;\n");
    }
    css += "}\n";
    for (scheme, selector) in [
        ("light", ":root, [data-theme=light]"),
        ("dark", "[data-theme=dark]"),
    ] {
        css += &format!("{selector} {{\n  color-scheme: {scheme};\n");
        for (k, v) in tokens["colors"][scheme].as_object().unwrap() {
            css += &format!("  --{}: {};\n", kebab(k), v.as_str().unwrap());
        }
        css += "}\n";
    }
    for (k, v) in tokens["typography"].as_object().unwrap() {
        css += &format!(
            ".type-{} {{ font-size: {}px; font-weight: {}; line-height: {}px; letter-spacing: {}px; }}\n",
            kebab(k),
            v["fontSize"],
            v["fontWeight"].as_str().unwrap(),
            v["lineHeight"],
            v["letterSpacing"]
        );
    }
    fs::write(out.join("tokens.css"), css).unwrap();

    let locales = repo.join("i18n/locales");
    println!("cargo:rerun-if-changed={}", locales.display());
    let mut rs = String::from("pub const LOCALES: &[(&str, &str, &str)] = &[\n");
    let mut entries: Vec<_> = fs::read_dir(&locales)
        .unwrap()
        .flatten()
        .filter(|e| e.path().is_dir())
        .collect();
    entries.sort_by_key(|e| e.file_name());
    for locale in entries {
        let mut files: Vec<_> = fs::read_dir(locale.path())
            .unwrap()
            .flatten()
            .map(|e| e.path())
            .collect();
        files.sort();
        for f in files
            .iter()
            .filter(|p| p.extension().is_some_and(|e| e == "json"))
        {
            rs += &format!(
                "    ({:?}, {:?}, include_str!({:?})),\n",
                locale.file_name().to_string_lossy(),
                f.file_stem().unwrap().to_string_lossy(),
                f.canonicalize().unwrap()
            );
        }
    }
    rs += "];\n";
    fs::write(out.join("locales.rs"), rs).unwrap();

    let logo = repo.join("assets/images/logo-full.png");
    println!("cargo:rerun-if-changed={}", logo.display());
    let b64 = base64::engine::general_purpose::STANDARD.encode(fs::read(&logo).unwrap());
    fs::write(out.join("logo.uri"), format!("data:image/png;base64,{b64}")).unwrap();

    // PostHog and the version, at build time as in androidApp/build.gradle.kts: POSTHOG_KEY and
    // POSTHOG_HOST come from the environment (no key: nothing is sent); the version and build
    // number from version.json.
    for var in ["POSTHOG_KEY", "POSTHOG_HOST"] {
        println!("cargo:rerun-if-env-changed={var}");
    }
    println!(
        "cargo:rustc-env=NIJII_POSTHOG_KEY={}",
        env::var("POSTHOG_KEY").unwrap_or_default()
    );
    println!(
        "cargo:rustc-env=NIJII_POSTHOG_HOST={}",
        env::var("POSTHOG_HOST").unwrap_or_else(|_| "https://us.i.posthog.com".into())
    );
    let release_path = repo.join("version.json");
    println!("cargo:rerun-if-changed={}", release_path.display());
    let release: Value = serde_json::from_str(&fs::read_to_string(&release_path).unwrap()).unwrap();
    println!(
        "cargo:rustc-env=NIJII_VERSION={}",
        release["version"].as_str().unwrap()
    );
    println!(
        "cargo:rustc-env=NIJII_BUILD={}",
        release["android"]["versionCode"]
    );
}
