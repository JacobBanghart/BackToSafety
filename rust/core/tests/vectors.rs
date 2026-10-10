//! Runs every JSON file in spec/vectors against the Rust core: the twin of kmp/shared's
//! VectorsTest. Every case must have a blessed `expected`.

use chrono::{DateTime, NaiveDate, SecondsFormat, Utc};
use nijii_core::alerts::*;
use nijii_core::emergency::*;
use nijii_core::readout::*;
use nijii_core::{Profile, Translations, Translator, age, formatters as f, mobility, phone, photo};
use serde_json::{Map, Value, json};
use std::path::{Path, PathBuf};
use std::sync::Arc;

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn translations() -> Arc<Translations> {
    let mut files = Vec::new();
    for locale in std::fs::read_dir(root().join("i18n/locales"))
        .unwrap()
        .flatten()
        .filter(|e| e.path().is_dir())
    {
        for file in std::fs::read_dir(locale.path()).unwrap().flatten() {
            let p = file.path();
            if p.extension().is_some_and(|e| e == "json") {
                let loc = locale.file_name().to_string_lossy().into_owned();
                let ns = p.file_stem().unwrap().to_string_lossy().into_owned();
                files.push((loc, ns, std::fs::read_to_string(&p).unwrap()));
            }
        }
    }
    Arc::new(Translations::from_json(
        files
            .iter()
            .map(|(l, n, t)| (l.as_str(), n.as_str(), t.as_str())),
    ))
}

fn s(a: &[Value], i: usize) -> &str {
    a[i].as_str().unwrap()
}
fn int(a: &[Value], i: usize) -> i64 {
    a[i].as_i64().unwrap()
}
fn date(iso: &str) -> NaiveDate {
    iso.parse().unwrap()
}
fn ms(iso: &str) -> i64 {
    parse_instant_ms(iso).unwrap()
}
fn iso(ms: i64) -> Value {
    json!(
        DateTime::<Utc>::from_timestamp_millis(ms)
            .unwrap()
            .to_rfc3339_opts(SecondsFormat::AutoSi, true)
    )
}
fn alert(key: &str) -> CountdownAlert {
    CountdownAlert::from_key(key).unwrap()
}
fn profile(v: &Value) -> Profile {
    serde_json::from_value(v.clone()).unwrap()
}
fn readout_input(v: &Value) -> ReadoutInput {
    ReadoutInput {
        profile: profile(&v["profile"]),
        last_seen_time: v
            .get("lastSeenTime")
            .and_then(Value::as_str)
            .map(str::to_string),
        today: date(v["today"].as_str().unwrap()),
    }
}

fn run(fun: &str, a: &[Value], tr: &Arc<Translations>) -> Option<Value> {
    let rt = || -> Translator { tr.translator(s(a, 0), "readout") };
    let et = || -> Translator { tr.translator(s(a, 0), "emergency") };
    Some(match fun {
        "parseDob" => json!(age::parse_dob(s(a, 0)).map(|d| d.to_string())),
        "ageOn" => json!(age::age_on(s(a, 0), date(s(a, 1)))),
        "formatDob" => json!(age::format_dob(date(s(a, 0)))),
        "formatHeightInput" => json!(f::format_height_input(s(a, 0))),
        "formatWeightInput" => json!(f::format_weight_input(s(a, 0))),
        "formatMedicAlertIdInput" => json!(f::format_medic_alert_id_input(s(a, 0))),
        "formatDobInput" => json!(f::format_dob_input(s(a, 0))),
        "formatPhoneNumber" => json!(phone::format_phone_number(s(a, 0))),
        "formatPhoneInput" => json!(phone::format_phone_input(s(a, 0))),
        "stripPhoneFormatting" => json!(phone::strip_phone_formatting(s(a, 0))),
        "normalizeSmsRecipient" => json!(phone::normalize_sms_recipient(s(a, 0))),
        "normalizeUniqueSmsRecipients" => {
            let phones: Vec<String> = serde_json::from_value(a[0].clone()).unwrap();
            json!(phone::normalize_unique_sms_recipients(&phones))
        }
        "photoFileName" => json!(photo::photo_file_name(s(a, 0))),
        "describeMobility" => json!(mobility::describe_mobility(s(a, 1), &rt())),
        "needsVehicleCheck" => json!(needs_vehicle_check(a[0].as_str())),
        "vehicleCheckKind" => json!(vehicle_check_kind(s(a, 0)).key()),
        "buildScript" => json!(build_script(&readout_input(&a[1]), &rt())),
        "buildCopyBlock" => json!(build_copy_block(&readout_input(&a[1]), &rt())),
        "missingScriptDetails" => json!(missing_script_details(&readout_input(&a[1]), &rt())),
        "buildInitialSteps" => Value::Array(
            build_initial_steps(&et(), s(a, 1))
                .into_iter()
                .map(|st| {
                    let mut o = Map::new();
                    o.insert("id".into(), json!(st.id));
                    o.insert("step".into(), json!(st.step));
                    o.insert("title".into(), json!(st.title));
                    o.insert("description".into(), json!(st.description));
                    if let Some(h) = st.hint {
                        o.insert("hint".into(), json!(h));
                    }
                    if st.urgent {
                        o.insert("urgent".into(), json!(true));
                    }
                    o.insert("checked".into(), json!(st.checked));
                    Value::Object(o)
                })
                .collect(),
        ),
        "secondsRemaining" => json!(seconds_remaining(ms(s(a, 0)), ms(s(a, 1)))),
        "countdownAlerts" => json!(
            countdown_alerts(int(a, 0), int(a, 1))
                .iter()
                .map(|x| x.key())
                .collect::<Vec<_>>()
        ),
        "formatCountdown" => json!(format_countdown(int(a, 0))),
        "buildAlertSms" => {
            let o = &a[1];
            json!(build_alert_sms(
                &et(),
                o.get("name").and_then(Value::as_str),
                o["startedTime"].as_str().unwrap(),
                o["wearing"].as_str().unwrap()
            ))
        }
        "directionHint" => json!(direction_hint(&et(), a[1].as_str())),
        "alertDueAt" => iso(alert_due_at_ms(alert(s(a, 0)), ms(s(a, 1)))),
        "alertsToSchedule" => Value::Array(
            alerts_to_schedule(ms(s(a, 0)), ms(s(a, 1)))
                .into_iter()
                .map(|x| json!({ "alert": x.alert.key(), "fireAt": iso(x.fire_at_ms) }))
                .collect(),
        ),
        "catchUpAlert" => {
            json!(catch_up_alert(ms(s(a, 0)), ms(s(a, 1)), ms(s(a, 2))).map(CountdownAlert::key))
        }
        "countdownAlertProperties" => {
            let d = [AlertDelivery::InApp, AlertDelivery::CatchUp]
                .into_iter()
                .find(|d| d.key() == s(a, 1))
                .unwrap();
            Value::Object(countdown_alert_properties(alert(s(a, 0)), d))
        }
        "emergencyResumedProperties" => Value::Object(emergency_resumed_properties(
            ms(s(a, 0)),
            ms(s(a, 1)),
            a[2].as_str().map(alert),
        )),
        "emergencyEndedProperties" => Value::Object(emergency_ended_properties(
            ms(s(a, 0)),
            ms(s(a, 1)),
            int(a, 2) as usize,
        )),
        "readinessProperties" => {
            let p = a[0].is_object().then(|| profile(&a[0]));
            Value::Object(readiness_properties(
                p.as_ref(),
                int(a, 1) as usize,
                int(a, 2) as usize,
                int(a, 3) as usize,
            ))
        }
        "smsResultProperties" => {
            let r = SmsResult::ALL
                .into_iter()
                .find(|r| r.key() == s(a, 0))
                .unwrap();
            Value::Object(sms_result_properties(r, int(a, 1) as usize))
        }
        "saveFailedProperties" => {
            Value::Object(save_failed_properties(s(a, 0), s(a, 1), a[2].as_str()))
        }
        "dialFailedProperties" => {
            let t = DialTarget::ALL
                .into_iter()
                .find(|t| t.key() == s(a, 0))
                .unwrap();
            Value::Object(dial_failed_properties(t, s(a, 1)))
        }
        "parseActiveEmergency" => match parse_active_emergency(a[0].as_str()) {
            None => Value::Null,
            Some(e) => {
                let mut o = Map::new();
                o.insert("startedAt".into(), json!(e.started_at));
                o.insert("wearing".into(), json!(e.wearing));
                o.insert("checkedSteps".into(), json!(e.checked_steps));
                o.insert("isActive".into(), json!(true));
                if let Some(id) = e.incident_id {
                    o.insert("incidentId".into(), json!(id));
                }
                Value::Object(o)
            }
        },
        _ => return None,
    })
}

/// JSON equality where 900 and 900.0 are the same number.
fn same(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::Number(x), Value::Number(y)) => x.as_f64() == y.as_f64(),
        (Value::Array(x), Value::Array(y)) => {
            x.len() == y.len() && x.iter().zip(y).all(|(x, y)| same(x, y))
        }
        (Value::Object(x), Value::Object(y)) => {
            x.len() == y.len() && x.iter().all(|(k, v)| y.get(k).is_some_and(|w| same(v, w)))
        }
        _ => a == b,
    }
}

#[test]
fn every_vector_matches() {
    let tr = translations();
    let mut failures = Vec::new();
    let mut cases = 0;
    let mut files: Vec<_> = std::fs::read_dir(root().join("spec/vectors"))
        .unwrap()
        .flatten()
        .map(|e| e.path())
        .collect();
    files.sort();
    for file in files
        .iter()
        .filter(|p| p.extension().is_some_and(|e| e == "json"))
    {
        let name = file.file_name().unwrap().to_string_lossy();
        let spec: Value = serde_json::from_str(&std::fs::read_to_string(file).unwrap()).unwrap();
        for (fun, list) in spec["functions"].as_object().unwrap() {
            for (i, case) in list.as_array().unwrap().iter().enumerate() {
                cases += 1;
                let args = case["args"].as_array().unwrap();
                let Some(expected) = case.get("expected") else {
                    failures.push(format!("{name} {fun}[{i}]: no expected value"));
                    continue;
                };
                let actual = std::panic::catch_unwind(|| run(fun, args, &tr));
                match actual {
                    Ok(None) => {
                        failures.push(format!("{name} {fun}: no Rust adapter"));
                        break;
                    }
                    Ok(Some(actual)) if same(&actual, expected) => {}
                    Ok(Some(actual)) => failures.push(format!(
                        "{name} {fun}[{i}] {}\n    expected {expected}\n    actual   {actual}",
                        Value::Array(args.clone())
                    )),
                    Err(_) => failures.push(format!("{name} {fun}[{i}]: panicked")),
                }
            }
        }
    }
    assert!(
        failures.is_empty(),
        "{} of {cases} vector cases failed:\n{}",
        failures.len(),
        failures.join("\n")
    );
    println!("{cases} vector cases passed");
}
