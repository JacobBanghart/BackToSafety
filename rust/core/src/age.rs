//! Date of birth (stored as MM/DD/YYYY) and whole-year age. Held to spec/vectors/age.json.

use chrono::{Datelike, NaiveDate};

/// `^([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})$`, by hand: the regex crate cost 500 KB of the library.
/// Returns (month, day, year) as typed.
#[doc(hidden)]
pub fn split_dob(value: &str) -> Option<(&str, &str, &str)> {
    let mut parts = value.split('/');
    let (m, d, y) = (parts.next()?, parts.next()?, parts.next()?);
    let digits = |s: &str, lens: std::ops::RangeInclusive<usize>| {
        lens.contains(&s.len()) && s.bytes().all(|b| b.is_ascii_digit())
    };
    (parts.next().is_none() && digits(m, 1..=2) && digits(d, 1..=2) && digits(y, 4..=4))
        .then_some((m, d, y))
}

pub fn parse_dob(value: &str) -> Option<NaiveDate> {
    let (month, day, year) = split_dob(value.trim())?;
    // Impossible dates (02/30, month 13) are rejected rather than rolled over.
    NaiveDate::from_ymd_opt(year.parse().ok()?, month.parse().ok()?, day.parse().ok()?)
}

/// Whole years between `dob` and `today`; None if the DOB is unparseable or in the future.
pub fn age_on(dob: &str, today: NaiveDate) -> Option<i32> {
    let birth = parse_dob(dob)?;
    let mut age = today.year() - birth.year();
    if (today.month(), today.day()) < (birth.month(), birth.day()) {
        age -= 1;
    }
    (age >= 0).then_some(age)
}

/// The stored MM/DD/YYYY form of a date.
pub fn format_dob(date: NaiveDate) -> String {
    format!("{:02}/{:02}/{}", date.month(), date.day(), date.year())
}
