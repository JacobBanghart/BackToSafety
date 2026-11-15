import Foundation

/// The RN app's Date.toISOString() form ("2026-10-06T16:00:00.000Z"), and without fractions.
func parseISO(_ s: String) -> Date? {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let d = f.date(from: s) { return d }
    f.formatOptions = [.withInternetDateTime]
    return f.date(from: s)
}

func isoString(_ ms: Int64) -> String {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}

/// The calendar for any date that crosses into the shared core or is stored: its `LocalDate` is
/// always Gregorian, while `Calendar.current` follows the region (Buddhist years, a Hebrew 13th
/// month, a Persian 31st day) and would crash `LocalDate(...)` or save the wrong year.
var gregorian: Calendar { Calendar(identifier: .gregorian) }

extension Date {
    var epochMs: Int64 { Int64(timeIntervalSince1970 * 1000) }
}
