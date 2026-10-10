import Shared
import UserNotifications

/// The countdown alerts as local notifications while the emergency screen isn't showing
/// (shared EmergencyAway decides when). The text is translated when scheduled, in the app's
/// language then. Tapping one opens the emergency ([NotificationDelegate]).
final class CountdownNotifications: NSObject, AlertScheduler {
    static let identifiers = [CountdownAlert.warning, CountdownAlert.expired].map { "countdown-\($0.key)" }

    private let center: AlertNotificationCenter
    private let emergencyT: () -> Translate
    private let emergencyNumber: () -> String

    init(center: AlertNotificationCenter = UNUserNotificationCenter.current(),
         emergencyT: @escaping () -> Translate, emergencyNumber: @escaping () -> String) {
        self.center = center
        self.emergencyT = emergencyT
        self.emergencyNumber = emergencyNumber
        super.init()
    }

    func schedule(alerts: [ScheduledAlert]) {
        cancelAll()
        // The app's strings are read on the main thread; the shared core may call from another.
        let (t, numberText) = Thread.isMainThread ? (emergencyT(), emergencyNumber())
            : DispatchQueue.main.sync { (emergencyT(), emergencyNumber()) }
        let number = ["emergencyNumber": numberText]
        for scheduled in alerts {
            let key = scheduled.alert.key
            let content = UNMutableNotificationContent()
            content.title = t("alerts.\(key).title", number)
            content.body = t("alerts.\(key).body", number)
            content.sound = .default
            // On the app's clock, which test builds can move, as a delay from now.
            let seconds = max(1, Double(scheduled.fireAtMs - AppClock.shared.nowMs()) / 1000)
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
            center.addRequest(UNNotificationRequest(identifier: "countdown-\(key)", content: content, trigger: trigger))
        }
    }

    func cancelAll() {
        center.removePendingNotificationRequests(withIdentifiers: Self.identifiers)
        center.removeDeliveredNotifications(withIdentifiers: Self.identifiers)
    }
}

/// The parts of UNUserNotificationCenter the alerts use, so the tests can stand in for it.
protocol AlertNotificationCenter {
    func addRequest(_ request: UNNotificationRequest)
    func removePendingNotificationRequests(withIdentifiers identifiers: [String])
    func removeDeliveredNotifications(withIdentifiers identifiers: [String])
}

extension UNUserNotificationCenter: AlertNotificationCenter {
    func addRequest(_ request: UNNotificationRequest) {
        add(request, withCompletionHandler: nil)
    }
}

/// Shows a countdown alert that arrives while the app is open (on another screen), and opens
/// the emergency when one is tapped.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()
    private var tappedBeforeHandler = false

    /// Called on a tap, on the main thread. A tap that launched the app arrives before the
    /// app sets this, and is delivered when it does.
    var onOpenEmergency: (() -> Void)? {
        didSet {
            if tappedBeforeHandler, let onOpenEmergency {
                tappedBeforeHandler = false
                onOpenEmergency()
            }
        }
    }

    func userNotificationCenter(_: UNUserNotificationCenter, willPresent _: UNNotification,
                                withCompletionHandler completion: @escaping (UNNotificationPresentationOptions) -> Void) {
        completion([.banner, .list, .sound])
    }

    func userNotificationCenter(_: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completion: @escaping () -> Void) {
        if CountdownNotifications.identifiers.contains(response.notification.request.identifier) {
            DispatchQueue.main.async {
                if let open = self.onOpenEmergency { open() } else { self.tappedBeforeHandler = true }
            }
        }
        completion()
    }
}
