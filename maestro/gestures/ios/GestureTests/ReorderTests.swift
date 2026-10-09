import XCTest

/// Long-press the second list item and drag it above the first, aimed by testID, on the
/// screen a setup flow left open. SCREEN (contacts or destinations) arrives through
/// TEST_RUNNER_SCREEN. Maestro can't hold-then-drag; this is reorder.sh's adb draganddrop.
final class ReorderTests: XCTestCase {
    func testDragSecondItemToTop() throws {
        let screen = try XCTUnwrap(ProcessInfo.processInfo.environment["SCREEN"], "set TEST_RUNNER_SCREEN")
        let app = XCUIApplication(bundleIdentifier: "com.backtosafety.app")
        app.activate()
        let second = app.descendants(matching: .any)["\(screen)-item-1-name"]
        let first = app.descendants(matching: .any)["\(screen)-item-0-name"]
        XCTAssertTrue(second.waitForExistence(timeout: 10))
        XCTAssertTrue(first.exists)
        let from = second.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let to = first.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).withOffset(CGVector(dx: 0, dy: -30))
        from.press(forDuration: 1.0, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.5)
        sleep(1)
    }
}
