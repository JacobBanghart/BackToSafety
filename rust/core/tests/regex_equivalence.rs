//! The four patterns the core matches by hand (phone extension, `{{placeholder}}`, the
//! MM/DD/YYYY date of birth, the height without a closing quote) against the regexes they
//! replaced, on every string up to a length over a small alphabet that holds each pattern's
//! edge characters, plus a pseudo-random sweep of longer ones. The regex crate is a dev
//! dependency only: it cost 500 KB of the shipped library.

use nijii_core::age::split_dob;
use nijii_core::formatters::is_height_without_closing_quote;
use nijii_core::i18n::interpolate;
use nijii_core::phone::find_extension;
use regex::Regex;

/// Every string of `alphabet` up to `max_len`, then `random` longer ones from a fixed seed.
fn strings(alphabet: &[&str], max_len: usize, random: usize) -> Vec<String> {
    let mut all = vec![String::new()];
    let mut layer = vec![String::new()];
    for _ in 0..max_len {
        layer = layer
            .iter()
            .flat_map(|s| alphabet.iter().map(move |a| format!("{s}{a}")))
            .collect();
        all.extend(layer.iter().cloned());
    }
    let mut state = 0x9E37_79B9_7F4A_7C15_u64;
    for _ in 0..random {
        let mut s = String::new();
        for _ in 0..(8 + state % 17) {
            state = state
                .wrapping_mul(6364136223846793005)
                .wrapping_add(1442695040888963407);
            s += alphabet[(state >> 33) as usize % alphabet.len()];
        }
        all.push(s);
    }
    all
}

#[test]
fn phone_extension() {
    let re = Regex::new(r"(?i)[ \t\n\r]*(?:ext\.?|x|#)[ \t\n\r]*([0-9]+)[ \t\n\r]*$").unwrap();
    let alphabet = [
        "5", "x", "X", "e", "t", ".", "#", " ", "\t", "\n", "é", "x5",
    ];
    for s in strings(&alphabet, 6, 20_000) {
        let expected = re
            .captures(&s)
            .map(|c| (c.get(0).unwrap().start(), c[1].to_string()));
        let actual = find_extension(&s).map(|(start, digits)| (start, digits.to_string()));
        assert_eq!(actual, expected, "{s:?}");
    }
    for s in [
        "555-1234 ext. 22",
        "555 EXT 9 ",
        "5551234x7",
        "ext5",
        "x",
        "123#4",
        "1 ext.",
        "12 e x 3",
    ] {
        let expected = re
            .captures(s)
            .map(|c| (c.get(0).unwrap().start(), c[1].to_string()));
        assert_eq!(
            find_extension(s).map(|(a, b)| (a, b.to_string())),
            expected,
            "{s:?}"
        );
    }
}

#[test]
fn placeholders() {
    let re = Regex::new(r"\{\{[ \t]*([A-Za-z0-9_]+)[ \t]*\}\}").unwrap();
    let known = |name: &str| (name == "a" || name == "name_1").then(|| format!("<{name}>"));
    let alphabet = [
        "{", "}", "a", "name_1", "b", " ", "\t", "\n", "é", "-", "{{", "}}",
    ];
    for s in strings(&alphabet, 6, 30_000) {
        let expected = re
            .replace_all(&s, |c: &regex::Captures| {
                known(&c[1]).unwrap_or_else(|| c[0].to_string())
            })
            .into_owned();
        assert_eq!(interpolate(&s, known), expected, "{s:?}");
    }
    assert_eq!(
        interpolate("Hi {{ name_1 }}, {{b}} {{a}}!", known),
        "Hi <name_1>, {{b}} <a>!"
    );
}

#[test]
fn date_of_birth() {
    let re = Regex::new(r"^([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})$").unwrap();
    let alphabet = ["1", "0", "9", "/", "٣", "\n", " ", "12", "2024"];
    for s in strings(&alphabet, 7, 20_000) {
        let expected = re
            .captures(&s)
            .map(|c| (c[1].to_string(), c[2].to_string(), c[3].to_string()));
        let actual = split_dob(&s).map(|(m, d, y)| (m.to_string(), d.to_string(), y.to_string()));
        assert_eq!(actual, expected, "{s:?}");
    }
}

#[test]
fn height_without_closing_quote() {
    let re = Regex::new(r"^[0-9]'[0-9]{1,2}$").unwrap();
    let alphabet = ["5", "1", "'", "\"", "\n", " ", "٣", "11"];
    for s in strings(&alphabet, 6, 20_000) {
        assert_eq!(
            is_height_without_closing_quote(&s),
            re.is_match(&s),
            "{s:?}"
        );
    }
}
