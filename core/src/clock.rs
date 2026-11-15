//! The app's notion of "now" (utils/clock.ts, AppClock.kt). Everything time-sensitive reads
//! [`AppClock::now_ms`], so test builds can freeze and advance time through the debug clock seam
//! (`backtosafety://debug/clock?at=<ISO>`, `?advance=<seconds>`). The app turns
//! [`AppClock::set_test_seams`] on only in debug builds; without it freeze and advance do
//! nothing.

use crate::emergency::parse_instant_ms;
use chrono::{DateTime, NaiveDate};
use std::sync::Mutex;

struct State {
    test_seams: bool,
    offset_ms: i64,
    frozen_at_ms: Option<i64>,
}

static STATE: Mutex<State> = Mutex::new(State {
    test_seams: false,
    offset_ms: 0,
    frozen_at_ms: None,
});

fn state() -> std::sync::MutexGuard<'static, State> {
    STATE.lock().unwrap_or_else(|e| e.into_inner())
}

pub struct AppClock;

impl AppClock {
    pub fn set_test_seams(enabled: bool) {
        state().test_seams = enabled;
    }

    pub fn now_ms() -> i64 {
        let s = state();
        s.frozen_at_ms
            .unwrap_or_else(|| chrono::Utc::now().timestamp_millis() + s.offset_ms)
    }

    /// Today's date where the device is, `utc_offset_seconds` from UTC at this moment, in the
    /// Gregorian calendar whatever calendar the user's region prefers. Both apps take "today"
    /// from here rather than from the platform's calendar (whose year, month and day can be
    /// Buddhist, Hebrew, Persian and so on), and it follows the debug clock too. The platform
    /// supplies the offset (its time zone's, at [`AppClock::now_ms`]): the core has no time zone
    /// database.
    pub fn today(utc_offset_seconds: i32) -> NaiveDate {
        Self::date_of(Self::now_ms(), utc_offset_seconds)
    }

    /// The Gregorian date of `epoch_ms` where the device is, `utc_offset_seconds` from UTC at
    /// that moment, as [`AppClock::today`] reckons it.
    pub fn date_of(epoch_ms: i64, utc_offset_seconds: i32) -> NaiveDate {
        let local_ms = epoch_ms + i64::from(utc_offset_seconds) * 1000;
        DateTime::from_timestamp_millis(local_ms)
            .map(|d| d.date_naive())
            .unwrap_or_default()
    }

    /// Moves the clock forward (or back), frozen or not. Ignored without test seams.
    pub fn advance(ms: i64) {
        let mut s = state();
        if !s.test_seams {
            return;
        }
        match s.frozen_at_ms {
            Some(at) => s.frozen_at_ms = Some(at + ms),
            None => s.offset_ms += ms,
        }
    }

    /// Stops the clock at `at_ms`, so screenshots show the same times every run.
    pub fn freeze(at_ms: i64) {
        let mut s = state();
        if s.test_seams {
            s.frozen_at_ms = Some(at_ms);
        }
    }

    /// Back to the live clock (tests).
    #[cfg(test)]
    fn unfreeze_for_tests() {
        let mut s = state();
        s.frozen_at_ms = None;
        s.offset_ms = 0;
    }

    /// Applies a `backtosafety://debug/clock?...` link (MainActivity.handleTestSeam). Returns
    /// whether the link was a clock link.
    pub fn handle_link(uri: &str) -> bool {
        let Some(rest) = uri.strip_prefix("backtosafety://debug/clock") else {
            return false;
        };
        let query = rest.strip_prefix('?').unwrap_or("");
        for pair in query.split('&') {
            let (key, value) = pair.split_once('=').unwrap_or((pair, ""));
            let value = percent_decode(value);
            match key {
                "at" => {
                    if let Some(ms) = parse_instant_ms(&value) {
                        Self::freeze(ms);
                    }
                }
                "advance" => {
                    if let Ok(seconds) = value.parse::<f64>() {
                        Self::advance((seconds * 1000.0) as i64);
                    }
                }
                _ => {}
            }
        }
        true
    }
}

fn percent_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(byte) = u8::from_str_radix(&s[i + 1..i + 3], 16) {
                out.push(byte);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    // One test: the clock is process-global.
    #[test]
    fn seam_freezes_advances_and_is_off_without_test_seams() {
        AppClock::set_test_seams(false);
        assert!(AppClock::handle_link(
            "backtosafety://debug/clock?at=2026-10-06T09:00:00-07:00"
        ));
        assert!((AppClock::now_ms() - chrono::Utc::now().timestamp_millis()).abs() < 5_000);
        AppClock::advance(60_000);
        assert!((AppClock::now_ms() - chrono::Utc::now().timestamp_millis()).abs() < 5_000);

        AppClock::set_test_seams(true);
        AppClock::handle_link("backtosafety://debug/clock?at=2026-10-06T09:00:00-07:00");
        assert_eq!(AppClock::now_ms(), 1_791_302_400_000);
        // Today is the device's date on the app's clock (AppClockTest), and follows it.
        let pacific = -7 * 3600;
        assert_eq!(
            AppClock::today(pacific),
            NaiveDate::from_ymd_opt(2026, 10, 6).unwrap()
        );
        AppClock::advance(24 * 3_600_000);
        assert_eq!(
            AppClock::today(pacific),
            NaiveDate::from_ymd_opt(2026, 10, 7).unwrap()
        );
        AppClock::advance(-24 * 3_600_000);
        AppClock::handle_link("backtosafety://debug/clock?advance=90.5");
        assert_eq!(AppClock::now_ms(), 1_791_302_490_500);
        AppClock::handle_link("backtosafety://debug/clock?at=2026-10-06T16%3A00%3A00%2B00%3A00");
        assert_eq!(AppClock::now_ms(), 1_791_302_400_000);
        // An unparseable time or amount changes nothing; so does another link.
        AppClock::handle_link("backtosafety://debug/clock?at=soon&advance=lots");
        assert_eq!(AppClock::now_ms(), 1_791_302_400_000);
        assert!(!AppClock::handle_link("backtosafety://other"));
        assert_eq!(AppClock::now_ms(), 1_791_302_400_000);

        // Not frozen: advancing shifts the live clock, as AppClock.kt's offset does.
        AppClock::set_test_seams(false);
        AppClock::set_test_seams(true);
        AppClock::unfreeze_for_tests();
        let before = AppClock::now_ms();
        AppClock::advance(3_600_000);
        let shifted = AppClock::now_ms() - before;
        assert!((3_600_000..3_605_000).contains(&shifted), "{shifted}");
        AppClock::unfreeze_for_tests();
        AppClock::set_test_seams(false);
    }

    #[test]
    fn dates_are_gregorian_where_the_device_is() {
        // 2026-10-11T12:00Z: noon in UTC, the evening before in UTC-14, the next day in UTC+14.
        let noon_utc = 1_791_720_000_000;
        let date = |y, m, d| NaiveDate::from_ymd_opt(y, m, d).unwrap();
        assert_eq!(AppClock::date_of(noon_utc, 0), date(2026, 10, 11));
        assert_eq!(
            AppClock::date_of(noon_utc, -14 * 3600 + 1),
            date(2026, 10, 10)
        );
        assert_eq!(AppClock::date_of(noon_utc, 12 * 3600), date(2026, 10, 12));
        assert_eq!(
            AppClock::date_of(noon_utc + 24 * 3_600_000, 0),
            date(2026, 10, 12)
        );
    }
}
