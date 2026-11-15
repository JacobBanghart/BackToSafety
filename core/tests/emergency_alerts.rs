//! The twin of kmp/shared's EmergencyAlertsTest, and Analytics' flush and reset: the events
//! around alerting the circle mean the same on both apps because both report through
//! track_sms_result.

use nijii_core::alerts::{Properties, SmsResult, track_sms_result};
use nijii_core::analytics::Analytics;
use serde_json::{Value, json};
use std::sync::{Arc, Mutex, MutexGuard};

/// The analytics sink is process-wide, so tests that read it run one at a time.
static SINK_LOCK: Mutex<()> = Mutex::new(());

struct Sink {
    _serial: MutexGuard<'static, ()>,
    tracked: Arc<Mutex<Vec<(String, Properties)>>>,
}

impl Sink {
    /// Records events, and the flusher's and resetter's calls as "<flush>" and "<reset>".
    fn new() -> Sink {
        let serial = SINK_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        let tracked = Arc::new(Mutex::new(Vec::new()));
        let (t, f, r) = (tracked.clone(), tracked.clone(), tracked.clone());
        Analytics::set_sink(move |name, props| {
            t.lock().unwrap().push((name.to_string(), props.clone()))
        });
        Analytics::set_flusher(move || {
            f.lock()
                .unwrap()
                .push(("<flush>".into(), Properties::new()))
        });
        Analytics::set_resetter(move || {
            r.lock()
                .unwrap()
                .push(("<reset>".into(), Properties::new()))
        });
        Sink {
            _serial: serial,
            tracked,
        }
    }

    fn take(&self) -> Vec<(String, Properties)> {
        std::mem::take(&mut *self.tracked.lock().unwrap())
    }
}

impl Drop for Sink {
    fn drop(&mut self) {
        Analytics::clear_sink();
    }
}

fn props(v: Value) -> Properties {
    v.as_object().unwrap().clone()
}

#[test]
fn a_sent_or_handed_off_text_counts_as_alerting_the_contacts() {
    let sink = Sink::new();
    for result in [SmsResult::Sent, SmsResult::HandedOff] {
        track_sms_result(result, 2);
        assert_eq!(
            sink.take(),
            vec![
                (
                    "emergency_contacts_alerted".to_string(),
                    props(json!({"recipient_count": 2}))
                ),
                (
                    "emergency_sms_result".to_string(),
                    props(json!({"result": result.key(), "recipient_count": 2}))
                ),
            ]
        );
    }
}

#[test]
fn a_cancelled_failed_or_unavailable_text_does_not() {
    let sink = Sink::new();
    for result in [
        SmsResult::Cancelled,
        SmsResult::Failed,
        SmsResult::Unavailable,
    ] {
        track_sms_result(result, 3);
        assert_eq!(
            sink.take(),
            vec![(
                "emergency_sms_result".to_string(),
                props(json!({"result": result.key(), "recipient_count": 3}))
            )]
        );
    }
}

#[test]
fn reset_flushes_first_then_forgets_the_identity() {
    let sink = Sink::new();
    Analytics::flush();
    assert_eq!(
        sink.take().iter().map(|e| e.0.as_str()).collect::<Vec<_>>(),
        ["<flush>"]
    );
    Analytics::reset();
    assert_eq!(
        sink.take().iter().map(|e| e.0.as_str()).collect::<Vec<_>>(),
        ["<flush>", "<reset>"]
    );
    Analytics::clear_sink();
    Analytics::reset(); // without a platform SDK, nothing happens
    assert!(sink.take().is_empty());
}
