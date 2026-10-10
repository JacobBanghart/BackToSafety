//! A small PostHog client: events go to the HTTP batch API from a background thread, so the
//! UI never waits on the network. It mirrors how the KMP Android app configures the PostHog
//! SDK (androidApp MainActivity.setUpAnalytics): flush every 20 events or 10 seconds, at most
//! 100 events per request, at most 1000 queued, and people are profiled only once identified
//! (`PersonProfiles.IDENTIFIED_ONLY`). Without an API key nothing is created, so nothing is sent.

use crate::alerts::Properties;
use serde_json::{Value, json};
use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

#[derive(Clone, Debug)]
pub struct Config {
    pub api_key: String,
    /// The PostHog host, e.g. "https://us.i.posthog.com".
    pub host: String,
    pub flush_at: usize,
    pub flush_interval: Duration,
    pub max_batch_size: usize,
    pub max_queue_size: usize,
    /// Added to every event: `$app_version`, `$app_build`, `$os` and so on.
    pub base_properties: Properties,
}

impl Config {
    pub fn new(api_key: impl Into<String>, host: impl Into<String>) -> Config {
        Config {
            api_key: api_key.into(),
            host: host.into(),
            flush_at: 20,
            flush_interval: Duration::from_secs(10),
            max_batch_size: 100,
            max_queue_size: 1000,
            base_properties: Properties::new(),
        }
    }
}

enum Msg {
    Event(Value),
    /// Send everything queued now, then acknowledge.
    Flush(Sender<()>),
}

struct Identity {
    anonymous_id: String,
    distinct_id: String,
    identified: bool,
}

pub struct Client {
    tx: Sender<Msg>,
    identity: Mutex<Identity>,
    base_properties: Properties,
}

/// Events as the `/batch/` endpoint takes them.
pub fn batch_body(api_key: &str, events: &[Value]) -> Value {
    json!({ "api_key": api_key, "batch": events })
}

/// `properties` without the null values (the KMP sink drops them before PostHog sees them).
pub fn without_nulls(properties: &Properties) -> Properties {
    properties
        .iter()
        .filter(|(_, v)| !v.is_null())
        .map(|(k, v)| (k.clone(), v.clone()))
        .collect()
}

impl Client {
    /// Starts the sender thread.
    pub fn start(config: Config) -> Arc<Client> {
        let (tx, rx) = mpsc::channel();
        let anonymous_id = uuid::Uuid::new_v4().to_string();
        let client = Arc::new(Client {
            tx,
            identity: Mutex::new(Identity {
                distinct_id: anonymous_id.clone(),
                anonymous_id,
                identified: false,
            }),
            base_properties: config.base_properties.clone(),
        });
        std::thread::Builder::new()
            .name("posthog".into())
            .spawn(move || run(config, rx))
            .expect("spawning the PostHog thread");
        client
    }

    /// Queues an event for the current identity.
    pub fn capture(&self, name: &str, properties: &Properties) {
        let (distinct_id, identified) = {
            let id = self.identity.lock().unwrap_or_else(|e| e.into_inner());
            (id.distinct_id.clone(), id.identified)
        };
        let mut props = properties.clone();
        for (k, v) in &self.base_properties {
            props.entry(k.clone()).or_insert(v.clone());
        }
        if !identified {
            props.insert("$process_person_profile".into(), json!(false));
        }
        self.send(event_json(name, &distinct_id, props));
    }

    /// PostHog.identify: from now on events are this person's (the device ID, which is the
    /// analytics identity as on iOS); the earlier anonymous ID is linked to it.
    pub fn identify(&self, distinct_id: &str) {
        let anonymous_id = {
            let mut id = self.identity.lock().unwrap_or_else(|e| e.into_inner());
            if id.identified && id.distinct_id == distinct_id {
                return;
            }
            let anon = id.anonymous_id.clone();
            id.distinct_id = distinct_id.to_string();
            id.identified = true;
            anon
        };
        let mut props = self.base_properties.clone();
        props.insert("$anon_distinct_id".into(), json!(anonymous_id));
        self.send(event_json("$identify", distinct_id, props));
    }

    /// Sends what's queued and waits (at most `timeout`) for the request to finish; for before
    /// the app stops on purpose.
    pub fn flush(&self, timeout: Duration) {
        let (ack_tx, ack_rx) = mpsc::channel();
        if self.tx.send(Msg::Flush(ack_tx)).is_ok() {
            let _ = ack_rx.recv_timeout(timeout);
        }
    }

    fn send(&self, event: Value) {
        let _ = self.tx.send(Msg::Event(event));
    }
}

fn event_json(name: &str, distinct_id: &str, mut properties: Properties) -> Value {
    properties.insert("$lib".into(), json!("nijii-rust"));
    json!({
        "event": name,
        "distinct_id": distinct_id,
        "properties": properties,
        "timestamp": chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true),
    })
}

fn run(config: Config, rx: mpsc::Receiver<Msg>) {
    let url = format!("{}/batch/", config.host.trim_end_matches('/'));
    let agent: ureq::Agent = ureq::Agent::config_builder()
        .timeout_global(Some(Duration::from_secs(15)))
        .build()
        .into();
    let mut queue: Vec<Value> = Vec::new();
    let mut deadline = Instant::now() + config.flush_interval;
    loop {
        let msg = rx.recv_timeout(deadline.saturating_duration_since(Instant::now()));
        let mut ack = None;
        let flush_now;
        match msg {
            Ok(Msg::Event(e)) => {
                queue.push(e);
                if queue.len() > config.max_queue_size {
                    queue.remove(0);
                }
                flush_now = queue.len() >= config.flush_at;
            }
            Ok(Msg::Flush(a)) => {
                ack = Some(a);
                flush_now = true;
            }
            Err(RecvTimeoutError::Timeout) => flush_now = true,
            Err(RecvTimeoutError::Disconnected) => {
                post_all(&agent, &url, &config, &mut queue);
                return;
            }
        }
        if flush_now {
            post_all(&agent, &url, &config, &mut queue);
            deadline = Instant::now() + config.flush_interval;
        }
        if let Some(a) = ack {
            let _ = a.send(());
        }
    }
}

/// Posts the queue in batches of at most `max_batch_size`. A batch that fails stays queued for
/// the next flush.
fn post_all(agent: &ureq::Agent, url: &str, config: &Config, queue: &mut Vec<Value>) {
    while !queue.is_empty() {
        let n = queue.len().min(config.max_batch_size);
        let body = batch_body(&config.api_key, &queue[..n]).to_string();
        let sent = agent
            .post(url)
            .header("Content-Type", "application/json")
            .send(body.as_bytes())
            .is_ok();
        if !sent {
            return;
        }
        queue.drain(..n);
    }
}
