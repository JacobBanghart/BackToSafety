//! Phone display formatting, as-you-type formatting and SMS recipient normalization.
//! Held to spec/vectors/phone.json.

use crate::formatters::digits;
use regex::Regex;
use std::sync::LazyLock;

static EXTENSION: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)[ \t\n\r]*(?:ext\.?|x|#)[ \t\n\r]*([0-9]+)[ \t\n\r]*$").unwrap()
});

pub fn format_phone_number(phone: &str) -> String {
    // Keep an extension readable instead of folding it into the number (F-4).
    if let Some(c) = EXTENSION.captures(phone) {
        let start = c.get(0).unwrap().start();
        if start > 0 {
            return format!("{} ext. {}", format_phone_number(&phone[..start]), &c[1]);
        }
    }
    let d = digits(phone);
    // Non-US international numbers have their own grouping; show them as entered (F-1).
    if phone.trim_start().starts_with('+') && !d.starts_with('1') {
        return phone.trim().to_string();
    }
    match d.len() {
        10 => format!("({}) {}-{}", &d[..3], &d[3..6], &d[6..]),
        11 if d.starts_with('1') => format!("+1 ({}) {}-{}", &d[1..4], &d[4..7], &d[7..]),
        n if n > 6 => format!("{}-{}-{}", &d[..3], &d[3..6], &d[6..]),
        _ => phone.to_string(),
    }
}

/// `(555) 123-4567` grouping of up to ten local digits.
fn group_us(local: &str) -> String {
    match local.len() {
        0..=3 => local.to_string(),
        4..=6 => format!("({}) {}", &local[..3], &local[3..]),
        n => format!(
            "({}) {}-{}",
            &local[..3],
            &local[3..6],
            &local[6..n.min(10)]
        ),
    }
}

pub fn format_phone_input(value: &str) -> String {
    let has_country_code = value.trim_start().starts_with('+');
    let d = digits(value);

    // A + prefix (e.g. an imported contact) keeps +1 visible.
    if has_country_code && d.starts_with('1') {
        let local = &d[1..];
        return match local.len() {
            n if n > 10 => format!("+{d}"),
            0 => "+1 ".to_string(),
            _ => format!("+1 {}", group_us(local)),
        };
    }
    // Other country codes: keep every digit, unformatted (F-1).
    if has_country_code {
        return format!("+{d}");
    }
    // Standard 10-digit US format (a leading 1 without + is dropped).
    let local = if d.len() == 11 && d.starts_with('1') {
        &d[1..]
    } else {
        &d[..]
    };
    // Too long to be a US number: never drop digits (F-2).
    if local.len() > 10 {
        return d;
    }
    group_us(local)
}

pub fn strip_phone_formatting(phone: &str) -> String {
    digits(phone)
}

pub fn normalize_sms_recipient(phone: &str) -> String {
    let trimmed = phone.trim();
    let d = digits(trimmed);
    if d.is_empty() {
        return String::new();
    }
    if trimmed.starts_with('+') {
        format!("+{d}")
    } else {
        d
    }
}

pub fn normalize_unique_sms_recipients(phones: &[String]) -> Vec<String> {
    let mut seen = Vec::<(String, String)>::new();
    for phone in phones
        .iter()
        .map(|p| normalize_sms_recipient(p))
        .filter(|p| !p.is_empty())
    {
        // The same US number with and without its +1 country code is one recipient (F-3).
        let d = phone.trim_start_matches('+');
        let key = if d.len() == 11 && d.starts_with('1') {
            &d[1..]
        } else {
            d
        }
        .to_string();
        if !seen.iter().any(|(k, _)| *k == key) {
            seen.push((key, phone));
        }
    }
    seen.into_iter().map(|(_, p)| p).collect()
}
