import Foundation

/// The app's notion of "now" (utils/clock.ts), kept in the Rust core so the store and
/// EmergencyAway read the same clock the UI freezes. Test builds can freeze and advance it
/// through the backtosafety://debug/clock link; the app turns `testSeamsEnabled` on only in
/// builds made with test seams.
final class AppClock: @unchecked Sendable {
    static let shared = AppClock()

    var testSeamsEnabled = false {
        didSet { appClockSetTestSeams(enabled: testSeamsEnabled) }
    }

    func nowMs() -> Int64 { appClockNowMs() }

    /// Today in the device's time zone, by this clock.
    func today() -> CalendarDate { dateOf(epochMs: nowMs()) }

    /// The day `epochMs` falls on in the device's time zone (the core has no zone database).
    func dateOf(epochMs: Int64) -> CalendarDate {
        let offset = TimeZone.current.secondsFromGMT(for: Date(timeIntervalSince1970: Double(epochMs) / 1000))
        return appClockDateOf(epochMs: epochMs, utcOffsetSeconds: Int32(offset))
    }

    /// Moves the clock forward (or back), frozen or not. Ignored without test seams.
    func advance(ms: Int64) { appClockAdvance(ms: ms) }

    /// Stops the clock at `atMs`, so screenshots show the same times every run.
    func freeze(atMs: Int64) { appClockFreeze(atMs: atMs) }

    /// Applies a backtosafety://debug/clock link; false if it wasn't one.
    @discardableResult
    func handleLink(_ url: URL) -> Bool { appClockHandleLink(uri: url.absoluteString) }
}
