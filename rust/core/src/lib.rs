//! The app's rules, without UI or storage: a port of kmp/shared's commonMain, held to the
//! same spec/vectors (tests/vectors.rs).

pub mod age;
pub mod alerts;
pub mod analytics;
pub mod emergency;
pub mod emergency_away;
pub mod formatters;
pub mod i18n;
pub mod mobility;
pub mod phone;
pub mod photo;
pub mod posthog;
pub mod profile;
pub mod readout;
pub mod startup;
pub mod store;

pub use i18n::{Translations, Translator};
pub use profile::Profile;
pub use store::Store;

uniffi::setup_scaffolding!();

/// FFI smoke test: the apps call this to prove the native library loads and bindings work.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
