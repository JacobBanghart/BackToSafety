import Foundation

/// utils/analytics.ts over the Rust core: the app reports `AnalyticsEvent`s and screen views
/// through this, the core names them, and the sink the app installs (PostHog, see
/// PostHogSetup.swift) receives them. Without a sink nothing is sent.
final class Analytics: @unchecked Sendable {
    static let shared = Analytics()

    /// Receives (event wire name, properties); screens arrive as `SCREEN` with `$screen_name`.
    var sink: ((String, [String: Any]) -> Void)? {
        didSet {
            guard let sink else { analyticsSetSink(sink: nil); return }
            analyticsSetSink(sink: ClosureSink(sink))
        }
    }

    /// Sends what the sink has queued now, not at its next batch (PostHog's flush).
    var flusher: () -> Void = {}

    /// Forgets who the sink has been reporting as (PostHog's reset).
    var resetter: () -> Void = {}

    let SCREEN = analyticsScreenEventName()

    /// Sends what's queued now: a failure that a crash may follow can't wait for the batch.
    func flush() { analyticsFlush() }

    /// Delete Account: later events start from a fresh anonymous identity, not the deleted
    /// device ID. What's queued is sent first; those events already carry the old identity.
    func reset() { analyticsReset() }

    func track(event: AnalyticsEvent, properties: [String: Any] = [:]) {
        analyticsTrack(event: event, properties: propValues(properties))
    }

    /// A screen view, by the RN app's route path (e.g. "emergency").
    func screen(path: String, properties: [String: Any] = [:]) {
        analyticsScreen(path: path, properties: propValues(properties))
    }
}

private final class ClosureSink: AnalyticsSink {
    private let body: (String, [String: Any]) -> Void
    init(_ body: @escaping (String, [String: Any]) -> Void) { self.body = body }
    func onEvent(name: String, properties: [String: PropValue]) {
        body(name, anyProperties(properties))
    }

    func flush() { Analytics.shared.flusher() }

    func reset() { Analytics.shared.resetter() }
}
