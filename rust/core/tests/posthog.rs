//! The PostHog client against a local fake of its HTTP batch API.

use nijii_core::alerts::Properties;
use nijii_core::posthog::{Client, Config, batch_body, without_nulls};
use serde_json::{Value, json};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::Arc;
use std::sync::mpsc::{Receiver, channel};
use std::time::Duration;

/// A server that answers each request with the next status (the last one repeats) and hands
/// over the request body.
fn fake_posthog(statuses: Vec<u16>) -> (String, Receiver<(String, Value)>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let host = format!("http://{}", listener.local_addr().unwrap());
    let (tx, rx) = channel();
    std::thread::spawn(move || {
        for (i, stream) in listener.incoming().enumerate() {
            let mut stream = stream.unwrap();
            let (path, body) = read_request(&mut stream);
            let status = statuses[i.min(statuses.len() - 1)];
            let _ = tx.send((path, serde_json::from_str(&body).unwrap_or(Value::Null)));
            let _ = write!(
                stream,
                "HTTP/1.1 {status} X\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{{}}"
            );
        }
    });
    (host, rx)
}

fn read_request(stream: &mut TcpStream) -> (String, String) {
    let mut data = Vec::new();
    let mut buf = [0u8; 4096];
    loop {
        let n = stream.read(&mut buf).unwrap();
        data.extend_from_slice(&buf[..n]);
        let text = String::from_utf8_lossy(&data).into_owned();
        if let Some((head, body)) = text.split_once("\r\n\r\n") {
            let len = head
                .lines()
                .find_map(|l| {
                    l.to_ascii_lowercase()
                        .strip_prefix("content-length:")
                        .map(|v| v.trim().parse::<usize>().unwrap())
                })
                .unwrap_or(0);
            if body.len() >= len {
                let path = head
                    .lines()
                    .next()
                    .unwrap()
                    .split_whitespace()
                    .nth(1)
                    .unwrap()
                    .to_string();
                return (path, body.to_string());
            }
        }
        if n == 0 {
            panic!("connection closed early");
        }
    }
}

fn config(host: &str) -> Config {
    let mut c = Config::new("phc_test", host);
    c.base_properties
        .insert("$app_version".into(), json!("1.3.3"));
    c
}

fn props(v: Value) -> Properties {
    v.as_object().unwrap().clone()
}

const WAIT: Duration = Duration::from_secs(10);

#[test]
fn flush_sends_the_queued_events_as_one_batch() {
    let (host, requests) = fake_posthog(vec![200]);
    let client = Client::start(config(&host));
    client.capture("app_ready", &props(json!({"startup_ms": 812})));
    client.capture("$screen", &props(json!({"$screen_name": "/"})));
    client.flush(WAIT);

    let (path, body) = requests.recv_timeout(WAIT).unwrap();
    assert_eq!(path, "/batch/");
    assert_eq!(body["api_key"], "phc_test");
    let events = body["batch"].as_array().unwrap();
    assert_eq!(events.len(), 2);
    assert_eq!(events[0]["event"], "app_ready");
    assert_eq!(events[0]["properties"]["startup_ms"], 812);
    assert_eq!(events[0]["properties"]["$app_version"], "1.3.3");
    assert_eq!(events[0]["properties"]["$lib"], "nijii-rust");
    assert_eq!(
        events[0]["properties"]["$process_person_profile"], false,
        "not identified yet"
    );
    assert_eq!(events[1]["properties"]["$screen_name"], "/");
    assert!(events[0]["timestamp"].as_str().unwrap().ends_with('Z'));
    assert!(requests.try_recv().is_err(), "one request");
}

#[test]
fn identify_links_the_anonymous_id_and_profiles_the_person() {
    let (host, requests) = fake_posthog(vec![200]);
    let client = Client::start(config(&host));
    client.capture("data_migrated", &Properties::new());
    client.identify("device-1");
    client.identify("device-1"); // again: nothing new
    client.capture("app_ready", &Properties::new());
    client.flush(WAIT);

    let (_, body) = requests.recv_timeout(WAIT).unwrap();
    let events = body["batch"].as_array().unwrap();
    assert_eq!(events.len(), 3);
    let anon = events[0]["distinct_id"].as_str().unwrap();
    assert_ne!(anon, "device-1");
    assert_eq!(events[1]["event"], "$identify");
    assert_eq!(events[1]["distinct_id"], "device-1");
    assert_eq!(events[1]["properties"]["$anon_distinct_id"], anon);
    assert_eq!(events[2]["distinct_id"], "device-1");
    assert!(
        events[2]["properties"]
            .get("$process_person_profile")
            .is_none()
    );
}

#[test]
fn reaching_flush_at_sends_without_a_flush() {
    let (host, requests) = fake_posthog(vec![200]);
    let mut c = config(&host);
    c.flush_at = 2;
    c.flush_interval = Duration::from_secs(3600);
    let client = Client::start(c);
    client.capture("a", &Properties::new());
    assert!(
        requests.recv_timeout(Duration::from_millis(300)).is_err(),
        "one event waits"
    );
    client.capture("b", &Properties::new());
    let (_, body) = requests.recv_timeout(WAIT).unwrap();
    assert_eq!(body["batch"].as_array().unwrap().len(), 2);
}

#[test]
fn the_flush_interval_sends_what_is_waiting() {
    let (host, requests) = fake_posthog(vec![200]);
    let mut c = config(&host);
    c.flush_interval = Duration::from_millis(100);
    let client = Client::start(c);
    client.capture("a", &Properties::new());
    let (_, body) = requests.recv_timeout(WAIT).unwrap();
    assert_eq!(body["batch"][0]["event"], "a");
}

#[test]
fn batches_are_capped() {
    let (host, requests) = fake_posthog(vec![200]);
    let mut c = config(&host);
    c.max_batch_size = 2;
    c.flush_at = 1000;
    let client = Client::start(c);
    for i in 0..5 {
        client.capture(&format!("e{i}"), &Properties::new());
    }
    client.flush(WAIT);
    let sizes: Vec<usize> = (0..3)
        .map(|_| {
            requests.recv_timeout(WAIT).unwrap().1["batch"]
                .as_array()
                .unwrap()
                .len()
        })
        .collect();
    assert_eq!(sizes, vec![2, 2, 1]);
}

#[test]
fn a_failed_batch_is_retried_with_the_next_flush() {
    let (host, requests) = fake_posthog(vec![500, 200]);
    let client = Client::start(config(&host));
    client.capture("a", &Properties::new());
    client.flush(WAIT);
    assert_eq!(
        requests.recv_timeout(WAIT).unwrap().1["batch"]
            .as_array()
            .unwrap()
            .len(),
        1
    );
    client.capture("b", &Properties::new());
    client.flush(WAIT);
    let (_, body) = requests.recv_timeout(WAIT).unwrap();
    let names: Vec<_> = body["batch"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e["event"].clone())
        .collect();
    assert_eq!(names, vec![json!("a"), json!("b")]);
}

#[test]
fn the_queue_drops_the_oldest_events_when_full() {
    let (host, requests) = fake_posthog(vec![500, 200]);
    let mut c = config(&host);
    c.max_queue_size = 3;
    c.flush_at = 1000;
    let client = Client::start(c);
    for i in 0..5 {
        client.capture(&format!("e{i}"), &Properties::new());
    }
    client.flush(WAIT); // 500
    let _ = requests.recv_timeout(WAIT).unwrap();
    client.flush(WAIT);
    let (_, body) = requests.recv_timeout(WAIT).unwrap();
    let names: Vec<_> = body["batch"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e["event"].as_str().unwrap().to_string())
        .collect();
    assert_eq!(names, vec!["e2", "e3", "e4"]);
}

#[test]
fn an_unreachable_host_does_not_block_or_panic() {
    let client: Arc<Client> = Client::start(config("http://127.0.0.1:1"));
    client.capture("a", &Properties::new());
    client.flush(Duration::from_secs(5));
}

#[test]
fn nulls_are_dropped_and_the_body_has_the_batch_shape() {
    assert_eq!(
        without_nulls(&props(json!({"away_s": 61, "catch_up": null}))),
        props(json!({"away_s": 61}))
    );
    assert_eq!(
        batch_body("k", &[json!({"event": "x"})]),
        json!({"api_key": "k", "batch": [{"event": "x"}]})
    );
}
