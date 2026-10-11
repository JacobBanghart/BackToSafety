import UIKit

/// Opens the phone app at `number`. A device that can't place calls (an iPad without an
/// iPhone to relay through) says so, with the number to call from a phone, and reports
/// dial_failed (as the Android app does).
@MainActor
func dial(_ number: String, target: DialTarget, screen: String, tCommon: Translate) {
    guard let url = URL(string: "tel:\(number)") else { return }
    UIApplication.shared.open(url) { opened in
        guard !opened else { return }
        Task { @MainActor in
            Analytics.shared.track(event: .dialFailed, properties: dialFailedProperties(target: target, screen: screen))
            presentAlert(title: tCommon("callUnavailable.title"),
                         message: tCommon("callUnavailable.message", ["number": formatPhoneNumber(phone: number)]),
                         ok: tCommon("ok"))
        }
    }
}

/// save_failed: a write to the store failed and the user saw an error.
func reportSaveFailed(screen: String, action: String, error: Error) {
    Analytics.shared.track(event: .saveFailed, properties: saveFailedProperties(
        screen: screen, action: action, errorType: errorType(error)
    ))
}

/// An error's type name (never its message, which could hold what was typed): the core's
/// StoreError type names for a StoreFailure.
func errorType(_ error: Error) -> String {
    switch error as? StoreFailure {
    case .Sqlite: "SqliteError"
    case .Io: "IoError"
    case .UnknownProfileField: "UnknownProfileField"
    case .NewerDatabase: "NewerDatabase"
    case nil: String(describing: type(of: error))
    }
}

/// A system alert over whatever is showing, modals included.
@MainActor
func presentAlert(title: String, message: String, ok: String) {
    let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    guard var top = scene?.keyWindow?.rootViewController else { return }
    while let presented = top.presentedViewController {
        top = presented
    }
    let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: ok, style: .default))
    top.present(alert, animated: true)
}
