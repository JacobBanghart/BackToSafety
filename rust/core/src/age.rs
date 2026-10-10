//! Date of birth (stored as MM/DD/YYYY) and whole-year age. Held to spec/vectors/age.json.

use chrono::{Datelike, NaiveDate};
use regex::Regex;
use std::sync::LazyLock;

static DOB: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})$").unwrap());

pub fn parse_dob(value: &str) -> Option<NaiveDate> {
    let c = DOB.captures(value.trim())?;
    // Impossible dates (02/30, month 13) are rejected rather than rolled over.
    NaiveDate::from_ymd_opt(c[3].parse().ok()?, c[1].parse().ok()?, c[2].parse().ok()?)
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
