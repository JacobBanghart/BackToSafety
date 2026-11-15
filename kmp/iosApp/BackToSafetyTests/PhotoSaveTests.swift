@testable import BackToSafety
import Shared
import UIKit
import XCTest

/// Saving a picked photo: a save that fails (here a write that throws partway, as on a full disk)
/// reaches the caller to be shown, reports save_failed once with action `photo` and the error's
/// type, and leaves no photo behind; one that works returns the photo's file:// URI under
/// Documents (issue #9). The Android twin is PhotoSaveTest.
final class PhotoSaveTests: XCTestCase {
    private struct DiskFull: Error {}

    private var documents: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private var events: [(name: String, properties: [String: Any])] = []
    /// Puts back the sink that was there before the test.
    private var restoreSink: () -> Void = {}
    private var saved: [URL] = []

    override func setUp() {
        super.setUp()
        let previous = Analytics.shared.sink
        restoreSink = { Analytics.shared.sink = previous }
        Analytics.shared.sink = { name, properties in self.events.append((name, properties)) }
    }

    override func tearDown() {
        restoreSink()
        for file in saved {
            try? FileManager.default.removeItem(at: file)
        }
        super.tearDown()
    }

    private func photoFiles() -> Set<String> {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: documents.path)) ?? []
        return Set(names.filter { $0.hasPrefix("profile_photo_") })
    }

    private func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 40, height: 40)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 40, height: 40))
        }
    }

    private func saveFailedEvents() -> [[String: Any]] {
        events.filter { $0.name == "save_failed" }.map(\.properties)
    }

    func testAWriteThatThrowsIsReportedAndThrownAndLeavesNoPhoto() throws {
        let before = photoFiles()

        XCTAssertThrowsError(try savePickedPhoto(image(), screen: "profile") { data, url in
            // Half a photo written before the disk fills.
            try data.prefix(10).write(to: url)
            throw DiskFull()
        }) { error in
            XCTAssertTrue(error is DiskFull)
        }

        let reported = saveFailedEvents()
        XCTAssertEqual(reported.count, 1)
        XCTAssertEqual(reported.first?["screen"] as? String, "profile")
        XCTAssertEqual(reported.first?["action"] as? String, "photo")
        XCTAssertEqual(reported.first?["error"] as? String, String(describing: DiskFull.self))
        XCTAssertEqual(photoFiles(), before)
    }

    func testNoImageIsReportedAndThrown() {
        let before = photoFiles()

        XCTAssertThrowsError(try savePickedPhoto(nil, screen: "onboarding_photo")) { error in
            XCTAssertTrue(error is PhotoSaveError)
        }

        let reported = saveFailedEvents()
        XCTAssertEqual(reported.count, 1)
        XCTAssertEqual(reported.first?["screen"] as? String, "onboarding_photo")
        XCTAssertEqual(reported.first?["action"] as? String, "photo")
        XCTAssertEqual(reported.first?["error"] as? String, String(describing: PhotoSaveError.self))
        XCTAssertEqual(photoFiles(), before)
    }

    func testAPhotoSavedWithTheDefaultWriterIsAFileUnderDocuments() throws {
        let uri = try savePickedPhoto(image(), screen: "profile")

        let url = try XCTUnwrap(URL(string: uri))
        saved.append(url)
        XCTAssertTrue(url.isFileURL)
        XCTAssertEqual(url.deletingLastPathComponent().standardizedFileURL.path, documents.standardizedFileURL.path)
        XCTAssertTrue(url.lastPathComponent.hasPrefix("profile_photo_"))
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertTrue(saveFailedEvents().isEmpty)
    }
}
