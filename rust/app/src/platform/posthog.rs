//! The analytics sink: PostHog over HTTP, set up as androidApp's MainActivity.setUpAnalytics
//! does for the SDK. The PostHog key and host are fixed at build time (POSTHOG_KEY and
//! POSTHOG_HOST in the environment); without a key nothing is set up and every event goes
//! nowhere. The client itself is `nijii_core::posthog`.

use nijii_core::analytics::Analytics;
use nijii_core::posthog::{Client, Config, without_nulls};
use nijii_core::startup::{AppVersion, lifecycle_events};
use serde_json::json;
use std::path::Path;
use std::sync::{Arc, OnceLock};
use std::time::Duration;

const KEY: &str = env!("NIJII_POSTHOG_KEY");
const HOST: &str = env!("NIJII_POSTHOG_HOST");

/// version.json's version and Android build number.
pub fn app_version() -> AppVersion {
    AppVersion {
        version: env!("NIJII_VERSION").into(),
        build: env!("NIJII_BUILD").into(),
    }
}

static CLIENT: OnceLock<Arc<Client>> = OnceLock::new();

/// Whether this build has a PostHog key.
pub fn enabled() -> bool {
    !KEY.is_empty()
}

/// Makes the PostHog client the analytics sink and sends the app lifecycle events the SDK sends
/// itself (installed, updated, opened). `state_dir` keeps the last version seen. Does nothing
/// without a key.
pub fn install(state_dir: &Path) {
    if !enabled() {
        return;
    }
    let version = app_version();
    let mut config = Config::new(KEY, HOST);
    config
        .base_properties
        .insert("$app_version".into(), json!(version.version));
    config
        .base_properties
        .insert("$app_build".into(), json!(version.build));
    config.base_properties.insert(
        "$os".into(),
        json!(if cfg!(target_os = "android") {
            "Android"
        } else {
            std::env::consts::OS
        }),
    );
    let client = Client::start(config);
    let sink = client.clone();
    Analytics::set_sink(move |name, properties| sink.capture(name, &without_nulls(properties)));

    let state = state_dir.join("posthog_app_version");
    let previous = std::fs::read_to_string(&state)
        .ok()
        .and_then(|s| AppVersion::parse(&s));
    for (name, properties) in lifecycle_events(previous.as_ref(), &version) {
        client.capture(name, &properties);
    }
    let _ = std::fs::write(&state, version.serialize());
    let _ = CLIENT.set(client);
}

/// PostHog.identify: the device ID is the analytics identity (as on iOS).
pub fn identify(distinct_id: &str) {
    if let Some(client) = CLIENT.get() {
        client.identify(distinct_id);
    }
}

/// Sends what's queued, waiting a moment for it; before the app stops on purpose.
pub fn flush() {
    if let Some(client) = CLIENT.get() {
        client.flush(Duration::from_secs(3));
    }
}
