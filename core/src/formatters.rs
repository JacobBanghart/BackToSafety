//! As-you-type formatters for profile fields. Held to spec/vectors/formatters.json.

/// `^[0-9]'[0-9]{1,2}$`: feet and inches typed without the closing quote.
#[doc(hidden)]
pub fn is_height_without_closing_quote(value: &str) -> bool {
    let b = value.as_bytes();
    matches!(b.len(), 3 | 4)
        && b[0].is_ascii_digit()
        && b[1] == b'\''
        && b[2..].iter().all(u8::is_ascii_digit)
}

pub(crate) fn digits(value: &str) -> String {
    value.chars().filter(char::is_ascii_digit).collect()
}

fn take(s: &str, n: usize) -> &str {
    &s[..s.len().min(n)]
}

/// Feet and inches from up to three digits: 5 → 5, 56 → 5'6", 511 → 5'11".
pub fn format_height_input(value: &str) -> String {
    let mut d = take(&digits(value), 3).to_string();
    // `5'6` (closing quote gone) means backspace just removed the quote; remove the
    // last digit too, or the quote comes straight back (F-11).
    if is_height_without_closing_quote(value) {
        d.pop();
    }
    match d.len() {
        0 | 1 => d,
        _ => format!("{}'{}\"", &d[..1], &d[1..]),
    }
}

/// Up to four digits with a thousands separator: 150 → 150, 1500 → 1,500.
pub fn format_weight_input(value: &str) -> String {
    let d = take(&digits(value), 4).to_string();
    if d.len() > 3 {
        format!("{},{}", &d[..d.len() - 3], &d[d.len() - 3..])
    } else {
        d
    }
}

/// Up to 16 letters/digits, upper-cased, in dash-separated groups of four.
pub fn format_medic_alert_id_input(value: &str) -> String {
    let id: Vec<char> = value
        .chars()
        .filter(char::is_ascii_alphanumeric)
        .map(|c| c.to_ascii_uppercase())
        .take(16)
        .collect();
    id.chunks(4)
        .map(|c| c.iter().collect::<String>())
        .collect::<Vec<_>>()
        .join("-")
}

/// MM/DD/YYYY from up to eight typed digits.
pub fn format_dob_input(value: &str) -> String {
    let d = take(&digits(value), 8).to_string();
    match d.len() {
        0..=2 => d,
        3..=4 => format!("{}/{}", &d[..2], &d[2..]),
        _ => format!("{}/{}/{}", &d[..2], &d[2..4], &d[4..]),
    }
}
