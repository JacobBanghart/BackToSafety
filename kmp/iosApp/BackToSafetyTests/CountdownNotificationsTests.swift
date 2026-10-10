@testable import BackToSafety
import Shared
import UserNotifications
import XCTest

/// The iOS side of the countdown alerts: what shared EmergencyAway schedules becomes a
/// notification request per alert, translated, due when the alert is, and cleared on return.
/// The Android twin is CountdownNotificationsTest.
@MainActor
final class CountdownNotificationsTests: XCTestCase {
    private final class FakeCenter: AlertNotificationCenter {
        var pending: [UNNotificationRequest] = []
        var delivered: Set<String> = []

        func addRequest(_ request: UNNotificationRequest) {
            pending.removeAll { $0.identifier == request.identifier }
            pending.append(request)
        }

        func removePendingNotificationRequests(withIdentifiers identifiers: [String]) {
            pending.removeAll { identifiers.contains($0.identifier) }
        }

        func removeDeliveredNotifications(withIdentifiers identifiers: [String]) {
            delivered.subtract(identifiers)
        }
    }

    /// 2026-10-06 16:00 UTC: when the emergency in these tests started.
    private let start: Int64 = 1_791_302_400_000
    private var center: FakeCenter!
    private var model: AppModel!
    private var language = "en"
    private var notifications: CountdownNotifications!

    override func setUp() async throws {
        AppClock.shared.testSeamsEnabled = true
        AppClock.shared.freeze(atMs: start)
        center = FakeCenter()
        model = AppModel(store: DatabaseBuilder_iosKt.openStoreAt(path: NSTemporaryDirectory() + "alerts-\(UUID().uuidString).db"))
        notifications = CountdownNotifications(
            center: center,
            emergencyT: { [unowned self] in model.t("emergency", in: language) },
            emergencyNumber: { "911" }
        )
    }

    private func interval(_ request: UNNotificationRequest) -> TimeInterval? {
        (request.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval
    }

    func testEachAlertIsARequestDueWhenTheAlertIs() {
        notifications.schedule(alerts: [
            ScheduledAlert(alert: .warning, fireAtMs: start + 601_000),
            ScheduledAlert(alert: .expired, fireAtMs: start + 900_000),
        ])
        XCTAssertEqual(center.pending.map(\.identifier), ["countdown-warning", "countdown-expired"])
        XCTAssertEqual(center.pending.map(interval), [601, 900])
        XCTAssertEqual(center.pending.map(\.content.title), ["Under 5 Minutes Left", "Time to Call 911"])
    }

    func testTheTextIsInTheAppsLanguage() {
        language = "es"
        notifications.schedule(alerts: [ScheduledAlert(alert: .expired, fireAtMs: start + 900_000)])
        XCTAssertEqual(center.pending.first?.content.title, "Hora de llamar al 911")
        XCTAssertTrue(center.pending.first?.content.body.hasPrefix("Terminaron los 15 minutos") ?? false)
    }

    func testSchedulingReplacesAndCancellingClears() {
        notifications.schedule(alerts: [
            ScheduledAlert(alert: .warning, fireAtMs: start + 601_000),
            ScheduledAlert(alert: .expired, fireAtMs: start + 900_000),
        ])
        notifications.schedule(alerts: [ScheduledAlert(alert: .expired, fireAtMs: start + 900_000)])
        XCTAssertEqual(center.pending.map(\.identifier), ["countdown-expired"])
        center.delivered = ["countdown-expired"]
        notifications.cancelAll()
        XCTAssertTrue(center.pending.isEmpty)
        XCTAssertTrue(center.delivered.isEmpty)
    }

    /// Through the shared rules: leaving the emergency screen schedules what's to come, and
    /// returning clears it and catches up on what came due.
    func testLeavingAndReturning() async throws {
        let away = EmergencyAway(store: model.store, scheduler: notifications)
        _ = try await model.store.saveActiveEmergency(e: ActiveEmergency(
            startedAt: "2026-10-06T16:00:00.000Z", wearing: "", checkedSteps: [], incidentId: nil
        ))
        AppClock.shared.freeze(atMs: start + 120_000)
        _ = try await away.left(nowMs: start + 120_000)
        XCTAssertEqual(center.pending.map(\.identifier), ["countdown-warning", "countdown-expired"])
        XCTAssertEqual(center.pending.map(interval), [481, 780])

        AppClock.shared.freeze(atMs: start + 700_000)
        let caughtUp = try await away.shown(nowMs: start + 700_000)
        XCTAssertEqual(caughtUp, .warning)
        XCTAssertTrue(center.pending.isEmpty)
    }
}
