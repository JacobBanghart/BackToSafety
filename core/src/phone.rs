//! Phone display formatting, as-you-type formatting, dialing and SMS recipient normalization.
//! Held to spec/vectors/phone.json.

use crate::formatters::digits;

fn is_space(c: char) -> bool {
    matches!(c, ' ' | '\t' | '\n' | '\r')
}

/// The extension at the end of `phone`: where it starts (including the space before it) and its
/// digits. The pattern `(?i)[ \t\n\r]*(?:extension|ext\.?|x|#)[ \t\n\r]*([0-9]+)[ \t\n\r]*$`, by hand: the
/// regex crate cost 500 KB of the library. Leftmost match, so the longest keyword wins.
#[doc(hidden)]
pub fn find_extension(phone: &str) -> Option<(usize, &str)> {
    let before_trailing = phone.trim_end_matches(is_space);
    let digits_start = before_trailing
        .trim_end_matches(|c: char| c.is_ascii_digit())
        .len();
    let digits = &before_trailing[digits_start..];
    if digits.is_empty() {
        return None;
    }
    let before_digits = phone[..digits_start].trim_end_matches(is_space);
    let lower = before_digits.to_ascii_lowercase();
    let keyword_len = if lower.ends_with("extension") {
        9
    } else if lower.ends_with("ext.") {
        4
    } else if lower.ends_with("ext") {
        3
    } else if lower.ends_with('x') || lower.ends_with('#') {
        1
    } else {
        return None;
    };
    let keyword_start = before_digits.len() - keyword_len;
    Some((
        phone[..keyword_start].trim_end_matches(is_space).len(),
        digits,
    ))
}

/// `phone` split into the number and its trailing extension ("ext. 9", "x9", "#9"), if it has one.
fn split_extension(phone: &str) -> (&str, Option<&str>) {
    match find_extension(phone) {
        Some((start, ext)) if start > 0 => (&phone[..start], Some(ext)),
        _ => (phone, None),
    }
}

pub fn format_phone_number(phone: &str) -> String {
    // Keep an extension readable instead of folding it into the number (F-4).
    if let (number, Some(ext)) = split_extension(phone) {
        return format!("{} ext. {}", format_phone_number(number), ext);
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

/// What to hand the system dialer for a stored `phone`: its digits, a leading `+`, and `*` / `#`
/// only where the number starts with one (a service code such as `*67`; anywhere else they're
/// noise). An extension ("ext. 12", "extension 12", "x12", "#12") becomes a `,` pause then its
/// digits, so `555-123-4567 #12` dials `5551234567,12`. Both the Android dialer and iOS's honor
/// `,` (a two-second pause) in a `tel:` number and tone out the digits after it once the call
/// connects; `;` (wait for the user to confirm) isn't uniformly supported, and a raw `#` would
/// start the URI's fragment. Callers still have to percent-encode the result into the `tel:` URI
/// (`Uri.fromParts` on Android, URLComponents on iOS), since it can hold `#`, `*` and `+`.
/// Returns "" for a number with no digits.
pub fn dial_string(phone: &str) -> String {
    let (number, extension) = split_extension(phone);
    let trimmed = number.trim();
    let service_code = trimmed.starts_with('*') || trimmed.starts_with('#');
    let dialable: String = trimmed
        .chars()
        .enumerate()
        .filter(|&(i, c)| {
            c.is_ascii_digit() || (c == '+' && i == 0) || (service_code && (c == '*' || c == '#'))
        })
        .map(|(_, c)| c)
        .collect();
    if !dialable.chars().any(|c| c.is_ascii_digit()) {
        return String::new();
    }
    match extension {
        Some(ext) => format!("{dialable},{ext}"),
        None => dialable,
    }
}

/// Whether `phone` can be saved as a contact's number: its number (an extension doesn't count)
/// has at least three digits. A "+", a few punctuation marks or a lone digit would be saved and
/// then dropped from the emergency text, and nothing could dial it. Three is the shortest real
/// number, an emergency or service code such as 911 or 112, so the rule stays out of the way of
/// numbers from anywhere; the as-you-type format already does the rest of the shaping.
pub fn is_usable_phone(phone: &str) -> bool {
    normalize_sms_recipient(phone)
        .chars()
        .filter(char::is_ascii_digit)
        .count()
        >= 3
}

pub fn normalize_sms_recipient(phone: &str) -> String {
    // A text goes to the number, not the extension (which would otherwise fold into it).
    let trimmed = split_extension(phone).0.trim();
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
