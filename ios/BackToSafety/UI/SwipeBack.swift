import SwiftUI
import UIKit

/// RN's native stack keeps the edge swipe back though the app draws its own headers; SwiftUI
/// drops it when the navigation bar is hidden, so the navigation controller re-enables it.
/// A screen with unsaved changes blocks it (hooks/useUnsavedChangesGuard.ts).
enum SwipeBack {
    @MainActor static var blocked = false
}

extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1 && !MainActor.assumeIsolated { SwipeBack.blocked }
    }
}

extension View {
    /// Blocks the swipe back while [enabled] (unsaved changes).
    func backGuard(_ enabled: Bool) -> some View {
        onAppear { SwipeBack.blocked = enabled }
            .onChange(of: enabled) { _, on in SwipeBack.blocked = on }
            .onDisappear { SwipeBack.blocked = false }
    }
}
