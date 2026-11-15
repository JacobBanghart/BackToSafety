//! utils/analytics.ts: the app reports [`AnalyticsEvent`]s and screen views through this.
//! The platform supplies the sink (PostHog on Android); without one nothing is sent.

use crate::alerts::Properties;
use serde_json::json;
use std::sync::{LazyLock, RwLock};

include!(concat!(env!("OUT_DIR"), "/analytics_event.rs"));

/// Receives (event wire name, properties); screens arrive as "$screen" with the RN route path.
pub type Sink = Box<dyn Fn(&str, &Properties) + Send + Sync>;

static SINK: LazyLock<RwLock<Sink>> = LazyLock::new(|| RwLock::new(Box::new(|_, _| {})));

/// A call into the platform's analytics SDK with no arguments (its flush or its reset).
pub type Hook = Box<dyn Fn() + Send + Sync>;

static FLUSHER: LazyLock<RwLock<Hook>> = LazyLock::new(|| RwLock::new(Box::new(|| {})));
static RESETTER: LazyLock<RwLock<Hook>> = LazyLock::new(|| RwLock::new(Box::new(|| {})));

/// The key of a screen view.
pub const SCREEN: &str = "$screen";

pub struct Analytics;

impl Analytics {
    /// Replaces the sink; the default drops everything.
    pub fn set_sink(sink: impl Fn(&str, &Properties) + Send + Sync + 'static) {
        *SINK.write().unwrap_or_else(|e| e.into_inner()) = Box::new(sink);
    }

    /// Back to the default sink, which drops everything, and no flusher or resetter.
    pub fn clear_sink() {
        Self::set_sink(|_, _| {});
        Self::set_flusher(|| {});
        Self::set_resetter(|| {});
    }

    /// What [`Analytics::flush`] calls: sends what the sink has queued now, not at its next
    /// batch. The platform supplies it with the sink.
    pub fn set_flusher(flusher: impl Fn() + Send + Sync + 'static) {
        *FLUSHER.write().unwrap_or_else(|e| e.into_inner()) = Box::new(flusher);
    }

    /// What [`Analytics::reset`] calls: forgets who the sink has been reporting as (PostHog's
    /// reset). The platform supplies it with the sink.
    pub fn set_resetter(resetter: impl Fn() + Send + Sync + 'static) {
        *RESETTER.write().unwrap_or_else(|e| e.into_inner()) = Box::new(resetter);
    }

    /// Sends what's queued now (before a crash could lose it).
    pub fn flush() {
        (FLUSHER.read().unwrap_or_else(|e| e.into_inner()))();
    }

    /// Delete Account: later events start from a fresh anonymous identity, not the deleted
    /// device ID. What's queued is sent first; those events already carry the old identity.
    pub fn reset() {
        Self::flush();
        (RESETTER.read().unwrap_or_else(|e| e.into_inner()))();
    }

    pub fn track(event: AnalyticsEvent, properties: Properties) {
        Self::send(event.wire_name(), &properties);
    }

    /// An event with no properties.
    pub fn track_bare(event: AnalyticsEvent) {
        Self::track(event, Properties::new());
    }

    /// A screen view: by the RN app's route path (e.g. "/emergency") on every navigation, plus
    /// the named ones some RN screens report themselves ("home", "settings").
    pub fn screen(path: &str, mut properties: Properties) {
        properties.insert("$screen_name".into(), json!(path));
        Self::send(SCREEN, &properties);
    }

    fn send(name: &str, properties: &Properties) {
        (SINK.read().unwrap_or_else(|e| e.into_inner()))(name, properties);
    }
}
