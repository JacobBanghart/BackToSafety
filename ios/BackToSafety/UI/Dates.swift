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

extension Date {
    var epochMs: Int64 { Int64(timeIntervalSince1970 * 1000) }
}
