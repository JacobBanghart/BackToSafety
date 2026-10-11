@testable import BackToSafety
import XCTest

/// The Rust core links into the app and its UniFFI bindings load: the pipeline's smoke test.
final class RustCoreTests: XCTestCase {
    func testCoreLoads() {
        XCTAssertFalse(coreVersion().isEmpty)
    }
}
