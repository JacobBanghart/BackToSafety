import Contacts
import ContactsUI
import UIKit

/// expo-contacts presentContactPickerAsync: the system contact picker, presented over the app.
/// Calls back with the picked contact, or nil when cancelled.
@MainActor
final class ContactPicker: NSObject, CNContactPickerDelegate {
    private static var current: ContactPicker?
    private let completion: (CNContact?) -> Void

    private init(_ completion: @escaping (CNContact?) -> Void) {
        self.completion = completion
    }

    static func present(_ completion: @escaping (CNContact?) -> Void) {
        let picker = ContactPicker(completion)
        current = picker
        let controller = CNContactPickerViewController()
        controller.delegate = picker
        topViewController()?.present(controller, animated: true)
    }

    nonisolated func contactPicker(_: CNContactPickerViewController, didSelect contact: CNContact) {
        MainActor.assumeIsolated { finish(contact) }
    }

    nonisolated func contactPickerDidCancel(_: CNContactPickerViewController) {
        MainActor.assumeIsolated { finish(nil) }
    }

    private func finish(_ contact: CNContact?) {
        completion(contact)
        ContactPicker.current = nil
    }
}

func topViewController() -> UIViewController? {
    let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
    while let presented = top?.presentedViewController { top = presented }
    return top
}
